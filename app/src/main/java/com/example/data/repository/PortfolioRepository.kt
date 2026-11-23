package com.example.data.repository

import android.content.Context
import androidx.room.withTransaction
import com.example.data.backup.BackupHolding
import com.example.data.backup.BackupPreferences
import com.example.data.backup.BackupPreview
import com.example.data.backup.BackupSnapshot
import com.example.data.backup.BackupTransaction
import com.example.data.backup.PortfolioBackupCodec
import com.example.data.backup.RestoreResult
import com.example.data.backup.ValorisBackupPayload
import com.example.data.local.CachedQuoteEntity
import com.example.data.local.FxRateDao
import com.example.data.local.FxRateEntity
import com.example.data.local.HoldingDao
import com.example.data.local.HoldingEntity
import com.example.data.local.NetWorthSnapshotEntity
import com.example.data.local.QuoteDao
import com.example.data.local.SnapshotDao
import com.example.data.local.TransactionDao
import com.example.data.local.TransactionEntity
import com.example.data.local.UserPreferences
import com.example.data.local.UserPreferencesRepository
import com.example.data.local.ValorisDatabase
import com.example.data.remote.FrankfurterFxService
import com.example.data.remote.MarketApiService
import com.example.data.remote.NetworkModule
import com.example.data.remote.OpenErFxService
import com.example.domain.model.AllocationSlice
import com.example.domain.model.AssetClass
import com.example.domain.model.AssetMovementSeries
import com.example.domain.model.AssetTransaction
import com.example.domain.model.ChartTimeframe
import com.example.domain.model.FxRateDisplay
import com.example.domain.model.HistoricalPoint
import com.example.domain.model.Holding
import com.example.domain.model.MarketStatus
import com.example.domain.model.MovementGraphMode
import com.example.domain.model.PnlTimelinePoint
import com.example.domain.model.PortfolioDashboardData
import com.example.domain.model.PortfolioHistorySummary
import com.example.domain.model.PortfolioMovementData
import com.example.domain.model.QuoteStatus
import com.example.domain.model.RefreshInterval
import com.example.domain.model.SearchSuggestion
import com.example.domain.model.SupportedCurrency
import com.example.domain.model.ThemeMode
import com.example.domain.model.TransactionDisplayItem
import com.example.domain.model.TransactionType
import com.example.domain.model.UnitType
import com.example.domain.model.ValuedHolding
import com.example.domain.model.WidgetDisplayMetric
import com.example.util.CuratedAssets
import com.example.util.CurrencyFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs

data class WidgetSourceData(
    val dashboard: PortfolioDashboardData,
    val movement: PortfolioMovementData?
)

data class RefreshResult(
    val fxUpdated: Boolean,
    val quotesUpdatedCount: Int,
    val failedSymbols: List<String>,
    val errorMessage: String?
)

data class ExportBackupResult(
    val json: String,
    val holdingsCount: Int,
    val transactionsCount: Int,
    val snapshotsCount: Int
)

data class SellResult(
    val symbol: String,
    val soldQuantity: Double,
    val unitType: UnitType,
    val soldAll: Boolean,
    val proceedsNative: Double,
    val nativeCurrency: String,
    val realizedGainNative: Double?,
    val depositedToCash: Boolean
)

