package com.example

import com.example.domain.model.UnitType
import com.example.util.CurrencyFormatter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PortfolioMathTest {

    private val sampleRates = mapOf(
        "USD" to 1.0,
        "IDR" to 16000.0,
        "SGD" to 1.35,
        "EUR" to 0.90
    )

    @Test
    fun convert_usd_to_idr_and_cross_currency() {
        val usdToIdr = CurrencyFormatter.convert(100.0, "USD", "IDR", sampleRates)
        assertEquals(1_600_000.0, usdToIdr, 0.01)

        val sgdToIdr = CurrencyFormatter.convert(135.0, "SGD", "IDR", sampleRates)
        assertEquals(1_600_000.0, sgdToIdr, 0.01)

        val idrToUsd = CurrencyFormatter.convert(1_600_000.0, "IDR", "USD", sampleRates)
        assertEquals(100.0, idrToUsd, 0.001)
    }

    @Test
    fun format_idr_and_usd_amounts() {
        assertEquals("Rp1.600.000", CurrencyFormatter.formatAmount(1_600_000.0, "IDR"))
        assertEquals("$1,250.50", CurrencyFormatter.formatAmount(1250.5, "USD"))
        assertEquals("Rp••••••••", CurrencyFormatter.formatAmount(1_600_000.0, "IDR", isHidden = true))
    }

    @Test
    fun format_quantity_units() {
        assertEquals("10 g", CurrencyFormatter.formatQuantity(10.0, UnitType.GRAMS))
        assertEquals("2.5 sh", CurrencyFormatter.formatQuantity(2.5, UnitType.SHARES))
        assertEquals("1.25 oz", CurrencyFormatter.formatQuantity(1.25, UnitType.TROY_OZ))
    }

    @Test
    fun format_held_duration() {
        val dayMs = 86_400_000L
        val now = 1_700_000_000_000L
        assertEquals("Today", CurrencyFormatter.formatHeldDuration(now, now))
        assertEquals("15 days", CurrencyFormatter.formatHeldDuration(now - 15 * dayMs, now))
        assertEquals("2 mo", CurrencyFormatter.formatHeldDuration(now - 65 * dayMs, now))
        assertEquals("1y 2m", CurrencyFormatter.formatHeldDuration(now - 430 * dayMs, now))
    }

    @Test
    fun weighted_average_cost_and_compact_pnl_formatting() {
        val avg1 = CurrencyFormatter.calculateWeightedAverageCost(
            existingQuantity = 10.0,
            existingAvgCost = 100.0,
            addedQuantity = 10.0,
            newBuyPrice = 150.0
        )
        assertEquals(125.0, avg1!!, 0.001)

        val avgFromNull = CurrencyFormatter.calculateWeightedAverageCost(
            existingQuantity = 5.0,
            existingAvgCost = null,
            addedQuantity = 5.0,
            newBuyPrice = 200.0
        )
        assertEquals(200.0, avgFromNull!!, 0.001)

        assertEquals(
            "+Rp12,5 jt",
            CurrencyFormatter.formatPreferenceAmount(
                amount = 12_500_000.0,
                currencyCode = "IDR",
                isHidden = false,
                useCompact = true,
                showSign = true
            )
        )
    }

    @Test
    fun sell_calculation_partial_and_full() {
        val partial = CurrencyFormatter.calculateSellOutcome(
            existingQuantity = 10.0,
            existingAvgCost = 100.0,
            sellQuantity = 4.0,
            sellPricePerUnit = 150.0
        )
        assertEquals(6.0, partial.remainingQuantity, 0.0001)
        assertEquals(false, partial.soldAll)
        assertEquals(600.0, partial.proceedsNative, 0.001)
        assertEquals(200.0, partial.realizedGainNative!!, 0.001)
        assertEquals(50.0, partial.realizedGainPercent!!, 0.001)

        val full = CurrencyFormatter.calculateSellOutcome(
            existingQuantity = 10.0,
            existingAvgCost = 100.0,
            sellQuantity = 10.0,
            sellPricePerUnit = 90.0
        )
        assertEquals(0.0, full.remainingQuantity, 0.0001)
        assertEquals(true, full.soldAll)
        assertEquals(900.0, full.proceedsNative, 0.001)
        assertEquals(-100.0, full.realizedGainNative!!, 0.001)
    }

    @Test
    fun resolve_previous_close_prefers_positive_previous_close() {
        val prev = com.example.data.repository.PortfolioRepository.resolvePreviousClose(
            currentPrice = 105.0,
            previousClose = 100.0,
            oneDayChartPreviousClose = 95.0
        )
        assertEquals(100.0, prev, 0.0001)

        val fallbackTo1dChartPrev = com.example.data.repository.PortfolioRepository.resolvePreviousClose(
            currentPrice = 105.0,
            previousClose = null,
            oneDayChartPreviousClose = 102.0
        )
        assertEquals(102.0, fallbackTo1dChartPrev, 0.0001)

        val fallbackToCurrent = com.example.data.repository.PortfolioRepository.resolvePreviousClose(
            currentPrice = 105.0,
            previousClose = 0.0,
            oneDayChartPreviousClose = null
        )
        assertEquals(105.0, fallbackToCurrent, 0.0001)
    }

    @Test
    fun resolve_market_status_open_and_closed() {
        val MarketOpen = com.example.domain.model.MarketStatus.OPEN
        val MarketClosed = com.example.domain.model.MarketStatus.CLOSED

        assertEquals(
            MarketOpen,
            com.example.data.repository.PortfolioRepository.resolveMarketStatus(
                encodedMarketState = "REGULAR|1000|2000",
                nowSec = 1500L
            )
        )
        assertEquals(
            MarketClosed,
            com.example.data.repository.PortfolioRepository.resolveMarketStatus(
                encodedMarketState = "REGULAR|1000|2000",
                nowSec = 2500L
            )
        )
        assertEquals(
            MarketClosed,
            com.example.data.repository.PortfolioRepository.resolveMarketStatus(
                encodedMarketState = "POST|1000|2000",
                nowSec = 2500L
            )
        )
        assertEquals(
            MarketClosed,
            com.example.data.repository.PortfolioRepository.resolveMarketStatus(
                encodedMarketState = "CLOSED|1000|2000",
                nowSec = 1500L
            )
        )
        assertEquals(
            MarketOpen,
            com.example.data.repository.PortfolioRepository.resolveMarketStatus(
                encodedMarketState = "REGULAR",
                nowSec = 1500L
            )
        )
        assertEquals(
            MarketClosed,
            com.example.data.repository.PortfolioRepository.resolveMarketStatus(
                encodedMarketState = "CLOSED",
                nowSec = 1500L
            )
        )
    }

    @Test
    fun extract_extended_hours_price_handles_pre_post_and_candles() {
        val fromPostCandle = com.example.data.repository.PortfolioRepository.extractExtendedHoursPrice(
            marketState = "POST",
            regularMarketPrice = 100.0,
            regularMarketTimeSec = 1600L,
            regularStartSec = 1000L,
            regularEndSec = 1600L,
            preMarketPrice = null,
            postMarketPrice = null,
            timestamps = listOf(1500L, 1600L, 1650L, 1700L),
            closes = listOf(99.5, 100.0, 101.2, 102.5)
        )
        assertEquals(102.5, fromPostCandle!!, 0.0001)

        val fromPreMeta = com.example.data.repository.PortfolioRepository.extractExtendedHoursPrice(
            marketState = "PRE",
            regularMarketPrice = 100.0,
            regularMarketTimeSec = 900L,
            regularStartSec = 1000L,
            regularEndSec = 1600L,
            preMarketPrice = 103.75,
            postMarketPrice = null,
            timestamps = emptyList(),
            closes = emptyList()
        )
        assertEquals(103.75, fromPreMeta!!, 0.0001)

        val noExtendedWhenRegular = com.example.data.repository.PortfolioRepository.extractExtendedHoursPrice(
            marketState = "REGULAR",
            regularMarketPrice = 100.0,
            regularMarketTimeSec = 1300L,
            regularStartSec = 1000L,
            regularEndSec = 1600L,
            preMarketPrice = null,
            postMarketPrice = null,
            timestamps = listOf(1200L, 1250L, 1300L),
            closes = listOf(99.0, 99.5, 100.0)
        )
        assertEquals(null, noExtendedWhenRegular)
    }

    @Test
    fun chart_timeframes_and_axis_date_formatting() {
        val frames = com.example.domain.model.ChartTimeframe.entries
        assertEquals(
            listOf("1D", "5D", "1M", "3M", "YTD", "1Y", "5Y", "ALL"),
            frames.map { it.label }
        )
        assertEquals("ytd", com.example.domain.model.ChartTimeframe.YTD.range)
        assertEquals("1y", com.example.domain.model.ChartTimeframe.YEAR.range)
        assertEquals("5y", com.example.domain.model.ChartTimeframe.FIVE_YEARS.range)
        assertEquals("max", com.example.domain.model.ChartTimeframe.ALL.range)

        // Nov 19, 2026 UTC noon-ish -> month-year for 5Y/ALL and full date for scrub
        val sampleTsSec = 1_795_090_000L
        assertEquals(
            CurrencyFormatter.formatMonthYear(sampleTsSec),
            CurrencyFormatter.formatChartAxisDate(sampleTsSec, com.example.domain.model.ChartTimeframe.FIVE_YEARS)
        )
        assertEquals(
            CurrencyFormatter.formatMonthYear(sampleTsSec),
            CurrencyFormatter.formatChartAxisDate(sampleTsSec, com.example.domain.model.ChartTimeframe.ALL)
        )
        assertEquals(
            CurrencyFormatter.formatDate(sampleTsSec * 1000L),
            CurrencyFormatter.formatChartScrubDate(sampleTsSec, com.example.domain.model.ChartTimeframe.YTD)
        )
        assertEquals(
            com.example.domain.model.ChartTimeframe.YEAR,
            com.example.domain.model.ChartTimeframe.fromName("YEAR")
        )
        assertEquals(
            com.example.domain.model.ChartTimeframe.MONTH,
            com.example.domain.model.ChartTimeframe.fromName(null)
        )
        assertEquals(
            com.example.domain.model.ChartTimeframe.MONTH,
            com.example.domain.model.ChartTimeframe.fromName("UNKNOWN")
        )
    }

    @Test
    fun app_lock_pin_hashing_and_verification() {
        val salt = com.example.util.AppLockSecurity.generateSaltHex()
        assertEquals(32, salt.length)
        val hash = com.example.util.AppLockSecurity.hashPin("2580", salt)
        assertEquals(true, com.example.util.AppLockSecurity.verifyPin("2580", hash, salt))
        assertEquals(false, com.example.util.AppLockSecurity.verifyPin("2581", hash, salt))
        assertEquals(false, com.example.util.AppLockSecurity.verifyPin("258", hash, salt))
        assertEquals(false, com.example.util.AppLockSecurity.verifyPin("2580", hash, ""))
    }

    @Test
    fun dual_movement_timeline_replay_asset_value_and_total_pnl_with_buys_and_sells() {
        val daySec = 86_400L
        val b0 = 10_000L * daySec
        val b1 = b0 + daySec
        val b2 = b0 + 2 * daySec
        val b3 = b0 + 3 * daySec
        val b4 = b0 + 4 * daySec
        val buckets = listOf(b0, b1, b2, b3, b4)

        val txs = listOf(
            com.example.domain.model.AssetTransaction(
                id = 1L,
                symbol = "AAPL",
                assetName = "Apple Inc.",
                assetClass = com.example.domain.model.AssetClass.STOCK,
                type = com.example.domain.model.TransactionType.BUY,
                quantity = 10.0,
                unitType = UnitType.SHARES,
                unitPriceNative = 100.0,
                totalAmountNative = 1000.0,
                nativeCurrency = "USD",
                timestampMillis = b1 * 1000L + 3_600_000L
            ),
            com.example.domain.model.AssetTransaction(
                id = 2L,
                symbol = "AAPL",
                assetName = "Apple Inc.",
                assetClass = com.example.domain.model.AssetClass.STOCK,
                type = com.example.domain.model.TransactionType.BUY,
                quantity = 10.0,
                unitType = UnitType.SHARES,
                unitPriceNative = 120.0,
                totalAmountNative = 1200.0,
                nativeCurrency = "USD",
                timestampMillis = b2 * 1000L + 3_600_000L
            ),
            com.example.domain.model.AssetTransaction(
                id = 3L,
                symbol = "AAPL",
                assetName = "Apple Inc.",
                assetClass = com.example.domain.model.AssetClass.STOCK,
                type = com.example.domain.model.TransactionType.SELL,
                quantity = 10.0,
                unitType = UnitType.SHARES,
                unitPriceNative = 150.0,
                totalAmountNative = 1500.0,
                nativeCurrency = "USD",
                realizedPnlNative = 400.0,
                depositedToCash = true,
                timestampMillis = b3 * 1000L + 3_600_000L
            ),
            com.example.domain.model.AssetTransaction(
                id = 4L,
                symbol = "AAPL",
                assetName = "Apple Inc.",
                assetClass = com.example.domain.model.AssetClass.STOCK,
                type = com.example.domain.model.TransactionType.SELL,
                quantity = 10.0,
                unitType = UnitType.SHARES,
                unitPriceNative = 160.0,
                totalAmountNative = 1600.0,
                nativeCurrency = "USD",
                realizedPnlNative = 500.0,
                depositedToCash = false,
                timestampMillis = b4 * 1000L + 3_600_000L
            )
        )

        val history = mapOf(
            "AAPL" to listOf(
                com.example.domain.model.HistoricalPoint(b0, 95.0),
                com.example.domain.model.HistoricalPoint(b1, 110.0),
                com.example.domain.model.HistoricalPoint(b2, 130.0),
                com.example.domain.model.HistoricalPoint(b3, 140.0),
                com.example.domain.model.HistoricalPoint(b4, 160.0)
            )
        )

        val res = com.example.data.repository.PortfolioRepository.computeDualMovementTimelines(
            buckets = buckets,
            bucketSeconds = daySec,
            nowMillis = (b4 + daySec) * 1000L,
            transactions = txs,
            currentHoldings = emptyList(),
            historicalPricesBySymbol = history,
            cachedQuotePriceBySymbol = mapOf("AAPL" to 160.0),
            effectiveCurrencyBySymbol = mapOf("AAPL" to "USD"),
            fxRates = sampleRates,
            displayCurrency = com.example.domain.model.SupportedCurrency.USD,
            respectBuyDates = true
        )

        // Asset Values: b0=0, b1=10*110=1100, b2=20*130=2600, b3=10*140+1500cash=2900, b4=1500cash
        assertEquals(0.0, res.totalTimeline[0].value, 0.01)
        assertEquals(1100.0, res.totalTimeline[1].value, 0.01)
        assertEquals(2600.0, res.totalTimeline[2].value, 0.01)
        assertEquals(2900.0, res.totalTimeline[3].value, 0.01)
        assertEquals(1500.0, res.totalTimeline[4].value, 0.01)

        // Total Profit & Loss (Unrealized + Realized):
        // b0: 0
        // b1: unreal=100, real=0 -> 100
        // b2: unreal=400, real=0 -> 400
        // b3: unreal=(1400-1100)=300, real=400 -> 700
        // b4: unreal=0, real=900 -> 900
        assertEquals(0.0, res.pnlTimeline[0].totalReturnDisplay, 0.01)
        assertEquals(100.0, res.pnlTimeline[1].totalReturnDisplay, 0.01)
        assertEquals(400.0, res.pnlTimeline[2].totalReturnDisplay, 0.01)
        assertEquals(300.0, res.pnlTimeline[3].unrealizedDisplay, 0.01)
        assertEquals(400.0, res.pnlTimeline[3].realizedDisplay, 0.01)
        assertEquals(700.0, res.pnlTimeline[3].totalReturnDisplay, 0.01)
        assertEquals(0.0, res.pnlTimeline[4].unrealizedDisplay, 0.01)
        assertEquals(900.0, res.pnlTimeline[4].realizedDisplay, 0.01)
        assertEquals(900.0, res.pnlTimeline[4].totalReturnDisplay, 0.01)
        assertEquals((900.0 / 2200.0) * 100.0, res.latestReturnPercent, 0.01)
    }

    @Test
    fun trend_chart_downsampling_caps_points_and_preserves_endpoints() {
        val short = listOf(10.0, 20.0, 30.0)
        assertEquals(short, com.example.ui.components.downsampleDoubles(short, maxPoints = 56))

        val dense = (0 until 260).map { it.toDouble() }
        val sampled = com.example.ui.components.downsampleDoubles(dense, maxPoints = 56)
        assertEquals(56, sampled.size)
        assertEquals(0.0, sampled.first(), 0.0001)
        assertEquals(259.0, sampled.last(), 0.0001)
    }

    @Test
    fun normalized_range_percent_calculates_compounded_return_between_indices() {
        // Asset rose +20% from t0 to t1 (1.2x), then to +50% at t2 (1.5x): 1.2 -> 1.5 is +25%
        assertEquals(
            25.0,
            com.example.ui.components.computeNormalizedRangePercent(20.0, 50.0),
            0.0001
        )
        // Asset rose +25% (1.25x) then dropped back to 0% (1.0x): 1.25 -> 1.0 is -20%
        assertEquals(
            -20.0,
            com.example.ui.components.computeNormalizedRangePercent(25.0, 0.0),
            0.0001
        )
        // Equal endpoints -> 0%
        assertEquals(
            0.0,
            com.example.ui.components.computeNormalizedRangePercent(12.5, 12.5),
            0.0001
        )
    }

    @Test
    fun replay_holding_from_transactions_reverts_quantity_avg_cost_and_buy_date() {
        val buy1 = com.example.data.local.TransactionEntity(
            id = 1L,
            holdingId = 10L,
            symbol = "AAPL",
            assetName = "Apple Inc.",
            assetClass = "STOCK",
            type = "BUY",
            quantity = 10.0,
            unitType = "SHARES",
            unitPriceNative = 100.0,
            totalAmountNative = 1000.0,
            nativeCurrency = "USD",
            timestampMillis = 2_000L
        )
        val buy2 = com.example.data.local.TransactionEntity(
            id = 2L,
            holdingId = 10L,
            symbol = "AAPL",
            assetName = "Apple Inc.",
            assetClass = "STOCK",
            type = "BUY",
            quantity = 10.0,
            unitType = "SHARES",
            unitPriceNative = 150.0,
            totalAmountNative = 1500.0,
            nativeCurrency = "USD",
            timestampMillis = 1_000L
        )
        val sell1 = com.example.data.local.TransactionEntity(
            id = 3L,
            holdingId = 10L,
            symbol = "AAPL",
            assetName = "Apple Inc.",
            assetClass = "STOCK",
            type = "SELL",
            quantity = 5.0,
            unitType = "SHARES",
            unitPriceNative = 160.0,
            totalAmountNative = 800.0,
            nativeCurrency = "USD",
            timestampMillis = 3_000L
        )

        val combined = com.example.data.repository.PortfolioRepository.replayHoldingFromTransactions(
            transactions = listOf(buy1, buy2, sell1),
            isCash = false
        )
        assertEquals(15.0, combined.quantity, 0.0001)
        assertEquals(125.0, combined.averageCostPerUnit!!, 0.0001)
        assertEquals(1_000L, combined.buyDateMillis)

        // Deleting buy2 reverts quantity back to 10 - 5 = 5, avgCost back to 100.0, buyDate back to 2_000L
        val afterDeletingBuy2 = com.example.data.repository.PortfolioRepository.replayHoldingFromTransactions(
            transactions = listOf(buy1, sell1),
            isCash = false
        )
        assertEquals(5.0, afterDeletingBuy2.quantity, 0.0001)
        assertEquals(100.0, afterDeletingBuy2.averageCostPerUnit!!, 0.0001)
        assertEquals(2_000L, afterDeletingBuy2.buyDateMillis)

        // Deleting buy2 when there was no sell reverts to original buy1 state (10 @ 100.0)
        val afterDeletingOnlyNewBuy = com.example.data.repository.PortfolioRepository.replayHoldingFromTransactions(
            transactions = listOf(buy1),
            isCash = false
        )
        assertEquals(10.0, afterDeletingOnlyNewBuy.quantity, 0.0001)
        assertEquals(100.0, afterDeletingOnlyNewBuy.averageCostPerUnit!!, 0.0001)
        assertEquals(2_000L, afterDeletingOnlyNewBuy.buyDateMillis)
    }

    @Test
    fun pick_best_display_name_avoids_truncated_short_name() {
        val resolved = com.example.data.repository.PortfolioRepository.pickBestDisplayName(
            longName = "Vanguard Total World Stock Index Fund ETF Shares",
            shortName = "Vanguard Total World Stock Inde",
            fallback = "VT"
        )
        assertEquals("Vanguard Total World Stock Index Fund ETF Shares", resolved)

        val fallbackToShort = com.example.data.repository.PortfolioRepository.pickBestDisplayName(
            longName = null,
            shortName = "Bank Central Asia",
            fallback = "BBCA.JK"
        )
        assertEquals("Bank Central Asia", fallbackToShort)

        val fallbackToSymbol = com.example.data.repository.PortfolioRepository.pickBestDisplayName(
            longName = "   ",
            shortName = null,
            fallback = "CUSTOM"
        )
        assertEquals("CUSTOM", fallbackToSymbol)
    }

    @Test
    fun comex_and_nymex_commodities_close_during_maintenance_and_weekend() {
        val nyTz = java.util.TimeZone.getTimeZone("America/New_York")
        fun nyEpochSec(year: Int, monthZeroBased: Int, day: Int, hour: Int, minute: Int): Long {
            val cal = java.util.Calendar.getInstance(nyTz)
            cal.clear()
            cal.set(year, monthZeroBased, day, hour, minute, 0)
            return cal.timeInMillis / 1000L
        }

        val MarketOpen = com.example.domain.model.MarketStatus.OPEN
        val MarketClosed = com.example.domain.model.MarketStatus.CLOSED
        val symbols = listOf("GC=F", "SI=F", "CL=F", "BZ=F", "NG=F")

        // Nov 18, 2026 is Wednesday
        val wedOpenMorning = nyEpochSec(2026, java.util.Calendar.NOVEMBER, 18, 10, 30)
        val wedMaintenanceStart = nyEpochSec(2026, java.util.Calendar.NOVEMBER, 18, 17, 0)
        val wedMaintenanceMid = nyEpochSec(2026, java.util.Calendar.NOVEMBER, 18, 17, 45)
        val wedReopen = nyEpochSec(2026, java.util.Calendar.NOVEMBER, 18, 18, 0)

        // Nov 20, 2026 is Friday; Nov 21 Saturday; Nov 22 Sunday
        val friBeforeClose = nyEpochSec(2026, java.util.Calendar.NOVEMBER, 20, 16, 59)
        val friAfterClose = nyEpochSec(2026, java.util.Calendar.NOVEMBER, 20, 17, 0)
        val satMidday = nyEpochSec(2026, java.util.Calendar.NOVEMBER, 21, 12, 0)
        val sunAfternoonClosed = nyEpochSec(2026, java.util.Calendar.NOVEMBER, 22, 15, 0)
        val sunBeforeOpen = nyEpochSec(2026, java.util.Calendar.NOVEMBER, 22, 17, 59)
        val sunOpenEvening = nyEpochSec(2026, java.util.Calendar.NOVEMBER, 22, 18, 0)

        for (sym in symbols) {
            // Even if Yahoo reports REGULAR 24h window, maintenance & Sunday/weekend close must be CLOSED
            val misleadingYahooState = "REGULAR|0|9999999999"

            assertEquals("$sym should be OPEN Wed 10:30 ET", MarketOpen,
                com.example.data.repository.PortfolioRepository.resolveMarketStatus(misleadingYahooState, wedOpenMorning, sym))
            assertEquals("$sym should be CLOSED Wed 17:00 ET (maintenance)", MarketClosed,
                com.example.data.repository.PortfolioRepository.resolveMarketStatus(misleadingYahooState, wedMaintenanceStart, sym))
            assertEquals("$sym should be CLOSED Wed 17:45 ET (maintenance)", MarketClosed,
                com.example.data.repository.PortfolioRepository.resolveMarketStatus(misleadingYahooState, wedMaintenanceMid, sym))
            assertEquals("$sym should be OPEN Wed 18:00 ET (reopen)", MarketOpen,
                com.example.data.repository.PortfolioRepository.resolveMarketStatus(misleadingYahooState, wedReopen, sym))

            assertEquals("$sym should be OPEN Fri 16:59 ET", MarketOpen,
                com.example.data.repository.PortfolioRepository.resolveMarketStatus(misleadingYahooState, friBeforeClose, sym))
            assertEquals("$sym should be CLOSED Fri 17:00 ET", MarketClosed,
                com.example.data.repository.PortfolioRepository.resolveMarketStatus(misleadingYahooState, friAfterClose, sym))
            assertEquals("$sym should be CLOSED Saturday", MarketClosed,
                com.example.data.repository.PortfolioRepository.resolveMarketStatus(misleadingYahooState, satMidday, sym))
            assertEquals("$sym should be CLOSED Sunday 15:00 ET", MarketClosed,
                com.example.data.repository.PortfolioRepository.resolveMarketStatus(misleadingYahooState, sunAfternoonClosed, sym))
            assertEquals("$sym should be CLOSED Sunday 17:59 ET", MarketClosed,
                com.example.data.repository.PortfolioRepository.resolveMarketStatus(misleadingYahooState, sunBeforeOpen, sym))
            assertEquals("$sym should be OPEN Sunday 18:00 ET", MarketOpen,
                com.example.data.repository.PortfolioRepository.resolveMarketStatus(misleadingYahooState, sunOpenEvening, sym))

            // Stale Sunday CLOSED cache should NOT keep Monday/Wednesday open hours stuck CLOSED
            assertEquals("$sym stale Sunday CLOSED flips OPEN Wed", MarketOpen,
                com.example.data.repository.PortfolioRepository.resolveMarketStatus("CLOSED", wedOpenMorning, sym, fetchedAtSec = sunAfternoonClosed))

            // Fresh holiday CLOSED fetched during normally open hours MUST stay CLOSED
            assertEquals("$sym fresh holiday CLOSED stays CLOSED", MarketClosed,
                com.example.data.repository.PortfolioRepository.resolveMarketStatus("CLOSED", wedOpenMorning, sym, fetchedAtSec = wedOpenMorning - 60L))
        }
    }

    @Test
    fun backup_codec_round_trip_and_validation() {
        val payload = com.example.data.backup.ValorisBackupPayload(
            exportedAtMillis = 1_795_000_000_000L,
            holdings = listOf(
                com.example.data.backup.BackupHolding(
                    id = 42L,
                    symbol = "GC=F",
                    name = "Gold Futures",
                    assetClass = "COMMODITY",
                    quantity = 15.5,
                    unitType = "GRAMS",
                    averageCostPerUnit = 85.0,
                    nativeCurrency = "USD",
                    accountLabel = "Vault",
                    buyDateMillis = 1_790_000_000_000L,
                    updatedAt = 1_795_000_000_000L
                )
            ),
            transactions = listOf(
                com.example.data.backup.BackupTransaction(
                    id = 7L,
                    holdingId = 42L,
                    symbol = "GC=F",
                    assetName = "Gold Futures",
                    assetClass = "COMMODITY",
                    type = "BUY",
                    quantity = 15.5,
                    unitType = "GRAMS",
                    unitPriceNative = 85.0,
                    totalAmountNative = 1317.5,
                    nativeCurrency = "USD",
                    timestampMillis = 1_790_000_000_000L,
                    note = "Buy lot"
                )
            ),
            snapshots = listOf(
                com.example.data.backup.BackupSnapshot(
                    timestamp = 1_794_999_000_000L,
                    totalValueUsd = 1400.0
                )
            ),
            preferences = com.example.data.backup.BackupPreferences(
                displayCurrency = "IDR",
                isBalanceHidden = true,
                refreshIntervalMinutes = 15,
                themeMode = "DARK",
                useCompactNumbers = true,
                showPnlOnCards = true,
                showExtendedHoursPrice = true,
                defaultChartTimeframe = "YEAR"
            )
        )

        val json = com.example.data.backup.PortfolioBackupCodec.encode(payload)
        val decoded = com.example.data.backup.PortfolioBackupCodec.decodeAndValidate(json).getOrThrow()

        assertEquals(1, decoded.holdings.size)
        assertEquals(42L, decoded.holdings.first().id)
        assertEquals("GC=F", decoded.holdings.first().symbol)
        assertEquals(15.5, decoded.holdings.first().quantity, 0.0001)
        assertEquals(1, decoded.transactions.size)
        assertEquals(42L, decoded.transactions.first().holdingId)
        assertEquals(1, decoded.snapshots.size)
        assertEquals("DARK", decoded.preferences?.themeMode)
        assertEquals("YEAR", decoded.preferences?.defaultChartTimeframe)

        // Reject invalid payloads cleanly
        assertTrue(com.example.data.backup.PortfolioBackupCodec.decodeAndValidate("").isFailure)
        assertTrue(com.example.data.backup.PortfolioBackupCodec.decodeAndValidate("{}").isFailure)
        assertTrue(
            com.example.data.backup.PortfolioBackupCodec.decodeAndValidate(
                """{"format":"other_app","schemaVersion":1,"exportedAtMillis":123}"""
            ).isFailure
        )
    }

    @Test
    fun commodity_grams_manual_offline_and_live_valuation_is_unit_consistent() {
        val rates = mapOf("USD" to 1.0, "IDR" to 16000.0)
        val now = 1_795_000_000_000L

        // 1. Manual override per gram: 10g @ Rp 1,500,000/g => Rp 15,000,000 (not divided by 31.1035)
        val manualGold = com.example.domain.model.Holding(
            id = 1L,
            symbol = "GC=F",
            name = "Antam Gold",
            assetClass = com.example.domain.model.AssetClass.COMMODITY,
            quantity = 10.0,
            unitType = UnitType.GRAMS,
            averageCostPerUnit = 1_200_000.0,
            nativeCurrency = "IDR",
            manualPriceOverride = 1_500_000.0,
            updatedAt = now
        )
        val valuedManual = com.example.data.repository.PortfolioRepository.evaluateValuedHolding(
            holding = manualGold,
            cachedQuote = com.example.data.local.CachedQuoteEntity(
                symbol = "GC=F",
                priceNative = 2650.0,
                previousCloseNative = 2600.0,
                currency = "USD",
                dayChangePercent = 1.92,
                marketState = "REGULAR",
                displayName = "Gold Futures",
                sparklineCsv = "",
                fetchedAt = now
            ),
            latestFxAt = now,
            displayCurrency = com.example.domain.model.SupportedCurrency.IDR,
            showExtendedHoursPrice = false,
            ratesPerUsd = rates,
            nowMillis = now
        )
        assertEquals(1_500_000.0, valuedManual.unitQuoteNative, 0.01)
        assertEquals(15_000_000.0, valuedManual.totalValueDisplay, 0.01)
        assertEquals(3_000_000.0, valuedManual.unrealizedGainDisplay!!, 0.01)
        assertEquals(25.0, valuedManual.unrealizedGainPercent!!, 0.01)

        // 2. Offline fallback with no cached quote uses avgCost per gram directly
        val offlineGold = manualGold.copy(manualPriceOverride = null)
        val valuedOffline = com.example.data.repository.PortfolioRepository.evaluateValuedHolding(
            holding = offlineGold,
            cachedQuote = null,
            latestFxAt = now,
            displayCurrency = com.example.domain.model.SupportedCurrency.IDR,
            showExtendedHoursPrice = false,
            ratesPerUsd = rates,
            nowMillis = now
        )
        assertEquals(1_200_000.0, valuedOffline.unitQuoteNative, 0.01)
        assertEquals(12_000_000.0, valuedOffline.totalValueDisplay, 0.01)
        assertEquals(0.0, valuedOffline.unrealizedGainDisplay!!, 0.01)

        // 3. Live Yahoo Futures quote ($3,110.34768 / troy oz == $100/g) -> 10g = $1,000 USD
        val liveGold = com.example.domain.model.Holding(
            id = 2L,
            symbol = "GC=F",
            name = "Gold Futures",
            assetClass = com.example.domain.model.AssetClass.COMMODITY,
            quantity = 10.0,
            unitType = UnitType.GRAMS,
            averageCostPerUnit = 90.0,
            nativeCurrency = "USD",
            manualPriceOverride = null,
            updatedAt = now
        )
        val valuedLive = com.example.data.repository.PortfolioRepository.evaluateValuedHolding(
            holding = liveGold,
            cachedQuote = com.example.data.local.CachedQuoteEntity(
                symbol = "GC=F",
                priceNative = 100.0 * CurrencyFormatter.GRAMS_PER_TROY_OZ,
                previousCloseNative = 95.0 * CurrencyFormatter.GRAMS_PER_TROY_OZ,
                currency = "USD",
                dayChangePercent = 5.26,
                marketState = "REGULAR",
                displayName = "Gold Futures",
                sparklineCsv = "",
                fetchedAt = now
            ),
            latestFxAt = now,
            displayCurrency = com.example.domain.model.SupportedCurrency.USD,
            showExtendedHoursPrice = false,
            ratesPerUsd = rates,
            nowMillis = now
        )
        assertEquals(100.0, valuedLive.unitQuoteNative, 0.001)
        assertEquals(1000.0, valuedLive.totalValueDisplay, 0.01)
        assertEquals(100.0, valuedLive.unrealizedGainDisplay!!, 0.01)
    }

    @Test
    fun pre_market_does_not_return_stale_post_market_price() {
        val result = com.example.data.repository.PortfolioRepository.extractExtendedHoursPrice(
            marketState = "PRE",
            regularMarketPrice = 100.0,
            regularMarketTimeSec = 1000L,
            regularStartSec = 2000L,
            regularEndSec = 3000L,
            preMarketPrice = null,
            postMarketPrice = 105.0,
            timestamps = emptyList(),
            closes = emptyList()
        )
        assertEquals(null, result)
    }

    @Test
    fun date_picker_utc_conversion_preserves_calendar_day_across_timezones() {
        val utcTz = java.util.TimeZone.getTimeZone("UTC")
        val pstTz = java.util.TimeZone.getTimeZone("America/Los_Angeles")
        val wibTz = java.util.TimeZone.getTimeZone("Asia/Jakarta")

        // User taps Nov 23, 2026 in Material 3 DatePicker -> selectedDateMillis is 2026-11-23T00:00:00Z
        val utcCal = java.util.Calendar.getInstance(utcTz).apply {
            clear()
            set(2026, java.util.Calendar.NOVEMBER, 23, 0, 0, 0)
        }
        val selectedUtcMidnight = utcCal.timeInMillis

        val localNoonPst = CurrencyFormatter.utcDatePickerMillisToLocalNoon(selectedUtcMidnight, pstTz)
        val pstCal = java.util.Calendar.getInstance(pstTz).apply { timeInMillis = localNoonPst }
        assertEquals(2026, pstCal.get(java.util.Calendar.YEAR))
        assertEquals(java.util.Calendar.NOVEMBER, pstCal.get(java.util.Calendar.MONTH))
        assertEquals(23, pstCal.get(java.util.Calendar.DAY_OF_MONTH))

        val roundTripUtc = CurrencyFormatter.localMillisToUtcDatePickerMillis(localNoonPst, pstTz)
        assertEquals(selectedUtcMidnight, roundTripUtc)

        val localNoonWib = CurrencyFormatter.utcDatePickerMillisToLocalNoon(selectedUtcMidnight, wibTz)
        val wibCal = java.util.Calendar.getInstance(wibTz).apply { timeInMillis = localNoonWib }
        assertEquals(23, wibCal.get(java.util.Calendar.DAY_OF_MONTH))

        // Tiny prices preserve precision instead of formatting to "0"
        assertEquals("0.00002", CurrencyFormatter.formatPlainInputNumber(0.00002))
        assertEquals("85.25", CurrencyFormatter.formatPlainInputNumber(85.2500))
    }

    @Test
    fun widget_snapshot_supports_asset_value_total_return_range_and_privacy() {
        val now = 1_700_000_000_000L
        val holding = com.example.domain.model.ValuedHolding(
            holding = com.example.domain.model.Holding(
                id = 1L,
                symbol = "VOO",
                name = "Vanguard S&P 500 ETF",
                assetClass = com.example.domain.model.AssetClass.ETF,
                quantity = 40.0,
                unitType = UnitType.SHARES,
                nativeCurrency = "USD",
                updatedAt = now
            ),
            unitQuoteNative = 400.0,
            previousCloseNative = 395.0,
            dayChangePercent = 1.27,
            totalValueNative = 16_000.0,
            totalValueDisplay = 16_000.0,
            totalValueUsd = 16_000.0,
            dayChangeDisplay = 200.0,
            unrealizedGainDisplay = 1_800.0,
            unrealizedGainPercent = 12.68,
            portfolioWeightPercent = 100.0,
            fxRateToDisplay = 1.0,
            quoteStatus = com.example.domain.model.QuoteStatus.LIVE,
            quoteUpdatedAt = now,
            sparklinePoints = emptyList()
        )
        val baseDashboard = com.example.domain.model.PortfolioDashboardData(
            displayCurrency = com.example.domain.model.SupportedCurrency.USD,
            isBalanceHidden = false,
            refreshInterval = com.example.domain.model.RefreshInterval.FIVE_MINUTES,
            themeMode = com.example.domain.model.ThemeMode.SYSTEM,
            useCompactNumbers = true,
            showPnlOnCards = true,
            widgetDisplayMetric = com.example.domain.model.WidgetDisplayMetric.ASSET_VALUE,
            widgetChartTimeframe = com.example.domain.model.ChartTimeframe.YEAR,
            totalValueDisplay = 16_000.0,
            dayChangeDisplay = 200.0,
            dayChangePercent = 1.27,
            totalUnrealizedGainDisplay = 1_800.0,
            totalUnrealizedGainPercent = 12.68,
            valuedHoldings = listOf(holding),
            allocationSlices = emptyList(),
            fxRatesList = emptyList(),
            usdToDisplayRate = 1.0,
            trendValuesDisplay = listOf(14_000.0, 15_200.0, 16_000.0),
            lastSyncEpoch = now - 120_000L,
            fxSourceLabel = "Open ExchangeRate-API",
            hasStaleQuotes = false
        )
        val movement = com.example.domain.model.PortfolioMovementData(
            timeframe = com.example.domain.model.ChartTimeframe.YEAR,
            totalTimeline = listOf(
                com.example.domain.model.HistoricalPoint(1000L, 12_000.0),
                com.example.domain.model.HistoricalPoint(2000L, 14_500.0),
                com.example.domain.model.HistoricalPoint(3000L, 16_000.0)
            ),
            pnlTimeline = listOf(
                com.example.domain.model.PnlTimelinePoint(1000L, -200.0, -200.0, 0.0),
                com.example.domain.model.PnlTimelinePoint(2000L, 900.0, 900.0, 0.0),
                com.example.domain.model.PnlTimelinePoint(3000L, 2_150.0, 1_800.0, 350.0)
            ),
            periodChangeDisplay = 4_000.0,
            periodChangePercent = 33.33,
            latestTotalReturnDisplay = 2_150.0,
            latestReturnPercent = 15.52,
            isLoading = false
        )

        // 1. ASSET_VALUE mode with 1Y timeline
        val assetValueSnap = com.example.widget.buildWidgetSnapshot(
            dashboard = baseDashboard,
            movement = movement,
            nowMillis = now
        )
        assertEquals(false, assetValueSnap.isEmpty)
        assertEquals("TOTAL ASSET VALUE", assetValueSnap.eyebrowLabel)
        assertEquals("1Y", assetValueSnap.rangeLabel)
        assertEquals("$16.0K", assetValueSnap.formattedPrimaryValue)
        assertEquals("+$4,000.00 (+33.33%) · 1Y", assetValueSnap.formattedSecondaryDelta)
        assertEquals(listOf(12_000.0, 14_500.0, 16_000.0), assetValueSnap.chartPoints)
        assertTrue(assetValueSnap.isPositive)

        // 2. TOTAL_RETURN mode with negative & positive PnL chart
        val totalReturnSnap = com.example.widget.buildWidgetSnapshot(
            dashboard = baseDashboard.copy(
                widgetDisplayMetric = com.example.domain.model.WidgetDisplayMetric.TOTAL_RETURN,
                widgetChartTimeframe = com.example.domain.model.ChartTimeframe.MONTH
            ),
            movement = movement.copy(timeframe = com.example.domain.model.ChartTimeframe.MONTH),
            nowMillis = now
        )
        assertEquals("TOTAL RETURN", totalReturnSnap.eyebrowLabel)
        assertEquals("1M", totalReturnSnap.rangeLabel)
        assertEquals("+$2,150.00", totalReturnSnap.formattedPrimaryValue)
        assertEquals("+15.52% overall · 1M", totalReturnSnap.formattedSecondaryDelta)
        assertEquals(listOf(-200.0, 900.0, 2_150.0), totalReturnSnap.chartPoints)
        assertTrue(totalReturnSnap.isPositive)

        // 3. Privacy Mode masks figures while preserving range badge
        val hiddenSnap = com.example.widget.buildWidgetSnapshot(
            dashboard = baseDashboard.copy(isBalanceHidden = true),
            movement = movement,
            nowMillis = now
        )
        assertTrue(hiddenSnap.isBalanceHidden)
        assertEquals("••••••", hiddenSnap.formattedPrimaryValue)
        assertTrue(hiddenSnap.formattedSecondaryDelta.startsWith("Hidden"))

        // 4. Empty state
        val emptySnap = com.example.widget.buildWidgetSnapshot(
            dashboard = baseDashboard.copy(valuedHoldings = emptyList(), totalValueDisplay = 0.0),
            movement = null,
            nowMillis = now
        )
        assertTrue(emptySnap.isEmpty)
    }
}