class PortfolioRepository(
    private val holdingDao: HoldingDao,
    private val quoteDao: QuoteDao,
    private val fxRateDao: FxRateDao,
    private val snapshotDao: SnapshotDao,
    private val transactionDao: TransactionDao,
    private val prefsRepo: UserPreferencesRepository,
    private val marketApi: MarketApiService,
    private val openErApi: OpenErFxService,
    private val frankfurterApi: FrankfurterFxService,
    private val database: ValorisDatabase? = null
) {

    private data class HistoryCacheKey(
        val symbol: String,
        val timeframe: ChartTimeframe,
        val usePrePost: Boolean
    )

    private data class CachedHistoryEntry(
        val fetchedAtMs: Long,
        val points: List<HistoricalPoint>
    )

    private val historyCache = ConcurrentHashMap<HistoryCacheKey, CachedHistoryEntry>()
    private val sparklineCache = ConcurrentHashMap<String, List<Double>>()
    private val seedMutex = Mutex()
    @Volatile
    private var lastFxFetchMs: Long = 0L
    @Volatile
    private var isHistorySeededCached: Boolean = false

    private suspend fun <T> runInTransaction(block: suspend () -> T): T {
        return if (database != null) {
            database.withTransaction { block() }
        } else {
            block()
        }
    }

    val appLockConfigFlow = prefsRepo.appLockConfigFlow

    val transactionsFlow: Flow<List<AssetTransaction>> = transactionDao.observeAllTransactions()
        .map { entities -> entities.map { it.toDomain() } }
        .flowOn(Dispatchers.Default)

    val historySummaryFlow: Flow<PortfolioHistorySummary> = combine(
        transactionDao.observeAllTransactions(),
        fxRateDao.observeAllRates(),
        prefsRepo.preferencesFlow
    ) { entities, fxEntities, prefs ->
        val displayCode = prefs.displayCurrency.code
        val fxRates = buildMap {
            SupportedCurrency.entries.forEach { put(it.code, CurrencyFormatter.defaultFallbackRatePerUsd(it.code)) }
            fxEntities.forEach { if (it.ratePerUsd > 0.0) put(it.currencyCode.uppercase(Locale.US), it.ratePerUsd) }
            put("USD", 1.0)
        }
        var totalInvested = 0.0
        var totalProceeds = 0.0
        var totalRealized = 0.0
        var buyCount = 0
        var sellCount = 0

        val items = entities.map { entity ->
            val tx = entity.toDomain()
            val totalDisplay = CurrencyFormatter.convert(
                amount = tx.totalAmountNative,
                fromCurrency = tx.nativeCurrency,
                toCurrency = displayCode,
                ratesPerUsd = fxRates
            )
            val realizedDisplay = tx.realizedPnlNative?.let { pnl ->
                CurrencyFormatter.convert(
                    amount = pnl,
                    fromCurrency = tx.nativeCurrency,
                    toCurrency = displayCode,
                    ratesPerUsd = fxRates
                )
            }
            when (tx.type) {
                TransactionType.BUY -> {
                    buyCount++
                    if (tx.assetClass != AssetClass.CASH) {
                        totalInvested += totalDisplay
                    }
                }
                TransactionType.SELL -> {
                    sellCount++
                    if (tx.assetClass != AssetClass.CASH) {
                        totalProceeds += totalDisplay
                        totalRealized += (realizedDisplay ?: 0.0)
                    }
                }
            }
            TransactionDisplayItem(
                transaction = tx,
                totalAmountDisplay = totalDisplay,
                realizedPnlDisplay = realizedDisplay
            )
        }
        PortfolioHistorySummary(
            items = items,
            totalInvestedDisplay = totalInvested,
            totalProceedsDisplay = totalProceeds,
            totalRealizedPnlDisplay = totalRealized,
            buyCount = buyCount,
            sellCount = sellCount
        )
    }.flowOn(Dispatchers.Default)

    suspend fun ensureInitialTransactionsSeeded() = withContext(Dispatchers.IO) {
        if (isHistorySeededCached) return@withContext
        seedMutex.withLock {
            if (isHistorySeededCached) return@withContext
            if (prefsRepo.isInitialHistorySeeded()) {
                isHistorySeededCached = true
                return@withContext
            }
            val existingCount = transactionDao.getTransactionCount()
            if (existingCount == 0) {
                val holdings = holdingDao.getAllHoldingsOnce()
                if (holdings.isNotEmpty()) {
                    val seeded = holdings.map { h ->
                        val isCash = h.assetClass == AssetClass.CASH.name
                        val unitPrice = when {
                            isCash -> 1.0
                            h.averageCostPerUnit != null && h.averageCostPerUnit > 0.0 -> h.averageCostPerUnit
                            h.manualPriceOverride != null && h.manualPriceOverride > 0.0 -> h.manualPriceOverride
                            else -> 0.0
                        }
                        TransactionEntity(
                            holdingId = h.id,
                            symbol = h.symbol,
                            assetName = h.name,
                            assetClass = h.assetClass,
                            type = TransactionType.BUY.name,
                            quantity = h.quantity,
                            unitType = h.unitType,
                            unitPriceNative = unitPrice,
                            totalAmountNative = if (isCash) h.quantity else h.quantity * unitPrice,
                            nativeCurrency = h.nativeCurrency,
                            realizedPnlNative = null,
                            depositedToCash = false,
                            timestampMillis = h.buyDateMillis ?: h.updatedAt,
                            note = "Seeded from existing holding"
                        )
                    }
                    transactionDao.insertTransactions(seeded)
                }
            }
            prefsRepo.markInitialHistorySeeded()
            isHistorySeededCached = true
        }
    }

    @OptIn(FlowPreview::class)
    val dashboardFlow: Flow<PortfolioDashboardData> = combine(
        holdingDao.observeAllHoldings(),
        quoteDao.observeAllQuotes(),
        fxRateDao.observeAllRates(),
        snapshotDao.observeSnapshots(),
        prefsRepo.preferencesFlow
    ) { holdingEntities, quoteEntities, fxEntities, snapshots, prefs ->
        buildDashboardData(
            holdingEntities = holdingEntities,
            quoteEntities = quoteEntities,
            fxEntities = fxEntities,
            snapshots = snapshots,
            prefs = prefs
        )
    }.debounce(35L).flowOn(Dispatchers.Default)

    private fun buildDashboardData(
        holdingEntities: List<HoldingEntity>,
        quoteEntities: List<CachedQuoteEntity>,
        fxEntities: List<FxRateEntity>,
        snapshots: List<NetWorthSnapshotEntity>,
        prefs: UserPreferences
    ): PortfolioDashboardData {
        val now = System.currentTimeMillis()
        val displayCurrency = prefs.displayCurrency
        val quotesMap = quoteEntities.associateBy { it.symbol.uppercase(Locale.US) }

        val ratesPerUsd = buildMap {
            SupportedCurrency.entries.forEach { put(it.code, CurrencyFormatter.defaultFallbackRatePerUsd(it.code)) }
            fxEntities.forEach { entity ->
                if (entity.ratePerUsd > 0.0) {
                    put(entity.currencyCode.uppercase(Locale.US), entity.ratePerUsd)
                }
            }
            put("USD", 1.0)
        }

        val holdings = holdingEntities.map { it.toDomain() }
        val latestFxAt = fxEntities.maxOfOrNull { it.fetchedAt }
        val rawValued = holdings.map { holding ->
            val cachedQuote = quotesMap[holding.symbol.uppercase(Locale.US)]
            evaluateValuedHolding(
                holding = holding,
                cachedQuote = cachedQuote,
                latestFxAt = latestFxAt,
                displayCurrency = displayCurrency,
                showExtendedHoursPrice = prefs.showExtendedHoursPrice,
                ratesPerUsd = ratesPerUsd,
                nowMillis = now,
                sparkline = cachedQuote?.let { parseSparkline(it.sparklineCsv) }.orEmpty()
            )
        }

        val totalDisplay = rawValued.sumOf { it.totalValueDisplay }
        val valuedWithWeights = rawValued.map { item ->
            val weight = if (totalDisplay > 0.0) (item.totalValueDisplay / totalDisplay) * 100.0 else 0.0
            item.copy(portfolioWeightPercent = weight)
        }

        val totalDayChangeDisplay = valuedWithWeights.sumOf { it.dayChangeDisplay }
        val prevTotalDisplay = totalDisplay - totalDayChangeDisplay
        val totalDayChangePct = if (prevTotalDisplay > 0.0) {
            (totalDayChangeDisplay / prevTotalDisplay) * 100.0
        } else 0.0

        val costTrackedItems = valuedWithWeights.filter { it.unrealizedGainDisplay != null }
        val totalUnrealizedGain = if (costTrackedItems.isNotEmpty()) {
            costTrackedItems.sumOf { it.unrealizedGainDisplay ?: 0.0 }
        } else null
        val totalCostBasisOfTracked = costTrackedItems.sumOf {
            (it.totalValueDisplay) - (it.unrealizedGainDisplay ?: 0.0)
        }
        val totalUnrealizedPct = if (totalUnrealizedGain != null && totalCostBasisOfTracked > 0.0) {
            (totalUnrealizedGain / totalCostBasisOfTracked) * 100.0
        } else null

        val slices = AssetClass.entries.mapNotNull { assetClass ->
            val sum = valuedWithWeights
                .filter { it.holding.assetClass == assetClass }
                .sumOf { it.totalValueDisplay }
            if (sum > 0.0) {
                AllocationSlice(
                    assetClass = assetClass,
                    valueDisplay = sum,
                    percentage = if (totalDisplay > 0.0) (sum / totalDisplay) * 100.0 else 0.0
                )
            } else null
        }

        val fxEntityMap = fxEntities.associateBy { it.currencyCode.uppercase(Locale.US) }
        val fxList = SupportedCurrency.entries
            .filter { it != displayCurrency }
            .map { currency ->
                val rateInBase = CurrencyFormatter.convert(
                    amount = 1.0,
                    fromCurrency = currency.code,
                    toCurrency = displayCurrency.code,
                    ratesPerUsd = ratesPerUsd
                )
                val entity = fxEntityMap[currency.code]
                FxRateDisplay(
                    currencyCode = currency.code,
                    currencyName = currency.displayName,
                    rateInBaseCurrency = rateInBase,
                    fetchedAt = entity?.fetchedAt ?: 0L,
                    source = entity?.source ?: "Reference fallback"
                )
            }

        val usdToDisplay = CurrencyFormatter.convert(
            amount = 1.0,
            fromCurrency = "USD",
            toCurrency = displayCurrency.code,
            ratesPerUsd = ratesPerUsd
        )

        val trendDisplay = snapshots.takeLast(24).map { snap ->
            CurrencyFormatter.convert(
                amount = snap.totalValueUsd,
                fromCurrency = "USD",
                toCurrency = displayCurrency.code,
                ratesPerUsd = ratesPerUsd
            )
        }.let { points ->
            if (totalDisplay > 0.0 && (points.isEmpty() || kotlin.math.abs((points.lastOrNull() ?: 0.0) - totalDisplay) > 0.01)) {
                (points + totalDisplay).takeLast(24)
            } else {
                points
            }
        }

        val latestSync = listOfNotNull(
            fxEntities.maxOfOrNull { it.fetchedAt },
            quoteEntities.maxOfOrNull { it.fetchedAt }
        ).maxOrNull()

        val fxSource = fxEntities.firstOrNull()?.source ?: "Cached reference"
        val hasStale = valuedWithWeights.any {
            it.holding.assetClass != AssetClass.CASH &&
                it.quoteStatus == QuoteStatus.CACHED &&
                it.holding.manualPriceOverride == null
        }

        return PortfolioDashboardData(
            displayCurrency = displayCurrency,
            isBalanceHidden = prefs.isBalanceHidden,
            refreshInterval = prefs.refreshInterval,
            themeMode = prefs.themeMode,
            useCompactNumbers = prefs.useCompactNumbers,
            showPnlOnCards = prefs.showPnlOnCards,
            showExtendedHoursPrice = prefs.showExtendedHoursPrice,
            defaultChartTimeframe = prefs.defaultChartTimeframe,
            widgetDisplayMetric = prefs.widgetDisplayMetric,
            widgetChartTimeframe = prefs.widgetChartTimeframe,
            appLockEnabled = prefs.appLockEnabled,
            biometricUnlockEnabled = prefs.biometricUnlockEnabled,
            totalValueDisplay = totalDisplay,
            dayChangeDisplay = totalDayChangeDisplay,
            dayChangePercent = totalDayChangePct,
            totalUnrealizedGainDisplay = totalUnrealizedGain,
            totalUnrealizedGainPercent = totalUnrealizedPct,
            valuedHoldings = valuedWithWeights,
            allocationSlices = slices,
            fxRatesList = fxList,
            usdToDisplayRate = usdToDisplay,
            trendValuesDisplay = trendDisplay,
            lastSyncEpoch = latestSync,
            fxSourceLabel = fxSource,
            hasStaleQuotes = hasStale
        )
    }

    suspend fun refreshAllMarketData(): RefreshResult = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val holdings = holdingDao.getAllHoldingsOnce()
        val symbolsToFetch = holdings
            .filter { it.assetClass != AssetClass.CASH.name && it.manualPriceOverride == null }
            .map { it.symbol.trim().uppercase(Locale.US) }
            .distinct()

        val failed = mutableListOf<String>()
        val (fxUpdated, fetchedQuotes) = coroutineScope {
            val fxDeferred = async { fetchAndPersistFxRates(now) }
            val quotesDeferred = symbolsToFetch.map { symbol ->
                async {
                    fetchSingleQuote(symbol, now) ?: run {
                        synchronized(failed) { failed.add(symbol) }
                        null
                    }
                }
            }
            val fxOk = fxDeferred.await()
            val quotes = quotesDeferred.awaitAll().filterNotNull()
            fxOk to quotes
        }

        if (fxUpdated) {
            lastFxFetchMs = now
        }
        if (fetchedQuotes.isNotEmpty()) {
            quoteDao.upsertQuotes(fetchedQuotes)
            // Auto-heal holdings that were saved with a truncated short name or only symbol
            val quotesBySymbol = fetchedQuotes.associateBy { it.symbol.uppercase(Locale.US) }
            val existingHoldings = holdingDao.getAllHoldingsOnce()
            existingHoldings.forEach { h ->
                val quote = quotesBySymbol[h.symbol.uppercase(Locale.US)]
                if (quote != null && quote.displayName.isNotBlank() && quote.displayName != h.symbol) {
                    val currentName = h.name.trim()
                    val newName = quote.displayName.trim()
                    val isTruncatedPrefix = newName.length > currentName.length &&
                        newName.startsWith(currentName, ignoreCase = true)
                    val isJustSymbol = currentName.equals(h.symbol, ignoreCase = true)
                    if (isTruncatedPrefix || isJustSymbol) {
                        holdingDao.upsertHolding(h.copy(name = newName, updatedAt = now))
                        val txs = transactionDao.getTransactionsForHolding(h.id)
                        txs.forEach { tx ->
                            val txName = tx.assetName.trim()
                            if (txName.equals(h.symbol, ignoreCase = true) ||
                                (newName.length > txName.length && newName.startsWith(txName, ignoreCase = true))) {
                                transactionDao.insertTransaction(tx.copy(assetName = newName))
                            }
                        }
                    }
                }
            }
        }

        recordSnapshotIfNeeded(now)

        val errMsg = when {
            !fxUpdated && fetchedQuotes.isEmpty() && symbolsToFetch.isNotEmpty() ->
                "Unable to reach live feeds. Showing locally cached prices."
            failed.isNotEmpty() ->
                "Could not refresh: ${failed.joinToString(", ")}"
            else -> null
        }

        RefreshResult(
            fxUpdated = fxUpdated,
            quotesUpdatedCount = fetchedQuotes.size,
            failedSymbols = failed,
            errorMessage = errMsg
        )
    }

    private suspend fun fetchAndPersistFxRates(now: Long): Boolean {
        // Primary: Open ER-API
        val primaryRates = runCatching {
            val response = openErApi.getLatestUsdRates()
            if (response.result.equals("success", true) && !response.rates.isNullOrEmpty()) {
                response.rates
            } else null
        }.getOrNull()

        if (primaryRates != null) {
            val entities = primaryRates.entries
                .filter { it.value > 0.0 }
                .map { (code, rate) ->
                    FxRateEntity(
                        currencyCode = code.uppercase(Locale.US),
                        ratePerUsd = rate,
                        source = "Open ExchangeRate-API",
                        fetchedAt = now
                    )
                }
            if (entities.isNotEmpty()) {
                fxRateDao.upsertRates(entities)
                return true
            }
        }

        // Fallback: Frankfurter (ECB)
        val fallbackRates = runCatching {
            val response = frankfurterApi.getLatestRates("USD")
            response.rates
        }.getOrNull()

        if (!fallbackRates.isNullOrEmpty()) {
            val entities = buildList {
                add(FxRateEntity("USD", 1.0, "Frankfurter (ECB)", now))
                fallbackRates.forEach { (code, rate) ->
                    if (rate > 0.0) {
                        add(FxRateEntity(code.uppercase(Locale.US), rate, "Frankfurter (ECB)", now))
                    }
                }
            }
            fxRateDao.upsertRates(entities)
            return true
        }

        return false
    }

    private suspend fun fetchSingleQuote(symbol: String, now: Long): CachedQuoteEntity? {
        return runCatching {
            val response = marketApi.getChartQuote(
                symbol = symbol,
                range = "1d",
                interval = "5m",
                includePrePost = true
            )
            val result = response.chart?.result?.firstOrNull() ?: return@runCatching null
            val meta = result.meta ?: return@runCatching null
            val rawPrice = meta.regularMarketPrice ?: return@runCatching null
            val rawPrevClose = resolvePreviousClose(
                currentPrice = rawPrice,
                previousClose = meta.previousClose,
                oneDayChartPreviousClose = meta.chartPreviousClose
            )

            val rawCurrency = meta.currency ?: "USD"
            // Normalize London pence (GBp) or Israeli agorot (ILA) to major currency units
            val (normalizedCurrency, scaleFactor) = when (rawCurrency) {
                "GBp" -> "GBP" to 0.01
                "ILA" -> "ILS" to 0.01
                else -> rawCurrency.uppercase(Locale.US) to 1.0
            }

            val price = rawPrice * scaleFactor
            val prevClose = rawPrevClose * scaleFactor
            val changePct = if (prevClose > 0.0) ((price - prevClose) / prevClose) * 100.0 else 0.0

            val rawTimestamps = result.timestamp.orEmpty()
            val rawCloses = result.indicators?.quote?.firstOrNull()?.close.orEmpty()
            val closes = rawCloses.filterNotNull().map { it * scaleFactor }

            val rawMarketState = meta.marketState?.trim()?.uppercase(Locale.US).orEmpty()
            val regStart = meta.currentTradingPeriod?.regular?.start
            val regEnd = meta.currentTradingPeriod?.regular?.end
            val encodedMarketState = if (regStart != null && regEnd != null && regEnd > regStart) {
                "$rawMarketState|$regStart|$regEnd"
            } else {
                rawMarketState.ifBlank { "UNKNOWN" }
            }

            val rawExtendedPrice = extractExtendedHoursPrice(
                marketState = rawMarketState,
                regularMarketPrice = rawPrice,
                regularMarketTimeSec = meta.regularMarketTime,
                regularStartSec = regStart,
                regularEndSec = regEnd,
                preMarketPrice = meta.preMarketPrice,
                postMarketPrice = meta.postMarketPrice,
                timestamps = rawTimestamps,
                closes = rawCloses
            )
            val extendedPrice = rawExtendedPrice?.let { it * scaleFactor }

            val sparklinePoints = if (closes.size > 24) {
                val step = (closes.size - 1) / 23.0
                (0 until 24).map { i -> closes[(i * step).toInt().coerceAtMost(closes.lastIndex)] }
            } else {
                closes
            }

            CachedQuoteEntity(
                symbol = symbol.uppercase(Locale.US),
                priceNative = price,
                previousCloseNative = prevClose,
                extendedPriceNative = extendedPrice,
                currency = normalizedCurrency,
                dayChangePercent = changePct,
                marketState = encodedMarketState,
                displayName = pickBestDisplayName(
                    longName = meta.longName,
                    shortName = meta.shortName,
                    fallback = symbol
                ),
                sparklineCsv = sparklinePoints.joinToString(",") { it.toString() },
                fetchedAt = now
            )
        }.getOrNull()
    }

    suspend fun fetchSymbolHistory(
        symbol: String,
        timeframe: ChartTimeframe,
        includeExtendedHours: Boolean = false,
        forceRefresh: Boolean = false
    ): List<Double> = fetchSymbolHistoryWithTimestamps(
        symbol = symbol,
        timeframe = timeframe,
        includeExtendedHours = includeExtendedHours,
        forceRefresh = forceRefresh
    ).map { it.value }

    suspend fun fetchSymbolHistoryWithTimestamps(
        symbol: String,
        timeframe: ChartTimeframe,
        includeExtendedHours: Boolean = false,
        forceRefresh: Boolean = false
    ): List<HistoricalPoint> = withContext(Dispatchers.IO) {
        val normalizedSym = symbol.trim().uppercase(Locale.US)
        val usePrePost = includeExtendedHours &&
            (timeframe == ChartTimeframe.ONE_DAY || timeframe == ChartTimeframe.FIVE_DAYS)
        val cacheKey = HistoryCacheKey(normalizedSym, timeframe, usePrePost)
        val now = System.currentTimeMillis()
        if (!forceRefresh) {
            historyCache[cacheKey]?.let { entry ->
                if ((now - entry.fetchedAtMs) < HISTORY_CACHE_TTL_MS && entry.points.isNotEmpty()) {
                    return@withContext entry.points
                }
            }
        }

        val fetched = runCatching {
            suspend fun fetchWithInterval(interval: String): List<HistoricalPoint> {
                val response = marketApi.getChartQuote(
                    symbol = normalizedSym,
                    range = timeframe.range,
                    interval = interval,
                    includePrePost = usePrePost
                )
                val result = response.chart?.result?.firstOrNull() ?: return emptyList()
                val scale = when (result.meta?.currency) {
                    "GBp", "ILA" -> 0.01
                    else -> 1.0
                }
                val timestamps = result.timestamp.orEmpty()
                val closes = result.indicators?.quote?.firstOrNull()?.close.orEmpty()
                return timestamps.zip(closes).mapNotNull { (ts, close) ->
                    close?.let { HistoricalPoint(timestampSec = ts, value = it * scale) }
                }
            }

            val primary = fetchWithInterval(timeframe.interval)
            if (primary.size < 2 && timeframe == ChartTimeframe.ALL) {
                fetchWithInterval("1wk").ifEmpty { primary }
            } else {
                primary
            }
        }.getOrDefault(emptyList())

        if (fetched.isNotEmpty()) {
            if (historyCache.size > 60) {
                historyCache.clear()
            }
            historyCache[cacheKey] = CachedHistoryEntry(now, fetched)
        }
        fetched
    }

    suspend fun fetchPortfolioMovement(
        timeframe: ChartTimeframe,
        graphMode: MovementGraphMode,
        respectBuyDates: Boolean,
        displayCurrency: SupportedCurrency,
        includeExtendedHours: Boolean = false,
        forceRefresh: Boolean = false
    ): PortfolioMovementData = withContext(Dispatchers.IO) {
        ensureInitialTransactionsSeeded()
        val holdings = holdingDao.getAllHoldingsOnce().map { it.toDomain() }
        val transactions = transactionDao.getAllTransactionsAscending().map { it.toDomain() }
        if (holdings.isEmpty() && transactions.isEmpty()) {
            return@withContext PortfolioMovementData(
                timeframe = timeframe,
                graphMode = graphMode,
                respectBuyDates = respectBuyDates,
                hasAnyBuyDates = false,
                isLoading = false
            )
        }

        val hasAnyBuyDates = holdings.any { it.buyDateMillis != null } || transactions.isNotEmpty()
        val quotesMap = quoteDao.getAllQuotesOnce().associateBy { it.symbol.uppercase(Locale.US) }
        val fxRates = buildMap {
            SupportedCurrency.entries.forEach { put(it.code, CurrencyFormatter.defaultFallbackRatePerUsd(it.code)) }
            fxRateDao.getAllRatesOnce().forEach { put(it.currencyCode.uppercase(Locale.US), it.ratePerUsd) }
            put("USD", 1.0)
        }

        val holdingMarketSymbols = holdings
            .filter { it.assetClass != AssetClass.CASH && it.manualPriceOverride == null }
            .map { it.symbol.trim().uppercase(Locale.US) }
        val txMarketSymbols = transactions
            .filter { it.assetClass != AssetClass.CASH }
            .map { it.symbol.trim().uppercase(Locale.US) }
        val manualSymbols = holdings
            .filter { it.manualPriceOverride != null }
            .map { it.symbol.trim().uppercase(Locale.US) }
            .toSet()
        val marketSymbols = (holdingMarketSymbols + txMarketSymbols)
            .filterNot { it in manualSymbols }
            .distinct()

        val symbolHistories: Map<String, List<HistoricalPoint>> = coroutineScope {
            marketSymbols.map { sym ->
                async {
                    sym to fetchSymbolHistoryWithTimestamps(sym, timeframe, includeExtendedHours, forceRefresh)
                }
            }.awaitAll().toMap()
        }

        val bucketSeconds = when (timeframe) {
            ChartTimeframe.ONE_DAY -> 900L
            ChartTimeframe.FIVE_DAYS -> 3600L
            ChartTimeframe.MONTH, ChartTimeframe.QUARTER, ChartTimeframe.YTD -> 86400L
            ChartTimeframe.YEAR, ChartTimeframe.FIVE_YEARS -> 7L * 86400L
            ChartTimeframe.ALL -> 30L * 86400L
        }
        val nowMillis = System.currentTimeMillis()
        val nowSec = nowMillis / 1000L

        val normalizedBySymbol: Map<String, List<HistoricalPoint>> = symbolHistories.mapValues { (sym, pts) ->
            val grouped = pts.groupBy { pt ->
                if (timeframe == ChartTimeframe.ALL) {
                    monthStartEpochSec(pt.timestampSec)
                } else {
                    (pt.timestampSec / bucketSeconds) * bucketSeconds
                }
            }
                .toSortedMap()
                .map { (bucketTs, group) -> HistoricalPoint(bucketTs, group.last().value) }
                .toMutableList()
            val liveQuote = resolveEffectiveQuotePrice(quotesMap[sym], includeExtendedHours, nowSec)
            val lastCandleTs = grouped.lastOrNull()?.timestampSec
            if (liveQuote != null && liveQuote > 0.0 && lastCandleTs != null && (lastCandleTs + bucketSeconds) < nowSec) {
                grouped.add(HistoricalPoint(nowSec, liveQuote))
            }
            grouped
        }

        val rawBuckets = normalizedBySymbol.values
            .flatten()
            .map { it.timestampSec }
            .distinct()
            .sorted()
            .toMutableList()

        if (rawBuckets.isEmpty()) {
            val nowBucket = (nowSec / bucketSeconds) * bucketSeconds
            val (count, step) = when (timeframe) {
                ChartTimeframe.ONE_DAY -> 24 to 3600L
                ChartTimeframe.FIVE_DAYS -> 30 to (4 * 3600L)
                ChartTimeframe.MONTH -> 30 to 86400L
                ChartTimeframe.QUARTER -> 90 to 86400L
                ChartTimeframe.YTD -> {
                    val dayOfYear = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
                        .get(Calendar.DAY_OF_YEAR)
                        .coerceAtLeast(2)
                    dayOfYear to 86400L
                }
                ChartTimeframe.YEAR -> 52 to (7 * 86400L)
                ChartTimeframe.FIVE_YEARS -> 260 to (7 * 86400L)
                ChartTimeframe.ALL -> 60 to (30 * 86400L)
            }
            for (i in count downTo 0) {
                rawBuckets.add(nowBucket - i * step)
            }
        }
        val latestTxMillis = transactions.maxOfOrNull { it.timestampMillis } ?: 0L
        val lastBucketEndMillis = (rawBuckets.last() + bucketSeconds) * 1000L - 1L
        if (latestTxMillis > lastBucketEndMillis || (rawBuckets.last() + bucketSeconds) < nowSec) {
            if (nowSec > rawBuckets.last()) {
                rawBuckets.add(nowSec)
            }
        }
        val allBuckets = rawBuckets.distinct().sorted()

        val assetSeries = holdings
            .filter { it.assetClass != AssetClass.CASH }
            .distinctBy { it.symbol.uppercase(Locale.US) }
            .mapNotNull { holding ->
                val symKey = holding.symbol.uppercase(Locale.US)
                val cachedQuote = quotesMap[symKey]
                val effectiveCurrency = when {
                    holding.manualPriceOverride != null -> holding.nativeCurrency
                    cachedQuote != null -> cachedQuote.currency.ifBlank { holding.nativeCurrency }
                    else -> holding.nativeCurrency
                }

                val rawPoints = if (holding.manualPriceOverride != null) {
                    listOf(
                        HistoricalPoint(allBuckets.first(), holding.manualPriceOverride),
                        HistoricalPoint(allBuckets.last(), holding.manualPriceOverride)
                    )
                } else {
                    val marketPts = normalizedBySymbol[symKey].orEmpty()
                    if (holding.assetClass == AssetClass.COMMODITY && holding.unitType == UnitType.GRAMS) {
                        marketPts.map { pt ->
                            pt.copy(
                                value = CurrencyFormatter.unitQuotePerEnteredUnit(
                                    assetClass = holding.assetClass,
                                    unitType = holding.unitType,
                                    marketQuoteNative = pt.value
                                )
                            )
                        }
                    } else {
                        marketPts
                    }
                }

                if (rawPoints.size < 2) return@mapNotNull null

                val alignedRaw = alignSeriesToBuckets(rawPoints, allBuckets)
                val basePrice = alignedRaw.firstOrNull { it.value > 0.0 }?.value ?: return@mapNotNull null
                val pctPoints = alignedRaw.map { pt ->
                    HistoricalPoint(
                        timestampSec = pt.timestampSec,
                        value = ((pt.value - basePrice) / basePrice) * 100.0
                    )
                }
                AssetMovementSeries(
                    symbol = symKey,
                    name = holding.name,
                    assetClass = holding.assetClass,
                    nativeCurrency = effectiveCurrency,
                    normalizedPercentPoints = pctPoints,
                    rawPricePoints = alignedRaw,
                    periodChangePercent = pctPoints.lastOrNull()?.value ?: 0.0,
                    latestPriceNative = alignedRaw.lastOrNull()?.value ?: basePrice
                )
            }

        val remotePriceMap = buildMap {
            quotesMap.forEach { (sym, q) ->
                resolveEffectiveQuotePrice(q, includeExtendedHours, nowSec)?.let { price ->
                    if (price > 0.0) put(sym, price)
                }
            }
        }
        val manualPriceMap = buildMap {
            holdings.forEach { h ->
                val manual = h.manualPriceOverride
                if (manual != null && manual > 0.0) {
                    put(h.symbol.uppercase(Locale.US), manual)
                }
            }
        }
        val effectiveCurrencyMap = buildMap {
            transactions.forEach { tx -> put(tx.symbol.uppercase(Locale.US), tx.nativeCurrency) }
            holdings.forEach { h ->
                val sym = h.symbol.uppercase(Locale.US)
                val q = quotesMap[sym]
                val cur = when {
                    h.assetClass == AssetClass.CASH -> h.nativeCurrency
                    h.manualPriceOverride != null -> h.nativeCurrency
                    q != null && q.currency.isNotBlank() -> q.currency
                    else -> h.nativeCurrency
                }
                put(sym, cur)
            }
        }

        val replay = computeDualMovementTimelines(
            buckets = allBuckets,
            bucketSeconds = bucketSeconds,
            nowMillis = nowMillis,
            transactions = transactions,
            currentHoldings = holdings,
            historicalPricesBySymbol = normalizedBySymbol,
            cachedQuotePriceBySymbol = remotePriceMap,
            effectiveCurrencyBySymbol = effectiveCurrencyMap,
            fxRates = fxRates,
            displayCurrency = displayCurrency,
            respectBuyDates = respectBuyDates,
            manualOverrideBySymbol = manualPriceMap
        )

        PortfolioMovementData(
            timeframe = timeframe,
            graphMode = graphMode,
            respectBuyDates = respectBuyDates,
            hasAnyBuyDates = hasAnyBuyDates,
            totalTimeline = replay.totalTimeline,
            pnlTimeline = replay.pnlTimeline,
            assetSeries = assetSeries,
            periodChangeDisplay = replay.periodChangeDisplay,
            periodChangePercent = replay.periodChangePercent,
            latestTotalReturnDisplay = replay.latestTotalReturnDisplay,
            latestUnrealizedPnlDisplay = replay.latestUnrealizedPnlDisplay,
            latestRealizedPnlDisplay = replay.latestRealizedPnlDisplay,
            latestReturnPercent = replay.latestReturnPercent,
            isLoading = false,
            errorMessage = if (marketSymbols.isNotEmpty() && assetSeries.isEmpty() && transactions.all { it.assetClass == AssetClass.CASH }) {
                null
            } else if (marketSymbols.isNotEmpty() && assetSeries.isEmpty() && holdings.any { it.assetClass != AssetClass.CASH && it.manualPriceOverride == null }) {
                "Could not load market history right now. Check connection and try again."
            } else null
        )
    }

    private fun alignSeriesToBuckets(
        points: List<HistoricalPoint>,
        buckets: List<Long>
    ): List<HistoricalPoint> {
        if (points.isEmpty()) return emptyList()
        return buckets.mapNotNull { b ->
            lookupNearestPrice(points, b)?.let { HistoricalPoint(b, it) }
        }
    }

    private fun lookupNearestPrice(points: List<HistoricalPoint>, bucketSec: Long): Double? {
        return findLatestPriceAtOrBefore(points, bucketSec)
    }

    private fun monthStartEpochSec(timestampSec: Long): Long {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        cal.timeInMillis = timestampSec * 1000L
        cal.set(Calendar.DAY_OF_MONTH, 1)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis / 1000L
    }

    suspend fun searchSymbols(query: String): List<SearchSuggestion> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            return@withContext CuratedAssets.presets
                .filter { it.assetClass != AssetClass.CASH }
                .map {
                    SearchSuggestion(
                        symbol = it.symbol,
                        name = it.name,
                        assetClass = it.assetClass,
                        exchange = it.subtitle,
                        currency = it.nativeCurrency
                    )
                }
        }

        val remote = runCatching {
            val response = marketApi.searchSymbols(trimmed)
            response.quotes.orEmpty().mapNotNull { q ->
                val sym = q.symbol ?: return@mapNotNull null
                val assetClass = when (q.quoteType?.uppercase(Locale.US)) {
                    "ETF" -> AssetClass.ETF
                    "FUTURE" -> AssetClass.COMMODITY
                    "EQUITY" -> AssetClass.STOCK
                    else -> return@mapNotNull null
                }
                val inferredCurrency = if (sym.endsWith(".JK", true)) "IDR" else "USD"
                SearchSuggestion(
                    symbol = sym,
                    name = pickBestDisplayName(
                        longName = q.longname,
                        shortName = q.shortname,
                        fallback = sym
                    ),
                    assetClass = assetClass,
                    exchange = q.exchDisp ?: q.exchange ?: "",
                    currency = inferredCurrency
                )
            }
        }.getOrDefault(emptyList())

        if (remote.isNotEmpty()) return@withContext remote

        // Fallback matching against curated presets if offline
        CuratedAssets.presets
            .filter {
                it.assetClass != AssetClass.CASH &&
                    (it.symbol.contains(trimmed, true) || it.name.contains(trimmed, true))
            }
            .map {
                SearchSuggestion(
                    symbol = it.symbol,
                    name = it.name,
                    assetClass = it.assetClass,
                    exchange = it.subtitle,
                    currency = it.nativeCurrency
                )
            }
    }

    suspend fun saveHolding(holding: Holding): Unit = withContext(Dispatchers.IO) {
        ensureInitialTransactionsSeeded()
        val now = System.currentTimeMillis()
        val normalized = holding.copy(
            symbol = holding.symbol.trim().uppercase(Locale.US),
            name = holding.name.trim().ifBlank { holding.symbol.trim().uppercase(Locale.US) },
            nativeCurrency = holding.nativeCurrency.trim().uppercase(Locale.US),
            updatedAt = now
        )
        val isNewHolding = normalized.id == 0L
        val (effectiveHoldingId, createdTxId) = runInTransaction {
            val previous = if (!isNewHolding) holdingDao.getHoldingById(normalized.id) else null
            val savedId = holdingDao.upsertHolding(HoldingEntity.fromDomain(normalized))
            val resolvedId = if (isNewHolding) savedId else normalized.id

            val isCash = normalized.assetClass == AssetClass.CASH
            var newTxId: Long? = null
            if (isNewHolding) {
                val unitPrice = if (isCash) 1.0 else (normalized.averageCostPerUnit ?: 0.0)
                val totalAmt = if (isCash) normalized.quantity else normalized.quantity * unitPrice
                newTxId = transactionDao.insertTransaction(
                    TransactionEntity(
                        holdingId = resolvedId,
                        symbol = normalized.symbol,
                        assetName = normalized.name,
                        assetClass = normalized.assetClass.name,
                        type = TransactionType.BUY.name,
                        quantity = normalized.quantity,
                        unitType = normalized.unitType.name,
                        unitPriceNative = unitPrice,
                        totalAmountNative = totalAmt,
                        nativeCurrency = normalized.nativeCurrency,
                        realizedPnlNative = null,
                        depositedToCash = false,
                        timestampMillis = normalized.buyDateMillis ?: now,
                        note = if (isCash) "Initial cash deposit" else "Initial position"
                    )
                )
            } else if (previous != null) {
                val existingTxs = transactionDao.getTransactionsForHolding(resolvedId)
                val unitPrice = if (isCash) 1.0 else (normalized.averageCostPerUnit ?: 0.0)
                if (!isCash && existingTxs.size == 1 && existingTxs.first().type == TransactionType.BUY.name) {
                    val single = existingTxs.first()
                    transactionDao.insertTransaction(
                        single.copy(
                            symbol = normalized.symbol,
                            assetName = normalized.name,
                            assetClass = normalized.assetClass.name,
                            quantity = normalized.quantity,
                            unitType = normalized.unitType.name,
                            unitPriceNative = unitPrice,
                            totalAmountNative = normalized.quantity * unitPrice,
                            nativeCurrency = normalized.nativeCurrency,
                            timestampMillis = normalized.buyDateMillis ?: single.timestampMillis
                        )
                    )
                } else {
                    val qtyDelta = normalized.quantity - previous.quantity
                    if (abs(qtyDelta) > 1e-8) {
                        val txType = if (qtyDelta > 0.0) TransactionType.BUY else TransactionType.SELL
                        val absDelta = abs(qtyDelta)
                        transactionDao.insertTransaction(
                            TransactionEntity(
                                holdingId = resolvedId,
                                symbol = normalized.symbol,
                                assetName = normalized.name,
                                assetClass = normalized.assetClass.name,
                                type = txType.name,
                                quantity = absDelta,
                                unitType = normalized.unitType.name,
                                unitPriceNative = unitPrice,
                                totalAmountNative = if (isCash) absDelta else absDelta * unitPrice,
                                nativeCurrency = normalized.nativeCurrency,
                                realizedPnlNative = null,
                                depositedToCash = false,
                                timestampMillis = now,
                                note = "Position adjustment"
                            )
                        )
                    }
                }
            }
            resolvedId to newTxId
        }

        val needsFxRefresh = (now - lastFxFetchMs) > 5 * 60 * 1000L
        var fetchedQuoteToApply: CachedQuoteEntity? = null
        if (normalized.assetClass != AssetClass.CASH && normalized.manualPriceOverride == null) {
            coroutineScope {
                val fxJob = if (needsFxRefresh) async {
                    if (fetchAndPersistFxRates(now)) lastFxFetchMs = now
                } else null
                val quoteJob = async { fetchSingleQuote(normalized.symbol, now) }
                fxJob?.await()
                fetchedQuoteToApply = quoteJob.await()
            }
        } else if (needsFxRefresh) {
            if (fetchAndPersistFxRates(now)) {
                lastFxFetchMs = now
            }
        }

        runInTransaction {
            fetchedQuoteToApply?.let { quote ->
                quoteDao.upsertQuote(quote)
                // Align holding native currency to actual market quote currency if not manually set,
                // and enrich truncated/symbol names to full display names
                val updatedHoldings = holdingDao.getAllHoldingsOnce()
                updatedHoldings
                    .filter { it.symbol.equals(normalized.symbol, true) }
                    .forEach { entity ->
                        val currentName = entity.name.trim()
                        val newName = quote.displayName.trim()
                        val isTruncatedPrefix = newName.length > currentName.length &&
                            newName.startsWith(currentName, ignoreCase = true)
                        val isJustSymbol = currentName.equals(entity.symbol, ignoreCase = true)
                        val targetName = if (isTruncatedPrefix || isJustSymbol) newName else entity.name
                        val targetCurrency = if (entity.nativeCurrency != quote.currency) quote.currency else entity.nativeCurrency

                        if (targetName != entity.name || targetCurrency != entity.nativeCurrency) {
                            holdingDao.upsertHolding(entity.copy(name = targetName, nativeCurrency = targetCurrency))
                        }
                    }
                if (createdTxId != null) {
                    val created = transactionDao.getTransactionsForHolding(effectiveHoldingId)
                        .firstOrNull { it.id == createdTxId }
                    if (created != null) {
                        val currentName = created.assetName.trim()
                        val newName = quote.displayName.trim()
                        val isTruncatedPrefix = newName.length > currentName.length &&
                            newName.startsWith(currentName, ignoreCase = true)
                        val isJustSymbol = currentName.equals(created.symbol, ignoreCase = true)
                        val targetName = if (isTruncatedPrefix || isJustSymbol) newName else created.assetName
                        val targetCurrency = if (created.nativeCurrency != quote.currency) quote.currency else created.nativeCurrency

                        if (targetName != created.assetName || targetCurrency != created.nativeCurrency) {
                            transactionDao.insertTransaction(created.copy(assetName = targetName, nativeCurrency = targetCurrency))
                        }
                    }
                }
            }
            recordSnapshotIfNeeded(System.currentTimeMillis(), force = true)
        }
    }

    suspend fun addBuyToHolding(
        holdingId: Long,
        addedQuantity: Double,
        buyPricePerUnit: Double?,
        buyDateMillis: Long?
    ): Unit = withContext(Dispatchers.IO) {
        ensureInitialTransactionsSeeded()
        runInTransaction {
            val existing = holdingDao.getHoldingById(holdingId) ?: return@runInTransaction
            val newQuantity = existing.quantity + addedQuantity
            if (newQuantity <= 0.0 || addedQuantity <= 0.0) return@runInTransaction

            val isCash = existing.assetClass == AssetClass.CASH.name
            val newAvgCost = if (isCash) {
                null
            } else {
                CurrencyFormatter.calculateWeightedAverageCost(
                    existingQuantity = existing.quantity,
                    existingAvgCost = existing.averageCostPerUnit,
                    addedQuantity = addedQuantity,
                    newBuyPrice = buyPricePerUnit
                )
            }

            val tradeTimestamp = buyDateMillis ?: System.currentTimeMillis()
            val resolvedBuyDate = listOfNotNull(existing.buyDateMillis, buyDateMillis).minOrNull()
            val updated = existing.copy(
                quantity = newQuantity,
                averageCostPerUnit = newAvgCost,
                buyDateMillis = resolvedBuyDate,
                updatedAt = System.currentTimeMillis()
            )
            holdingDao.upsertHolding(updated)

            val unitPrice = if (isCash) 1.0 else (buyPricePerUnit ?: existing.averageCostPerUnit ?: 0.0)
            val totalAmt = if (isCash) addedQuantity else addedQuantity * unitPrice
            transactionDao.insertTransaction(
                TransactionEntity(
                    holdingId = existing.id,
                    symbol = existing.symbol,
                    assetName = existing.name,
                    assetClass = existing.assetClass,
                    type = TransactionType.BUY.name,
                    quantity = addedQuantity,
                    unitType = existing.unitType,
                    unitPriceNative = unitPrice,
                    totalAmountNative = totalAmt,
                    nativeCurrency = existing.nativeCurrency,
                    realizedPnlNative = null,
                    depositedToCash = false,
                    timestampMillis = tradeTimestamp,
                    note = if (isCash) "Cash deposit" else "Buy lot"
                )
            )
            recordSnapshotIfNeeded(System.currentTimeMillis(), force = true)
        }
    }

    suspend fun sellFromHolding(
        holdingId: Long,
        sellQuantity: Double,
        sellPricePerUnit: Double,
        depositProceedsToCash: Boolean,
        sellDateMillis: Long = System.currentTimeMillis()
    ): SellResult? = withContext(Dispatchers.IO) {
        ensureInitialTransactionsSeeded()
        runInTransaction {
            val existing = holdingDao.getHoldingById(holdingId) ?: return@runInTransaction null
            if (sellQuantity <= 0.0) return@runInTransaction null

            val isCash = existing.assetClass == AssetClass.CASH.name
            val effectiveSellPrice = if (isCash) 1.0 else sellPricePerUnit.coerceAtLeast(0.0)
            val calc = CurrencyFormatter.calculateSellOutcome(
                existingQuantity = existing.quantity,
                existingAvgCost = if (isCash) null else existing.averageCostPerUnit,
                sellQuantity = sellQuantity,
                sellPricePerUnit = effectiveSellPrice
            )
            val actualSoldQty = sellQuantity.coerceAtMost(existing.quantity)

            if (calc.soldAll) {
                holdingDao.deleteHoldingById(existing.id)
            } else {
                holdingDao.upsertHolding(
                    existing.copy(
                        quantity = calc.remainingQuantity,
                        updatedAt = System.currentTimeMillis()
                    )
                )
            }

            var deposited = false
            if (!isCash && depositProceedsToCash && calc.proceedsNative > 0.0) {
                val existingCash = holdingDao.findCashHoldingByCurrency(existing.nativeCurrency)
                val now = System.currentTimeMillis()
                if (existingCash != null) {
                    holdingDao.upsertHolding(
                        existingCash.copy(
                            quantity = existingCash.quantity + calc.proceedsNative,
                            updatedAt = now
                        )
                    )
                } else {
                    val cur = SupportedCurrency.fromCode(existing.nativeCurrency)
                    holdingDao.upsertHolding(
                        HoldingEntity(
                            symbol = "CASH-${existing.nativeCurrency.uppercase(Locale.US)}",
                            name = "${cur.displayName} Cash",
                            assetClass = AssetClass.CASH.name,
                            quantity = calc.proceedsNative,
                            unitType = UnitType.CURRENCY_UNIT.name,
                            averageCostPerUnit = null,
                            nativeCurrency = existing.nativeCurrency.uppercase(Locale.US),
                            manualPriceOverride = null,
                            accountLabel = "Sale Proceeds",
                            buyDateMillis = sellDateMillis,
                            updatedAt = now
                        )
                    )
                }
                deposited = true
            }

            transactionDao.insertTransaction(
                TransactionEntity(
                    holdingId = existing.id,
                    symbol = existing.symbol,
                    assetName = existing.name,
                    assetClass = existing.assetClass,
                    type = TransactionType.SELL.name,
                    quantity = actualSoldQty,
                    unitType = existing.unitType,
                    unitPriceNative = effectiveSellPrice,
                    totalAmountNative = calc.proceedsNative,
                    nativeCurrency = existing.nativeCurrency,
                    realizedPnlNative = if (isCash) null else calc.realizedGainNative,
                    depositedToCash = deposited,
                    timestampMillis = sellDateMillis,
                    note = when {
                        isCash -> "Cash withdrawal"
                        calc.soldAll -> "Closed position"
                        else -> "Partial sell"
                    }
                )
            )

            recordSnapshotIfNeeded(System.currentTimeMillis(), force = true)

            SellResult(
                symbol = existing.symbol,
                soldQuantity = actualSoldQty,
                unitType = runCatching { UnitType.valueOf(existing.unitType) }.getOrDefault(UnitType.SHARES),
                soldAll = calc.soldAll,
                proceedsNative = calc.proceedsNative,
                nativeCurrency = existing.nativeCurrency,
                realizedGainNative = calc.realizedGainNative,
                depositedToCash = deposited
            )
        }
    }

    suspend fun deleteTransaction(transactionId: Long) = withContext(Dispatchers.IO) {
        ensureInitialTransactionsSeeded()
        runInTransaction {
            val tx = transactionDao.getTransactionById(transactionId) ?: return@runInTransaction
            transactionDao.deleteTransactionById(transactionId)

            val now = System.currentTimeMillis()
            val targetHolding = tx.holdingId?.let { holdingDao.getHoldingById(it) }
                ?: if (tx.holdingId == null) {
                    holdingDao.getAllHoldingsOnce().firstOrNull {
                        it.symbol.equals(tx.symbol, ignoreCase = true)
                    }
                } else {
                    null
                }

            if (targetHolding != null) {
                val isCash = targetHolding.assetClass == AssetClass.CASH.name
                val remainingForHolding = if (isCash) {
                    val allRemainingTxs = transactionDao.getAllTransactionsAscending()
                    val ownCashTxs = allRemainingTxs.filter {
                        it.holdingId == targetHolding.id ||
                            (it.holdingId == null && it.symbol.equals(targetHolding.symbol, ignoreCase = true))
                    }
                    val settledProceedsAsCashCredits = allRemainingTxs
                        .filter {
                            it.type == TransactionType.SELL.name &&
                                it.assetClass != AssetClass.CASH.name &&
                                it.depositedToCash &&
                                it.totalAmountNative > 0.0 &&
                                it.nativeCurrency.equals(targetHolding.nativeCurrency, ignoreCase = true)
                        }
                        .map { sellTx ->
                            TransactionEntity(
                                id = sellTx.id,
                                holdingId = targetHolding.id,
                                symbol = targetHolding.symbol,
                                assetName = targetHolding.name,
                                assetClass = AssetClass.CASH.name,
                                type = TransactionType.BUY.name,
                                quantity = sellTx.totalAmountNative,
                                unitType = UnitType.CURRENCY_UNIT.name,
                                unitPriceNative = 1.0,
                                totalAmountNative = sellTx.totalAmountNative,
                                nativeCurrency = targetHolding.nativeCurrency,
                                realizedPnlNative = null,
                                depositedToCash = false,
                                timestampMillis = sellTx.timestampMillis,
                                note = "Sale proceeds"
                            )
                        }
                    ownCashTxs + settledProceedsAsCashCredits
                } else if (tx.holdingId != null) {
                    transactionDao.getTransactionsForHolding(targetHolding.id)
                } else {
                    transactionDao.getAllTransactionsAscending().filter {
                        it.holdingId == targetHolding.id ||
                            (it.holdingId == null && it.symbol.equals(targetHolding.symbol, ignoreCase = true))
                    }
                }

                if (remainingForHolding.isNotEmpty()) {
                    val replayed = replayHoldingFromTransactions(remainingForHolding, isCash = isCash)
                    if (replayed.quantity <= 1e-8) {
                        holdingDao.deleteHoldingById(targetHolding.id)
                    } else {
                        holdingDao.upsertHolding(
                            targetHolding.copy(
                                quantity = replayed.quantity,
                                averageCostPerUnit = if (isCash) null else replayed.averageCostPerUnit,
                                buyDateMillis = replayed.buyDateMillis,
                                updatedAt = now
                            )
                        )
                    }
                } else {
                    val adjustedQty = if (tx.type == TransactionType.BUY.name) {
                        (targetHolding.quantity - tx.quantity).coerceAtLeast(0.0)
                    } else {
                        targetHolding.quantity + tx.quantity
                    }
                    if (adjustedQty <= 1e-8) {
                        holdingDao.deleteHoldingById(targetHolding.id)
                    } else {
                        holdingDao.upsertHolding(
                            targetHolding.copy(
                                quantity = adjustedQty,
                                updatedAt = now
                            )
                        )
                    }
                }
            } else if (tx.type == TransactionType.SELL.name && tx.note == "Closed position" && tx.holdingId != null) {
                val isCash = tx.assetClass == AssetClass.CASH.name
                val remainingForHolding = transactionDao.getTransactionsForHolding(tx.holdingId)
                val replayed = if (remainingForHolding.isNotEmpty()) {
                    replayHoldingFromTransactions(remainingForHolding, isCash = isCash)
                } else {
                    ReplayedHoldingState(
                        quantity = tx.quantity,
                        averageCostPerUnit = null,
                        buyDateMillis = null
                    )
                }
                if (replayed.quantity > 1e-8) {
                    holdingDao.upsertHolding(
                        HoldingEntity(
                            id = tx.holdingId,
                            symbol = tx.symbol,
                            name = tx.assetName,
                            assetClass = tx.assetClass,
                            quantity = replayed.quantity,
                            unitType = tx.unitType,
                            averageCostPerUnit = if (isCash) null else replayed.averageCostPerUnit,
                            nativeCurrency = tx.nativeCurrency,
                            manualPriceOverride = null,
                            accountLabel = if (isCash) "Primary Account" else "Portfolio",
                            buyDateMillis = replayed.buyDateMillis,
                            updatedAt = now
                        )
                    )
                }
            }

            if (tx.type == TransactionType.SELL.name &&
                tx.assetClass != AssetClass.CASH.name &&
                tx.depositedToCash &&
                tx.totalAmountNative > 0.0
            ) {
                val cashHolding = holdingDao.findCashHoldingByCurrency(tx.nativeCurrency)
                if (cashHolding != null) {
                    val remainingCash = (cashHolding.quantity - tx.totalAmountNative).coerceAtLeast(0.0)
                    if (remainingCash <= 1e-8 && cashHolding.accountLabel == "Sale Proceeds") {
                        holdingDao.deleteHoldingById(cashHolding.id)
                    } else {
                        holdingDao.upsertHolding(
                            cashHolding.copy(
                                quantity = remainingCash,
                                updatedAt = now
                            )
                        )
                    }
                }
            }

            recordSnapshotIfNeeded(now, force = true)
        }
    }

    suspend fun deleteHolding(holdingId: Long) = withContext(Dispatchers.IO) {
        ensureInitialTransactionsSeeded()
        runInTransaction {
            holdingDao.deleteHoldingById(holdingId)
            transactionDao.deleteTransactionsForHolding(holdingId)
            recordSnapshotIfNeeded(System.currentTimeMillis(), force = true)
        }
    }

    suspend fun setDisplayCurrency(currency: SupportedCurrency) {
        prefsRepo.setDisplayCurrency(currency)
    }

    suspend fun toggleBalanceHidden() {
        prefsRepo.toggleBalanceHidden()
    }

    suspend fun setBalanceHidden(hidden: Boolean) {
        prefsRepo.setBalanceHidden(hidden)
    }

    suspend fun setRefreshInterval(interval: RefreshInterval) {
        prefsRepo.setRefreshInterval(interval)
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        prefsRepo.setThemeMode(mode)
    }

    suspend fun setUseCompactNumbers(compact: Boolean) {
        prefsRepo.setUseCompactNumbers(compact)
    }

    suspend fun setShowPnlOnCards(show: Boolean) {
        prefsRepo.setShowPnlOnCards(show)
    }

    suspend fun setShowExtendedHoursPrice(show: Boolean) {
        prefsRepo.setShowExtendedHoursPrice(show)
    }

    suspend fun setDefaultChartTimeframe(timeframe: ChartTimeframe) {
        prefsRepo.setDefaultChartTimeframe(timeframe)
    }

    suspend fun setWidgetDisplayMetric(metric: WidgetDisplayMetric) {
        prefsRepo.setWidgetDisplayMetric(metric)
    }

    suspend fun setWidgetChartTimeframe(timeframe: ChartTimeframe) {
        prefsRepo.setWidgetChartTimeframe(timeframe)
    }

    suspend fun enableAppLock(pin: String, biometricEnabled: Boolean) {
        prefsRepo.enableAppLock(pin, biometricEnabled)
    }

    suspend fun updateAppLockPin(newPin: String) {
        prefsRepo.updatePin(newPin)
    }

    suspend fun disableAppLock() {
        prefsRepo.disableAppLock()
    }

    suspend fun setBiometricUnlockEnabled(enabled: Boolean) {
        prefsRepo.setBiometricUnlockEnabled(enabled)
    }

    suspend fun verifyAppLockPin(pin: String): Boolean {
        return prefsRepo.verifyPin(pin)
    }

    suspend fun exportBackupJson(): ExportBackupResult = withContext(Dispatchers.IO) {
        ensureInitialTransactionsSeeded()
        val holdings = holdingDao.getAllHoldingsOnce()
        val transactions = transactionDao.getAllTransactionsAscending()
        val snapshots = snapshotDao.getAllSnapshotsOnce()
        val prefs = prefsRepo.getCurrentPreferences()

        val payload = ValorisBackupPayload(
            exportedAtMillis = System.currentTimeMillis(),
            holdings = holdings.map { BackupHolding.fromEntity(it) },
            transactions = transactions.map { BackupTransaction.fromEntity(it) },
            snapshots = snapshots.map { BackupSnapshot.fromEntity(it) },
            preferences = BackupPreferences.fromUserPreferences(prefs)
        )
        ExportBackupResult(
            json = PortfolioBackupCodec.encode(payload),
            holdingsCount = holdings.size,
            transactionsCount = transactions.size,
            snapshotsCount = snapshots.size
        )
    }

    suspend fun parseBackupPreview(rawJson: String): Result<BackupPreview> = withContext(Dispatchers.Default) {
        PortfolioBackupCodec.decodeAndValidate(rawJson).map { payload ->
            BackupPreview(
                exportedAtMillis = payload.exportedAtMillis,
                holdingsCount = payload.holdings.size,
                transactionsCount = payload.transactions.size,
                snapshotsCount = payload.snapshots.size,
                payload = payload
            )
        }
    }

    suspend fun restoreFromBackup(payload: ValorisBackupPayload): RestoreResult = withContext(Dispatchers.IO) {
        var restoredHoldings = 0
        var restoredTransactions = 0
        var restoredSnapshots = 0

        runInTransaction {
            transactionDao.deleteAllTransactions()
            holdingDao.deleteAllHoldings()
            snapshotDao.deleteAllSnapshots()

            val holdingIdMap = HashMap<Long, Long>(payload.holdings.size)
            val usedHoldingIds = HashSet<Long>(payload.holdings.size)
            val orderedHoldings = payload.holdings.sortedByDescending { it.id > 0L }

            for (backupHolding in orderedHoldings) {
                val preserveId = if (backupHolding.id > 0L && usedHoldingIds.add(backupHolding.id)) {
                    backupHolding.id
                } else {
                    0L
                }
                val savedId = holdingDao.upsertHolding(backupHolding.toEntity(resolvedId = preserveId))
                val finalId = if (preserveId > 0L) preserveId else savedId
                if (backupHolding.id > 0L && !holdingIdMap.containsKey(backupHolding.id)) {
                    holdingIdMap[backupHolding.id] = finalId
                }
                restoredHoldings++
            }

            if (payload.transactions.isNotEmpty()) {
                val txEntities = payload.transactions.map { backupTx ->
                    val mappedHoldingId = backupTx.holdingId?.let { oldId ->
                        holdingIdMap[oldId] ?: oldId
                    }
                    backupTx.toEntity(resolvedHoldingId = mappedHoldingId, resolvedId = 0L)
                }
                transactionDao.insertTransactions(txEntities)
                restoredTransactions = txEntities.size
            }

            if (payload.snapshots.isNotEmpty()) {
                val snapEntities = payload.snapshots
                    .filter { it.timestamp > 0L && it.totalValueUsd.isFinite() && it.totalValueUsd >= 0.0 }
                    .map { it.toEntity() }
                if (snapEntities.isNotEmpty()) {
                    snapshotDao.insertSnapshots(snapEntities)
                    restoredSnapshots = snapEntities.size
                }
            }
        }

        val prefs = payload.preferences
        if (prefs != null) {
            prefsRepo.restoreNonSecurityPreferences(
                displayCurrency = SupportedCurrency.fromCode(prefs.displayCurrency),
                isBalanceHidden = prefs.isBalanceHidden,
                refreshInterval = RefreshInterval.fromMinutes(prefs.refreshIntervalMinutes),
                themeMode = ThemeMode.fromName(prefs.themeMode),
                useCompactNumbers = prefs.useCompactNumbers,
                showPnlOnCards = prefs.showPnlOnCards,
                showExtendedHoursPrice = prefs.showExtendedHoursPrice,
                defaultChartTimeframe = ChartTimeframe.fromName(prefs.defaultChartTimeframe)
            )
        } else {
            prefsRepo.markInitialHistorySeeded()
        }

        isHistorySeededCached = true
        historyCache.clear()

        RestoreResult(
            holdingsCount = restoredHoldings,
            transactionsCount = restoredTransactions,
            snapshotsCount = restoredSnapshots
        )
    }

    private suspend fun recordSnapshotIfNeeded(now: Long, force: Boolean = false) {
        val holdings = holdingDao.getAllHoldingsOnce().map { it.toDomain() }
        if (holdings.isEmpty()) return

        val latest = snapshotDao.getLatestSnapshot()
        if (!force && latest != null && (now - latest.timestamp) < 15 * 60 * 1000L) {
            return
        }

        val quotes = quoteDao.getAllQuotesOnce().associateBy { it.symbol.uppercase(Locale.US) }
        val fxRates = buildMap {
            SupportedCurrency.entries.forEach { put(it.code, CurrencyFormatter.defaultFallbackRatePerUsd(it.code)) }
            fxRateDao.getAllRatesOnce().forEach { put(it.currencyCode.uppercase(Locale.US), it.ratePerUsd) }
            put("USD", 1.0)
        }

        val totalUsd = holdings.sumOf { h ->
            val q = quotes[h.symbol.uppercase(Locale.US)]
            val unitPrice = when {
                h.assetClass == AssetClass.CASH -> 1.0
                h.manualPriceOverride != null -> h.manualPriceOverride
                q != null -> CurrencyFormatter.unitQuotePerEnteredUnit(
                    assetClass = h.assetClass,
                    unitType = h.unitType,
                    marketQuoteNative = q.priceNative
                )
                else -> h.averageCostPerUnit ?: 0.0
            }
            val cur = when {
                h.assetClass == AssetClass.CASH -> h.nativeCurrency
                h.manualPriceOverride != null -> h.nativeCurrency
                q != null -> q.currency
                else -> h.nativeCurrency
            }
            val nativeVal = h.quantity * unitPrice
            CurrencyFormatter.convert(nativeVal, cur, "USD", fxRates)
        }

        if (totalUsd > 0.0) {
            snapshotDao.insertSnapshot(NetWorthSnapshotEntity(timestamp = now, totalValueUsd = totalUsd))
            snapshotDao.pruneOldSnapshots()
        }
    }

    suspend fun getDashboardSnapshot(): PortfolioDashboardData = withContext(Dispatchers.IO) {
        val holdingEntities = holdingDao.getAllHoldingsOnce()
        val quoteEntities = quoteDao.getAllQuotesOnce()
        val fxEntities = fxRateDao.getAllRatesOnce()
        val snapshots = snapshotDao.getAllSnapshotsOnce()
        val prefs = prefsRepo.getCurrentPreferences()
        withContext(Dispatchers.Default) {
            buildDashboardData(
                holdingEntities = holdingEntities,
                quoteEntities = quoteEntities,
                fxEntities = fxEntities,
                snapshots = snapshots,
                prefs = prefs
            )
        }
    }

    suspend fun getWidgetSnapshotData(
        forceRefreshHistory: Boolean = false
    ): WidgetSourceData = withContext(Dispatchers.IO) {
        val dashboard = getDashboardSnapshot()
        val graphMode = when (dashboard.widgetDisplayMetric) {
            WidgetDisplayMetric.ASSET_VALUE -> MovementGraphMode.ASSET_VALUE
            WidgetDisplayMetric.TOTAL_RETURN -> MovementGraphMode.PROFIT_LOSS
        }
        val movement = if (dashboard.valuedHoldings.isEmpty()) {
            null
        } else {
            runCatching {
                withTimeoutOrNull(6_500L) {
                    fetchPortfolioMovement(
                        timeframe = dashboard.widgetChartTimeframe,
                        graphMode = graphMode,
                        respectBuyDates = true,
                        displayCurrency = dashboard.displayCurrency,
                        includeExtendedHours = dashboard.showExtendedHoursPrice,
                        forceRefresh = forceRefreshHistory
                    )
                }
            }.getOrNull()
        }
        WidgetSourceData(
            dashboard = dashboard,
            movement = movement
        )
    }

    private fun parseSparkline(csv: String): List<Double> {
        if (csv.isBlank()) return emptyList()
        return sparklineCache.getOrPut(csv) {
            if (sparklineCache.size > 120) sparklineCache.clear()
            csv.split(",").mapNotNull { it.toDoubleOrNull() }
        }
    }

    companion object {
        private const val HISTORY_CACHE_TTL_MS = 120_000L

        @Volatile
        private var INSTANCE: PortfolioRepository? = null

        fun getInstance(context: Context): PortfolioRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: run {
                    val appCtx = context.applicationContext
                    val db = ValorisDatabase.getInstance(appCtx)
                    val prefs = UserPreferencesRepository(appCtx)
                    PortfolioRepository(
                        holdingDao = db.holdingDao(),
                        quoteDao = db.quoteDao(),
                        fxRateDao = db.fxRateDao(),
                        snapshotDao = db.snapshotDao(),
                        transactionDao = db.transactionDao(),
                        prefsRepo = prefs,
                        marketApi = NetworkModule.marketApiService,
                        openErApi = NetworkModule.openErFxService,
                        frankfurterApi = NetworkModule.frankfurterFxService,
                        database = db
                    ).also { INSTANCE = it }
                }
            }
        }

        fun pickBestDisplayName(
            longName: String?,
            shortName: String?,
            fallback: String
        ): String {
            val cleanLong = longName?.trim()?.takeIf { it.isNotEmpty() }
            val cleanShort = shortName?.trim()?.takeIf { it.isNotEmpty() }
            return when {
                cleanLong != null && cleanShort != null -> {
                    if (cleanLong.length >= cleanShort.length || cleanLong.startsWith(cleanShort, ignoreCase = true)) {
                        cleanLong
                    } else {
                        cleanShort
                    }
                }
                cleanLong != null -> cleanLong
                cleanShort != null -> cleanShort
                else -> fallback
            }
        }

        internal data class DualTimelineResult(
            val totalTimeline: List<HistoricalPoint>,
            val pnlTimeline: List<PnlTimelinePoint>,
            val periodChangeDisplay: Double,
            val periodChangePercent: Double,
            val latestTotalReturnDisplay: Double,
            val latestUnrealizedPnlDisplay: Double,
            val latestRealizedPnlDisplay: Double,
            val latestReturnPercent: Double
        )

        private data class ReplayPosition(
            val symbol: String,
            var assetClass: AssetClass,
            var unitType: UnitType,
            var nativeCurrency: String,
            var openQty: Double = 0.0,
            var openCostBasisNative: Double = 0.0,
            var realizedPnlNative: Double = 0.0,
            var totalBuysCostNative: Double = 0.0,
            var hasTrackedCost: Boolean = false
        )

        internal fun evaluateValuedHolding(
            holding: Holding,
            cachedQuote: CachedQuoteEntity?,
            latestFxAt: Long?,
            displayCurrency: SupportedCurrency,
            showExtendedHoursPrice: Boolean,
            ratesPerUsd: Map<String, Double>,
            nowMillis: Long,
            sparkline: List<Double> = emptyList()
        ): ValuedHolding {
            val effectiveQuoteNative: Double
            val effectivePrevCloseNative: Double
            val dayChangePct: Double
            val effectiveCurrency: String
            val status: QuoteStatus
            val marketStatus: MarketStatus?
            val isUsingExtended: Boolean
            val quoteTimestamp: Long?
            val convertedSparkline: List<Double>

            when {
                holding.assetClass == AssetClass.CASH -> {
                    effectiveQuoteNative = 1.0
                    effectivePrevCloseNative = 1.0
                    dayChangePct = 0.0
                    effectiveCurrency = holding.nativeCurrency
                    status = QuoteStatus.CASH_PAR
                    marketStatus = null
                    isUsingExtended = false
                    quoteTimestamp = latestFxAt
                    convertedSparkline = emptyList()
                }
                holding.manualPriceOverride != null && holding.manualPriceOverride > 0.0 -> {
                    effectiveQuoteNative = holding.manualPriceOverride
                    effectivePrevCloseNative = holding.manualPriceOverride
                    dayChangePct = 0.0
                    effectiveCurrency = holding.nativeCurrency
                    status = QuoteStatus.MANUAL
                    marketStatus = null
                    isUsingExtended = false
                    quoteTimestamp = holding.updatedAt
                    convertedSparkline = emptyList()
                }
                cachedQuote != null -> {
                    val resolvedMarketStatus = resolveMarketStatus(
                        encodedMarketState = cachedQuote.marketState,
                        nowSec = nowMillis / 1000L,
                        symbol = holding.symbol,
                        assetClass = holding.assetClass,
                        fetchedAtSec = cachedQuote.fetchedAt / 1000L
                    )
                    val extPrice = cachedQuote.extendedPriceNative?.takeIf { it > 0.0 }
                    val useExtended = showExtendedHoursPrice &&
                        resolvedMarketStatus == MarketStatus.CLOSED &&
                        extPrice != null
                    val rawMarketPrice = if (useExtended) extPrice else cachedQuote.priceNative
                    effectiveQuoteNative = CurrencyFormatter.unitQuotePerEnteredUnit(
                        assetClass = holding.assetClass,
                        unitType = holding.unitType,
                        marketQuoteNative = rawMarketPrice
                    )
                    effectivePrevCloseNative = CurrencyFormatter.unitQuotePerEnteredUnit(
                        assetClass = holding.assetClass,
                        unitType = holding.unitType,
                        marketQuoteNative = cachedQuote.previousCloseNative
                    )
                    dayChangePct = if (effectivePrevCloseNative > 0.0) {
                        ((effectiveQuoteNative - effectivePrevCloseNative) / effectivePrevCloseNative) * 100.0
                    } else cachedQuote.dayChangePercent
                    effectiveCurrency = cachedQuote.currency.ifBlank { holding.nativeCurrency }
                    val isFresh = (nowMillis - cachedQuote.fetchedAt) < 20 * 60 * 1000L
                    status = if (isFresh) QuoteStatus.LIVE else QuoteStatus.CACHED
                    marketStatus = resolvedMarketStatus
                    isUsingExtended = useExtended
                    quoteTimestamp = cachedQuote.fetchedAt
                    convertedSparkline = if (holding.assetClass == AssetClass.COMMODITY && holding.unitType == UnitType.GRAMS) {
                        sparkline.map { CurrencyFormatter.unitQuotePerEnteredUnit(holding.assetClass, holding.unitType, it) }
                    } else {
                        sparkline
                    }
                }
                else -> {
                    val fallbackUnit = holding.averageCostPerUnit ?: 0.0
                    effectiveQuoteNative = fallbackUnit
                    effectivePrevCloseNative = fallbackUnit
                    dayChangePct = 0.0
                    effectiveCurrency = holding.nativeCurrency
                    status = QuoteStatus.CACHED
                    marketStatus = null
                    isUsingExtended = false
                    quoteTimestamp = null
                    convertedSparkline = emptyList()
                }
            }

            val totalValueNative = if (holding.assetClass == AssetClass.CASH) {
                holding.quantity
            } else {
                holding.quantity * effectiveQuoteNative
            }

            val prevTotalValueNative = if (holding.assetClass == AssetClass.CASH) {
                holding.quantity
            } else {
                holding.quantity * effectivePrevCloseNative
            }

            val fxToDisplay = CurrencyFormatter.convert(
                amount = 1.0,
                fromCurrency = effectiveCurrency,
                toCurrency = displayCurrency.code,
                ratesPerUsd = ratesPerUsd
            )

            val totalValueDisplay = CurrencyFormatter.convert(
                amount = totalValueNative,
                fromCurrency = effectiveCurrency,
                toCurrency = displayCurrency.code,
                ratesPerUsd = ratesPerUsd
            )

            val totalValueUsd = CurrencyFormatter.convert(
                amount = totalValueNative,
                fromCurrency = effectiveCurrency,
                toCurrency = "USD",
                ratesPerUsd = ratesPerUsd
            )

            val prevValueDisplay = CurrencyFormatter.convert(
                amount = prevTotalValueNative,
                fromCurrency = effectiveCurrency,
                toCurrency = displayCurrency.code,
                ratesPerUsd = ratesPerUsd
            )
            val dayChangeDisplay = totalValueDisplay - prevValueDisplay

            val avgCost = holding.averageCostPerUnit
            val unrealizedGainDisplay: Double?
            val unrealizedGainPercent: Double?
            if (holding.assetClass != AssetClass.CASH && avgCost != null && avgCost > 0.0) {
                val totalCostNative = holding.quantity * avgCost
                val totalCostDisplay = CurrencyFormatter.convert(
                    amount = totalCostNative,
                    fromCurrency = holding.nativeCurrency,
                    toCurrency = displayCurrency.code,
                    ratesPerUsd = ratesPerUsd
                )
                unrealizedGainDisplay = totalValueDisplay - totalCostDisplay
                unrealizedGainPercent = if (totalCostDisplay > 0.0) {
                    (unrealizedGainDisplay / totalCostDisplay) * 100.0
                } else null
            } else {
                unrealizedGainDisplay = null
                unrealizedGainPercent = null
            }

            return ValuedHolding(
                holding = holding.copy(nativeCurrency = effectiveCurrency),
                unitQuoteNative = effectiveQuoteNative,
                previousCloseNative = effectivePrevCloseNative,
                dayChangePercent = dayChangePct,
                totalValueNative = totalValueNative,
                totalValueDisplay = totalValueDisplay,
                totalValueUsd = totalValueUsd,
                dayChangeDisplay = dayChangeDisplay,
                unrealizedGainDisplay = unrealizedGainDisplay,
                unrealizedGainPercent = unrealizedGainPercent,
                portfolioWeightPercent = 0.0,
                fxRateToDisplay = fxToDisplay,
                quoteStatus = status,
                marketStatus = marketStatus,
                isUsingExtendedHoursPrice = isUsingExtended,
                quoteUpdatedAt = quoteTimestamp,
                sparklinePoints = convertedSparkline
            )
        }

        internal fun computeDualMovementTimelines(
            buckets: List<Long>,
            bucketSeconds: Long,
            nowMillis: Long,
            transactions: List<AssetTransaction>,
            currentHoldings: List<Holding>,
            historicalPricesBySymbol: Map<String, List<HistoricalPoint>>,
            cachedQuotePriceBySymbol: Map<String, Double>,
            effectiveCurrencyBySymbol: Map<String, String>,
            fxRates: Map<String, Double>,
            displayCurrency: SupportedCurrency,
            respectBuyDates: Boolean,
            manualOverrideBySymbol: Map<String, Double> = emptyMap()
        ): DualTimelineResult {
            if (buckets.isEmpty()) {
                return DualTimelineResult(
                    totalTimeline = emptyList(),
                    pnlTimeline = emptyList(),
                    periodChangeDisplay = 0.0,
                    periodChangePercent = 0.0,
                    latestTotalReturnDisplay = 0.0,
                    latestUnrealizedPnlDisplay = 0.0,
                    latestRealizedPnlDisplay = 0.0,
                    latestReturnPercent = 0.0
                )
            }

            fun resolveUnitPricePerEnteredUnit(
                sym: String,
                assetClass: AssetClass,
                unitType: UnitType,
                bucketSec: Long,
                fallbackCostPerEnteredUnit: Double?
            ): Double {
                manualOverrideBySymbol[sym]?.takeIf { it > 0.0 }?.let { return it }
                val symPts = historicalPricesBySymbol[sym]
                if (!symPts.isNullOrEmpty()) {
                    val marketPt = findLatestPriceAtOrBefore(symPts, bucketSec) ?: symPts.first().value
                    return CurrencyFormatter.unitQuotePerEnteredUnit(assetClass, unitType, marketPt)
                }
                cachedQuotePriceBySymbol[sym]?.takeIf { it > 0.0 }?.let { remotePrice ->
                    return CurrencyFormatter.unitQuotePerEnteredUnit(assetClass, unitType, remotePrice)
                }
                return fallbackCostPerEnteredUnit ?: 0.0
            }

            val holdingAvgCostBySymbol = currentHoldings.associate {
                it.symbol.uppercase(Locale.US) to it.averageCostPerUnit
            }

            val sortedTxs = transactions.sortedWith(compareBy<AssetTransaction> { it.timestampMillis }.thenBy { it.id })
            val positions = linkedMapOf<String, ReplayPosition>()
            var txIndex = 0
            var totalBuysCapitalDisplay = 0.0

            val lastBucketSec = buckets.last()
            val pnlTimeline = ArrayList<PnlTimelinePoint>(buckets.size)
            val ledgerTotalTimeline = ArrayList<HistoricalPoint>(buckets.size)

            for (bucketSec in buckets) {
                val rawBucketEnd = (bucketSec + bucketSeconds) * 1000L - 1L
                val bucketEndMs = if (bucketSec == lastBucketSec) maxOf(rawBucketEnd, nowMillis) else rawBucketEnd

                while (txIndex < sortedTxs.size && sortedTxs[txIndex].timestampMillis <= bucketEndMs) {
                    val tx = sortedTxs[txIndex++]
                    val symKey = tx.symbol.trim().uppercase(Locale.US)
                    val pos = positions.getOrPut(symKey) {
                        ReplayPosition(
                            symbol = symKey,
                            assetClass = tx.assetClass,
                            unitType = tx.unitType,
                            nativeCurrency = effectiveCurrencyBySymbol[symKey] ?: tx.nativeCurrency
                        )
                    }
                    pos.assetClass = tx.assetClass
                    pos.unitType = tx.unitType
                    val resolvedCur = effectiveCurrencyBySymbol[symKey] ?: tx.nativeCurrency
                    pos.nativeCurrency = resolvedCur

                    if (tx.assetClass == AssetClass.CASH) {
                        when (tx.type) {
                            TransactionType.BUY -> pos.openQty += tx.quantity
                            TransactionType.SELL -> pos.openQty = (pos.openQty - tx.quantity).coerceAtLeast(0.0)
                        }
                    } else {
                        when (tx.type) {
                            TransactionType.BUY -> {
                                pos.openQty += tx.quantity
                                if (tx.unitPriceNative > 0.0) {
                                    val lotCostNative = tx.quantity * tx.unitPriceNative
                                    pos.openCostBasisNative += lotCostNative
                                    pos.totalBuysCostNative += lotCostNative
                                    pos.hasTrackedCost = true
                                    totalBuysCapitalDisplay += CurrencyFormatter.convert(
                                        amount = lotCostNative,
                                        fromCurrency = tx.nativeCurrency,
                                        toCurrency = displayCurrency.code,
                                        ratesPerUsd = fxRates
                                    )
                                }
                            }
                            TransactionType.SELL -> {
                                val soldQty = if (pos.openQty > 0.0) tx.quantity.coerceAtMost(pos.openQty) else tx.quantity
                                if (pos.openQty > 1e-8 && soldQty > 0.0) {
                                    val avgCost = if (pos.hasTrackedCost && pos.openCostBasisNative > 0.0) {
                                        pos.openCostBasisNative / pos.openQty
                                    } else null
                                    val pnlNative = tx.realizedPnlNative ?: avgCost?.let {
                                        (soldQty * tx.unitPriceNative) - (soldQty * it)
                                    }
                                    if (pnlNative != null) {
                                        pos.realizedPnlNative += pnlNative
                                    }
                                    if (avgCost != null) {
                                        pos.openCostBasisNative = (pos.openCostBasisNative - soldQty * avgCost).coerceAtLeast(0.0)
                                    }
                                    pos.openQty = (pos.openQty - soldQty).coerceAtLeast(0.0)
                                    if (pos.openQty <= 1e-8) {
                                        pos.openQty = 0.0
                                        pos.openCostBasisNative = 0.0
                                    }
                                } else if (tx.realizedPnlNative != null) {
                                    pos.realizedPnlNative += tx.realizedPnlNative
                                }

                                if (tx.depositedToCash && tx.totalAmountNative > 0.0) {
                                    val cashSym = "CASH-${tx.nativeCurrency.uppercase(Locale.US)}"
                                    val cashPos = positions.getOrPut(cashSym) {
                                        ReplayPosition(
                                            symbol = cashSym,
                                            assetClass = AssetClass.CASH,
                                            unitType = UnitType.CURRENCY_UNIT,
                                            nativeCurrency = tx.nativeCurrency.uppercase(Locale.US)
                                        )
                                    }
                                    cashPos.openQty += tx.totalAmountNative
                                }
                            }
                        }
                    }
                }

                var valueDisplayAtBucket = 0.0
                var unrealizedDisplayAtBucket = 0.0
                var realizedDisplayAtBucket = 0.0

                for (pos in positions.values) {
                    val cur = pos.nativeCurrency
                    if (pos.assetClass == AssetClass.CASH) {
                        if (pos.openQty > 1e-8) {
                            valueDisplayAtBucket += CurrencyFormatter.convert(
                                amount = pos.openQty,
                                fromCurrency = cur,
                                toCurrency = displayCurrency.code,
                                ratesPerUsd = fxRates
                            )
                        }
                    } else {
                        if (pos.openQty > 1e-8) {
                            val avgCost = if (pos.openCostBasisNative > 0.0) {
                                pos.openCostBasisNative / pos.openQty
                            } else {
                                holdingAvgCostBySymbol[pos.symbol]
                            }
                            val unitPricePerEntered = resolveUnitPricePerEnteredUnit(
                                sym = pos.symbol,
                                assetClass = pos.assetClass,
                                unitType = pos.unitType,
                                bucketSec = bucketSec,
                                fallbackCostPerEnteredUnit = avgCost
                            )
                            val marketValNative = pos.openQty * unitPricePerEntered
                            valueDisplayAtBucket += CurrencyFormatter.convert(
                                amount = marketValNative,
                                fromCurrency = cur,
                                toCurrency = displayCurrency.code,
                                ratesPerUsd = fxRates
                            )
                            if (pos.hasTrackedCost && pos.openCostBasisNative > 0.0 && unitPricePerEntered > 0.0) {
                                val unrealNative = marketValNative - pos.openCostBasisNative
                                unrealizedDisplayAtBucket += CurrencyFormatter.convert(
                                    amount = unrealNative,
                                    fromCurrency = cur,
                                    toCurrency = displayCurrency.code,
                                    ratesPerUsd = fxRates
                                )
                            }
                        }
                        if (abs(pos.realizedPnlNative) > 1e-8) {
                            realizedDisplayAtBucket += CurrencyFormatter.convert(
                                amount = pos.realizedPnlNative,
                                fromCurrency = cur,
                                toCurrency = displayCurrency.code,
                                ratesPerUsd = fxRates
                            )
                        }
                    }
                }

                ledgerTotalTimeline.add(HistoricalPoint(timestampSec = bucketSec, value = valueDisplayAtBucket))
                pnlTimeline.add(
                    PnlTimelinePoint(
                        timestampSec = bucketSec,
                        totalReturnDisplay = unrealizedDisplayAtBucket + realizedDisplayAtBucket,
                        unrealizedDisplay = unrealizedDisplayAtBucket,
                        realizedDisplay = realizedDisplayAtBucket
                    )
                )
            }

            // If user toggles off trade-date replay, backtest current holdings across the whole period for Asset Value
            val totalTimeline = if (respectBuyDates && sortedTxs.isNotEmpty()) {
                ledgerTotalTimeline
            } else {
                buckets.map { bucketSec ->
                    val totalAtBucket = currentHoldings.sumOf { holding ->
                        val symKey = holding.symbol.uppercase(Locale.US)
                        val cur = effectiveCurrencyBySymbol[symKey] ?: holding.nativeCurrency
                        val valueNative = if (holding.assetClass == AssetClass.CASH) {
                            holding.quantity
                        } else {
                            val unitPricePerEntered = holding.manualPriceOverride
                                ?: resolveUnitPricePerEnteredUnit(
                                    sym = symKey,
                                    assetClass = holding.assetClass,
                                    unitType = holding.unitType,
                                    bucketSec = bucketSec,
                                    fallbackCostPerEnteredUnit = holding.averageCostPerUnit
                                )
                            holding.quantity * unitPricePerEntered
                        }
                        CurrencyFormatter.convert(valueNative, cur, displayCurrency.code, fxRates)
                    }
                    HistoricalPoint(timestampSec = bucketSec, value = totalAtBucket)
                }
            }

            val firstNonZero = totalTimeline.firstOrNull { it.value > 0.0 }?.value ?: 0.0
            val lastVal = totalTimeline.lastOrNull()?.value ?: 0.0
            val periodChange = lastVal - firstNonZero
            val periodPct = if (firstNonZero > 0.0) (periodChange / firstNonZero) * 100.0 else 0.0

            val lastPnl = pnlTimeline.lastOrNull()
            val latestTotalReturn = lastPnl?.totalReturnDisplay ?: 0.0
            val latestUnrealized = lastPnl?.unrealizedDisplay ?: 0.0
            val latestRealized = lastPnl?.realizedDisplay ?: 0.0
            val returnPct = if (totalBuysCapitalDisplay > 0.0) {
                (latestTotalReturn / totalBuysCapitalDisplay) * 100.0
            } else 0.0

            return DualTimelineResult(
                totalTimeline = totalTimeline,
                pnlTimeline = pnlTimeline,
                periodChangeDisplay = periodChange,
                periodChangePercent = periodPct,
                latestTotalReturnDisplay = latestTotalReturn,
                latestUnrealizedPnlDisplay = latestUnrealized,
                latestRealizedPnlDisplay = latestRealized,
                latestReturnPercent = returnPct
            )
        }

        internal fun resolvePreviousClose(
            currentPrice: Double,
            previousClose: Double?,
            oneDayChartPreviousClose: Double?
        ): Double {
            return previousClose?.takeIf { it > 0.0 }
                ?: oneDayChartPreviousClose?.takeIf { it > 0.0 }
                ?: currentPrice
        }

        private val NEW_YORK_TZ: TimeZone = TimeZone.getTimeZone("America/New_York")

        private val CME_GLOBEX_COMMODITY_SYMBOLS = setOf(
            "GC=F", "MGC=F", "SI=F", "SIL=F", "HG=F", "PL=F", "PA=F", "ALI=F",
            "CL=F", "MCL=F", "QM=F", "BZ=F", "NG=F", "QG=F", "HO=F", "RB=F",
            "ZC=F", "ZW=F", "ZS=F", "ZL=F", "ZM=F", "LE=F", "GF=F", "HE=F"
        )

        private fun isGlobexOpenHourNewYork(epochSec: Long): Boolean {
            val cal = Calendar.getInstance(NEW_YORK_TZ).apply {
                timeInMillis = epochSec * 1000L
            }
            val day = cal.get(Calendar.DAY_OF_WEEK)
            val hour = cal.get(Calendar.HOUR_OF_DAY)
            return when (day) {
                Calendar.SATURDAY -> false
                Calendar.SUNDAY -> hour >= 18
                Calendar.FRIDAY -> hour < 17
                // Mon-Thu: daily 60-minute COMEX/NYMEX maintenance & settlement break from 17:00 to 18:00 ET
                else -> hour != 17
            }
        }

        private fun isSpotMetalsFxOpenHourNewYork(epochSec: Long): Boolean {
            val cal = Calendar.getInstance(NEW_YORK_TZ).apply {
                timeInMillis = epochSec * 1000L
            }
            val day = cal.get(Calendar.DAY_OF_WEEK)
            val hour = cal.get(Calendar.HOUR_OF_DAY)
            return when (day) {
                Calendar.SATURDAY -> false
                Calendar.SUNDAY -> hour >= 17
                Calendar.FRIDAY -> hour < 17
                else -> true
            }
        }

        internal fun resolveMarketStatus(
            encodedMarketState: String?,
            nowSec: Long = System.currentTimeMillis() / 1000L,
            symbol: String? = null,
            assetClass: AssetClass? = null,
            fetchedAtSec: Long? = null
        ): MarketStatus? {
            val cleanSymbol = symbol?.trim()?.uppercase(Locale.US).orEmpty()
            val parts = encodedMarketState?.split("|").orEmpty()
            val stateToken = parts.getOrNull(0)?.trim()?.uppercase(Locale.US).orEmpty()

            val isGlobexCommodity = cleanSymbol in CME_GLOBEX_COMMODITY_SYMBOLS ||
                cleanSymbol.endsWith("=F") ||
                (assetClass == AssetClass.COMMODITY && cleanSymbol.isNotEmpty() && !cleanSymbol.endsWith("=X"))

            if (isGlobexCommodity) {
                if (!isGlobexOpenHourNewYork(nowSec)) {
                    return MarketStatus.CLOSED
                }
                val honorExchangeClosed = stateToken == "CLOSED" && (
                    fetchedAtSec == null ||
                        ((nowSec - fetchedAtSec) in 0L..7200L && isGlobexOpenHourNewYork(fetchedAtSec))
                    )
                return if (honorExchangeClosed) MarketStatus.CLOSED else MarketStatus.OPEN
            }

            if (cleanSymbol.endsWith("=X")) {
                if (!isSpotMetalsFxOpenHourNewYork(nowSec)) {
                    return MarketStatus.CLOSED
                }
                val honorExchangeClosed = stateToken == "CLOSED" && (
                    fetchedAtSec == null ||
                        ((nowSec - fetchedAtSec) in 0L..7200L && isSpotMetalsFxOpenHourNewYork(fetchedAtSec))
                    )
                return if (honorExchangeClosed) MarketStatus.CLOSED else MarketStatus.OPEN
            }

            if (encodedMarketState.isNullOrBlank()) return null
            val regStart = parts.getOrNull(1)?.toLongOrNull()
            val regEnd = parts.getOrNull(2)?.toLongOrNull()

            if (regStart != null && regEnd != null && regEnd > regStart) {
                if (stateToken == "CLOSED") return MarketStatus.CLOSED
                return if (nowSec in regStart..regEnd) MarketStatus.OPEN else MarketStatus.CLOSED
            }

            return when (stateToken) {
                "REGULAR", "OPEN" -> MarketStatus.OPEN
                "CLOSED", "PRE", "POST", "PREPRE", "POSTPOST" -> MarketStatus.CLOSED
                else -> null
            }
        }

        internal fun extractExtendedHoursPrice(
            marketState: String?,
            regularMarketPrice: Double,
            regularMarketTimeSec: Long?,
            regularStartSec: Long?,
            regularEndSec: Long?,
            preMarketPrice: Double?,
            postMarketPrice: Double?,
            timestamps: List<Long>,
            closes: List<Double?>
        ): Double? {
            val state = marketState?.trim()?.uppercase(Locale.US).orEmpty()
            when (state) {
                "PRE", "PREPRE" -> preMarketPrice?.takeIf { it > 0.0 }?.let { return it }
                "POST", "POSTPOST" -> postMarketPrice?.takeIf { it > 0.0 }?.let { return it }
            }

            val latestExtendedCandle = timestamps.zip(closes)
                .lastOrNull { (ts, close) ->
                    if (close == null || close <= 0.0) return@lastOrNull false
                    when {
                        regularMarketTimeSec != null && regularMarketTimeSec > 0L ->
                            ts > regularMarketTimeSec
                        regularStartSec != null && regularEndSec != null && regularEndSec > regularStartSec ->
                            ts < regularStartSec || ts > regularEndSec
                        else -> false
                    }
                }
                ?.second

            if (latestExtendedCandle != null) return latestExtendedCandle

            if (state != "REGULAR" && state != "OPEN" && state != "PRE" && state != "PREPRE") {
                return postMarketPrice?.takeIf { it > 0.0 } ?: preMarketPrice?.takeIf { it > 0.0 }
            }
            return null
        }

        private fun resolveEffectiveQuotePrice(
            quote: CachedQuoteEntity?,
            includeExtendedHours: Boolean,
            nowSec: Long
        ): Double? {
            if (quote == null) return null
            val extPrice = quote.extendedPriceNative?.takeIf { it > 0.0 }
            val useExtended = includeExtendedHours &&
                extPrice != null &&
                resolveMarketStatus(
                    encodedMarketState = quote.marketState,
                    nowSec = nowSec,
                    symbol = quote.symbol,
                    fetchedAtSec = quote.fetchedAt / 1000L
                ) == MarketStatus.CLOSED
            return if (useExtended) extPrice else quote.priceNative
        }

        private fun findLatestPriceAtOrBefore(
            sortedPoints: List<HistoricalPoint>,
            bucketSec: Long
        ): Double? {
            if (sortedPoints.isEmpty()) return null
            var lo = 0
            var hi = sortedPoints.lastIndex
            var ansIdx = -1
            while (lo <= hi) {
                val mid = (lo + hi) ushr 1
                if (sortedPoints[mid].timestampSec <= bucketSec) {
                    ansIdx = mid
                    lo = mid + 1
                } else {
                    hi = mid - 1
                }
            }
            return if (ansIdx >= 0) sortedPoints[ansIdx].value else sortedPoints[0].value
        }

        data class ReplayedHoldingState(
            val quantity: Double,
            val averageCostPerUnit: Double?,
            val buyDateMillis: Long?
        )

        fun replayHoldingFromTransactions(
            transactions: List<TransactionEntity>,
            isCash: Boolean
        ): ReplayedHoldingState {
            var qty = 0.0
            var avgCost: Double? = null
            var buyDateMillis: Long? = null
            val ordered = transactions.sortedWith(
                compareBy<TransactionEntity> { it.timestampMillis }.thenBy { it.id }
            )
            for (tx in ordered) {
                if (tx.type == TransactionType.BUY.name) {
                    if (tx.quantity > 0.0) {
                        avgCost = if (isCash) {
                            null
                        } else {
                            CurrencyFormatter.calculateWeightedAverageCost(
                                existingQuantity = qty,
                                existingAvgCost = avgCost,
                                addedQuantity = tx.quantity,
                                newBuyPrice = tx.unitPriceNative.takeIf { it > 0.0 }
                            )
                        }
                        qty += tx.quantity
                        buyDateMillis = if (buyDateMillis == null) {
                            tx.timestampMillis
                        } else {
                            minOf(buyDateMillis, tx.timestampMillis)
                        }
                    }
                } else {
                    qty = (qty - tx.quantity).coerceAtLeast(0.0)
                    if (qty <= 1e-8) {
                        qty = 0.0
                        avgCost = null
                        buyDateMillis = null
                    }
                }
            }
            return ReplayedHoldingState(
                quantity = qty,
                averageCostPerUnit = avgCost,
                buyDateMillis = buyDateMillis
            )
        }
    }
}
