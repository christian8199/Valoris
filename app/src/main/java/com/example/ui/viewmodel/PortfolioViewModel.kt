package com.example.ui.viewmodel

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.Immutable
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.backup.BackupPreview
import com.example.data.local.AppLockConfig
import com.example.data.repository.PortfolioRepository
import com.example.widget.ValorisWidgetUpdater
import com.example.domain.model.AssetClass
import com.example.domain.model.AssetPreset
import com.example.domain.model.ChartTimeframe
import com.example.domain.model.HistoricalPoint
import com.example.domain.model.HistoryFilterType
import com.example.domain.model.Holding
import com.example.domain.model.MovementGraphMode
import com.example.domain.model.PortfolioDashboardData
import com.example.domain.model.PortfolioHistorySummary
import com.example.domain.model.PortfolioMovementData
import com.example.domain.model.RefreshInterval
import com.example.domain.model.SearchSuggestion
import com.example.domain.model.SortOption
import com.example.domain.model.SupportedCurrency
import com.example.domain.model.ThemeMode
import com.example.domain.model.ValuedHolding
import com.example.domain.model.WidgetDisplayMetric

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

@Immutable
sealed interface PortfolioContentState {
    data object Loading : PortfolioContentState
    @Immutable
    data class Ready(
        val dashboard: PortfolioDashboardData,
        val filteredHoldings: List<ValuedHolding>,
        val selectedFilter: AssetClass?,
        val sortOption: SortOption,
        val holdingsSearchQuery: String
    ) : PortfolioContentState
}

@Immutable
data class HoldingDetailState(
    val holdingId: Long,
    val timeframe: ChartTimeframe = ChartTimeframe.MONTH,
    val chartPoints: List<HistoricalPoint> = emptyList(),
    val isLoadingChart: Boolean = false
)

class PortfolioViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = PortfolioRepository.getInstance(application)

    private val _selectedFilter = MutableStateFlow<AssetClass?>(null)
    private val _sortOption = MutableStateFlow(SortOption.VALUE_DESC)
    private val _holdingsSearchQuery = MutableStateFlow("")
    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _searchSuggestions = MutableStateFlow(
        com.example.util.CuratedAssets.presets
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
    )
    val searchSuggestions: StateFlow<List<SearchSuggestion>> = _searchSuggestions.asStateFlow()

    private val _isSearchingSymbol = MutableStateFlow(false)
    val isSearchingSymbol: StateFlow<Boolean> = _isSearchingSymbol.asStateFlow()

    private val _detailState = MutableStateFlow<HoldingDetailState?>(null)
    val detailState: StateFlow<HoldingDetailState?> = _detailState.asStateFlow()

    private val _movementState = MutableStateFlow(PortfolioMovementData(isLoading = true))
    val movementState: StateFlow<PortfolioMovementData> = _movementState.asStateFlow()

    private val _historyFilter = MutableStateFlow(HistoryFilterType.ALL)
    val historyFilter: StateFlow<HistoryFilterType> = _historyFilter.asStateFlow()

    private val _historySearchQuery = MutableStateFlow("")
    val historySearchQuery: StateFlow<String> = _historySearchQuery.asStateFlow()

    val historySummary: StateFlow<PortfolioHistorySummary> = repository.historySummaryFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = PortfolioHistorySummary()
    )

    val appLockConfig: StateFlow<AppLockConfig> = repository.appLockConfigFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = AppLockConfig()
    )

    private val _isAppUnlocked = MutableStateFlow(false)
    val isAppUnlocked: StateFlow<Boolean> = _isAppUnlocked.asStateFlow()

    private val _isForegroundActive = MutableStateFlow(true)

    private val _pinError = MutableStateFlow<String?>(null)
    val pinError: StateFlow<String?> = _pinError.asStateFlow()

    private val _failedPinAttempts = MutableStateFlow(0)
    val failedPinAttempts: StateFlow<Int> = _failedPinAttempts.asStateFlow()

    private val _pinLockoutRemainingSeconds = MutableStateFlow(0)
    val pinLockoutRemainingSeconds: StateFlow<Int> = _pinLockoutRemainingSeconds.asStateFlow()

    private val _snackbarEvents = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val snackbarEvents: SharedFlow<String> = _snackbarEvents.asSharedFlow()

    private val _isBackupBusy = MutableStateFlow(false)
    val isBackupBusy: StateFlow<Boolean> = _isBackupBusy.asStateFlow()

    private val _pendingRestorePreview = MutableStateFlow<BackupPreview?>(null)
    val pendingRestorePreview: StateFlow<BackupPreview?> = _pendingRestorePreview.asStateFlow()

    private var symbolSearchJob: Job? = null
    private var movementJob: Job? = null
    private var detailChartJob: Job? = null
    private var pinLockoutJob: Job? = null
    private var hasUserOverriddenMovementTimeframe = false

    val uiState: StateFlow<PortfolioContentState> = combine(
        repository.dashboardFlow,
        _selectedFilter,
        _sortOption,
        _holdingsSearchQuery
    ) { dashboard, filter, sort, query ->
        val filtered = dashboard.valuedHoldings
            .filter { item ->
                val matchesClass = filter == null || item.holding.assetClass == filter
                val q = query.trim()
                val matchesQuery = q.isEmpty() ||
                    item.holding.symbol.contains(q, ignoreCase = true) ||
                    item.holding.name.contains(q, ignoreCase = true) ||
                    item.holding.accountLabel.contains(q, ignoreCase = true)
                matchesClass && matchesQuery
            }
            .let { list ->
                when (sort) {
                    SortOption.VALUE_DESC -> list.sortedByDescending { it.totalValueDisplay }
                    SortOption.CHANGE_DESC -> list.sortedByDescending { it.dayChangePercent }
                    SortOption.NAME_ASC -> list.sortedBy { it.holding.symbol }
                }
            }

        PortfolioContentState.Ready(
            dashboard = dashboard,
            filteredHoldings = filtered,
            selectedFilter = filter,
            sortOption = sort,
            holdingsSearchQuery = query
        )
    }.flowOn(Dispatchers.Default).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = PortfolioContentState.Loading
    )

    init {
        viewModelScope.launch { repository.ensureInitialTransactionsSeeded() }
        // Stagger silent background sync so the cached portfolio renders instantly on frame 1 without I/O contention
        viewModelScope.launch {
            delay(500L)
            refreshMarketData(silent = true)
        }
        startAutoRefreshLoop()
        observePortfolioChangesForMovement()
    }

    private data class HoldingReloadSig(
        val id: Long,
        val quantity: Double,
        val averageCostPerUnit: Double?,
        val buyDateMillis: Long?
    )

    private data class TxReloadSig(
        val id: Long,
        val type: com.example.domain.model.TransactionType,
        val quantity: Double,
        val unitPriceNative: Double,
        val timestampMillis: Long
    )

    private data class MovementReloadKey(
        val currency: SupportedCurrency,
        val holdingSignature: List<HoldingReloadSig>,
        val txSignature: List<TxReloadSig>,
        val hasData: Boolean,
        val includeExtendedHours: Boolean,
        val defaultTimeframe: ChartTimeframe
    )

    @OptIn(FlowPreview::class)
    private fun observePortfolioChangesForMovement() {
        viewModelScope.launch {
            combine(
                repository.dashboardFlow,
                repository.transactionsFlow
            ) { dash, txs ->
                MovementReloadKey(
                    currency = dash.displayCurrency,
                    holdingSignature = dash.valuedHoldings.map {
                        HoldingReloadSig(
                            id = it.holding.id,
                            quantity = it.holding.quantity,
                            averageCostPerUnit = it.holding.averageCostPerUnit,
                            buyDateMillis = it.holding.buyDateMillis
                        )
                    },
                    txSignature = txs.map {
                        TxReloadSig(
                            id = it.id,
                            type = it.type,
                            quantity = it.quantity,
                            unitPriceNative = it.unitPriceNative,
                            timestampMillis = it.timestampMillis
                        )
                    },
                    hasData = dash.valuedHoldings.isNotEmpty() || txs.isNotEmpty(),
                    includeExtendedHours = dash.showExtendedHoursPrice,
                    defaultTimeframe = dash.defaultChartTimeframe
                )
            }
                .distinctUntilChanged()
                .debounce(180L)
                .flowOn(Dispatchers.Default)
                .collectLatest { key ->
                    val current = _movementState.value
                    val targetTimeframe = if (hasUserOverriddenMovementTimeframe) {
                        current.timeframe
                    } else {
                        key.defaultTimeframe
                    }
                    if (key.hasData) {
                        loadMovement(
                            timeframe = targetTimeframe,
                            graphMode = current.graphMode,
                            respectBuyDates = current.respectBuyDates,
                            displayCurrency = key.currency,
                            includeExtendedHours = key.includeExtendedHours
                        )
                    } else {
                        _movementState.value = PortfolioMovementData(
                            timeframe = targetTimeframe,
                            graphMode = current.graphMode,
                            respectBuyDates = current.respectBuyDates,
                            hasAnyBuyDates = false,
                            isLoading = false
                        )
                    }
                }
        }
    }



    fun selectMovementTimeframe(timeframe: ChartTimeframe) {
        hasUserOverriddenMovementTimeframe = true
        val current = _movementState.value
        val dash = (uiState.value as? PortfolioContentState.Ready)?.dashboard
        loadMovement(
            timeframe = timeframe,
            graphMode = current.graphMode,
            respectBuyDates = current.respectBuyDates,
            displayCurrency = dash?.displayCurrency ?: SupportedCurrency.IDR,
            includeExtendedHours = dash?.showExtendedHoursPrice ?: false
        )
    }

    fun selectMovementGraphMode(mode: MovementGraphMode) {
        _movementState.value = _movementState.value.copy(graphMode = mode)
    }

    fun toggleRespectBuyDates() {
        val current = _movementState.value
        val dash = (uiState.value as? PortfolioContentState.Ready)?.dashboard
        loadMovement(
            timeframe = current.timeframe,
            graphMode = current.graphMode,
            respectBuyDates = !current.respectBuyDates,
            displayCurrency = dash?.displayCurrency ?: SupportedCurrency.IDR,
            includeExtendedHours = dash?.showExtendedHoursPrice ?: false
        )
    }

    fun refreshMovement() {
        val current = _movementState.value
        val dash = (uiState.value as? PortfolioContentState.Ready)?.dashboard
        loadMovement(
            timeframe = current.timeframe,
            graphMode = current.graphMode,
            respectBuyDates = current.respectBuyDates,
            displayCurrency = dash?.displayCurrency ?: SupportedCurrency.IDR,
            includeExtendedHours = dash?.showExtendedHoursPrice ?: false,
            forceRefresh = true
        )
    }

    private fun loadMovement(
        timeframe: ChartTimeframe,
        graphMode: MovementGraphMode,
        respectBuyDates: Boolean,
        displayCurrency: SupportedCurrency,
        includeExtendedHours: Boolean,
        forceRefresh: Boolean = false
    ) {
        movementJob?.cancel()
        movementJob = viewModelScope.launch {
            _movementState.value = _movementState.value.copy(
                timeframe = timeframe,
                graphMode = graphMode,
                respectBuyDates = respectBuyDates,
                isLoading = true,
                errorMessage = null
            )
            val data = repository.fetchPortfolioMovement(
                timeframe = timeframe,
                graphMode = graphMode,
                respectBuyDates = respectBuyDates,
                displayCurrency = displayCurrency,
                includeExtendedHours = includeExtendedHours,
                forceRefresh = forceRefresh
            )
            _movementState.value = data
        }
    }

    private fun startAutoRefreshLoop() {
        viewModelScope.launch {
            combine(
                repository.dashboardFlow.map { it.refreshInterval }.distinctUntilChanged(),
                _isForegroundActive,
                appLockConfig,
                _isAppUnlocked
            ) { interval, isForeground, lockConfig, isUnlocked ->
                val isAllowed = isForeground && lockConfig.isLoaded && (!lockConfig.isEnabled || isUnlocked)
                if (isAllowed && interval != RefreshInterval.MANUAL && interval.millis > 0L) {
                    interval.millis
                } else {
                    0L
                }
            }
                .distinctUntilChanged()
                .collectLatest { intervalMillis ->
                    if (intervalMillis > 0L) {
                        while (true) {
                            delay(intervalMillis)
                            refreshMarketData(silent = true)
                        }
                    }
                }
        }
    }

    fun refreshMarketData(silent: Boolean = false) {
        if (!silent && _isRefreshing.value) return
        viewModelScope.launch {
            if (!silent) {
                _isRefreshing.value = true
            }
            try {
                val result = repository.refreshAllMarketData()
                syncWidgets()
                if (!silent) {
                    val msg = result.errorMessage ?: "Live rates & quotes updated"
                    _snackbarEvents.emit(msg)
                } else if (result.errorMessage != null && result.quotesUpdatedCount == 0 && !result.fxUpdated) {
                    // Only surface silent error if network completely unreachable and user has holdings
                    val current = uiState.value
                    if (current is PortfolioContentState.Ready && current.dashboard.valuedHoldings.isNotEmpty()) {
                        _snackbarEvents.emit(result.errorMessage)
                    }
                }
            } finally {
                if (!silent) {
                    _isRefreshing.value = false
                }
            }
        }
    }

    fun setFilter(assetClass: AssetClass?) {
        _selectedFilter.value = assetClass
    }

    fun setSortOption(option: SortOption) {
        _sortOption.value = option
    }

    fun setHoldingsSearchQuery(query: String) {
        _holdingsSearchQuery.value = query
    }

    fun setDisplayCurrency(currency: SupportedCurrency) {
        viewModelScope.launch {
            repository.setDisplayCurrency(currency)
            syncWidgets()
        }
    }

    fun toggleBalanceHidden() {
        viewModelScope.launch {
            repository.toggleBalanceHidden()
            syncWidgets(bumpDataRevision = false)
        }
    }

    fun setBalanceHidden(hidden: Boolean) {
        viewModelScope.launch {
            repository.setBalanceHidden(hidden)
            syncWidgets(bumpDataRevision = false)
        }
    }

    fun setRefreshInterval(interval: RefreshInterval) {
        viewModelScope.launch {
            repository.setRefreshInterval(interval)
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            repository.setThemeMode(mode)
        }
    }

    fun setUseCompactNumbers(compact: Boolean) {
        viewModelScope.launch {
            repository.setUseCompactNumbers(compact)
            syncWidgets(bumpDataRevision = false)
        }
    }

    fun setShowPnlOnCards(show: Boolean) {
        viewModelScope.launch {
            repository.setShowPnlOnCards(show)
        }
    }

    fun setShowExtendedHoursPrice(show: Boolean) {
        viewModelScope.launch {
            repository.setShowExtendedHoursPrice(show)
            syncWidgets()
        }
    }

    fun setDefaultChartTimeframe(timeframe: ChartTimeframe) {
        hasUserOverriddenMovementTimeframe = false
        viewModelScope.launch {
            repository.setDefaultChartTimeframe(timeframe)
        }
    }

    fun setWidgetDisplayMetric(metric: WidgetDisplayMetric) {
        viewModelScope.launch {
            repository.setWidgetDisplayMetric(metric)
            syncWidgets()
            ValorisWidgetUpdater.refreshGeneratedPreviewIfNeeded(getApplication())
        }
    }

    fun setWidgetChartTimeframe(timeframe: ChartTimeframe) {
        viewModelScope.launch {
            repository.setWidgetChartTimeframe(timeframe)
            syncWidgets()
            ValorisWidgetUpdater.refreshGeneratedPreviewIfNeeded(getApplication())
        }
    }

    fun requestPinPortfolioWidget() {
        viewModelScope.launch {
            val app = getApplication<Application>()
            val prompted = ValorisWidgetUpdater.requestPinWidget(app)
            if (prompted) {
                _snackbarEvents.emit("Launcher prompt sent — place Valoris Portfolio on your Home Screen")
            } else {
                _snackbarEvents.emit("Long-press your Home Screen → Widgets → Valoris Portfolio")
            }
        }
    }

    private suspend fun syncWidgets(bumpDataRevision: Boolean = true) {
        ValorisWidgetUpdater.updateAllWidgets(
            context = getApplication(),
            bumpDataRevision = bumpDataRevision
        )
    }

    fun unlockWithBiometric() {
        pinLockoutJob?.cancel()
        _pinLockoutRemainingSeconds.value = 0
        _pinError.value = null
        _failedPinAttempts.value = 0
        _isAppUnlocked.value = true
    }

    fun submitUnlockPin(pin: String) {
        if (pin.length != 4 || _pinLockoutRemainingSeconds.value > 0) return
        viewModelScope.launch {
            val valid = repository.verifyAppLockPin(pin)
            if (valid) {
                pinLockoutJob?.cancel()
                _pinLockoutRemainingSeconds.value = 0
                _pinError.value = null
                _failedPinAttempts.value = 0
                _isAppUnlocked.value = true
            } else {
                val attempts = _failedPinAttempts.value + 1
                _failedPinAttempts.value = attempts
                if (attempts >= 5) {
                    val cooldownSeconds = when {
                        attempts >= 8 -> 60
                        attempts >= 6 -> 30
                        else -> 15
                    }
                    startPinLockoutCountdown(attempts, cooldownSeconds)
                } else {
                    _pinError.value = "Incorrect PIN. Please try again."
                }
            }
        }
    }

    private fun startPinLockoutCountdown(attempts: Int, durationSeconds: Int) {
        pinLockoutJob?.cancel()
        pinLockoutJob = viewModelScope.launch {
            for (remaining in durationSeconds downTo 1) {
                _pinLockoutRemainingSeconds.value = remaining
                _pinError.value = "Too many incorrect PINs ($attempts). Wait ${remaining}s or use fingerprint."
                delay(1000L)
            }
            _pinLockoutRemainingSeconds.value = 0
            _pinError.value = "Incorrect PIN ($attempts attempts). Try again or use fingerprint."
        }
    }

    fun clearPinError() {
        if (_pinLockoutRemainingSeconds.value == 0) {
            _pinError.value = null
        }
    }

    fun setForegroundActive(active: Boolean) {
        _isForegroundActive.value = active
    }

    fun lockAppIfEnabled() {
        if (appLockConfig.value.isEnabled) {
            _isAppUnlocked.value = false
            if (_pinLockoutRemainingSeconds.value == 0) {
                _pinError.value = null
            }
        }
    }

    fun enableAppLock(pin: String, biometricEnabled: Boolean) {
        viewModelScope.launch {
            repository.enableAppLock(pin, biometricEnabled)
            pinLockoutJob?.cancel()
            _pinLockoutRemainingSeconds.value = 0
            _isAppUnlocked.value = true
            _pinError.value = null
            _failedPinAttempts.value = 0
            _snackbarEvents.emit("App Lock enabled")
        }
    }

    fun changeAppLockPin(currentPin: String, newPin: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            if (!repository.verifyAppLockPin(currentPin)) {
                onResult(false)
                return@launch
            }
            repository.updateAppLockPin(newPin)
            onResult(true)
            _snackbarEvents.emit("PIN updated")
        }
    }

    fun disableAppLock(currentPin: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            if (!repository.verifyAppLockPin(currentPin)) {
                onResult(false)
                return@launch
            }
            repository.disableAppLock()
            _isAppUnlocked.value = true
            onResult(true)
            _snackbarEvents.emit("App Lock disabled")
        }
    }

    fun setBiometricUnlockEnabled(enabled: Boolean) {
        viewModelScope.launch {
            repository.setBiometricUnlockEnabled(enabled)
        }
    }

    fun exportBackupToUri(uri: Uri) {
        if (_isBackupBusy.value) return
        viewModelScope.launch {
            _isBackupBusy.value = true
            try {
                val exported = repository.exportBackupJson()
                val app = getApplication<Application>()
                withContext(Dispatchers.IO) {
                    val out = app.contentResolver.openOutputStream(uri)
                        ?: throw IllegalStateException("Unable to open destination file for writing.")
                    out.bufferedWriter(Charsets.UTF_8).use { writer ->
                        writer.write(exported.json)
                    }
                }
                _snackbarEvents.emit(
                    "Backup saved (${exported.holdingsCount} assets, ${exported.transactionsCount} history entries)"
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _snackbarEvents.emit(e.message ?: "Failed to export backup file.")
            } finally {
                _isBackupBusy.value = false
            }
        }
    }

    fun prepareRestoreFromUri(uri: Uri) {
        if (_isBackupBusy.value) return
        viewModelScope.launch {
            _isBackupBusy.value = true
            try {
                val app = getApplication<Application>()
                val rawJson = withContext(Dispatchers.IO) {
                    val input = app.contentResolver.openInputStream(uri)
                        ?: throw IllegalStateException("Unable to open selected backup file.")
                    input.use { stream ->
                        val maxBytes = 10 * 1024 * 1024
                        val buffer = ByteArray(8192)
                        val out = ByteArrayOutputStream()
                        var totalRead = 0
                        while (true) {
                            val read = stream.read(buffer)
                            if (read < 0) break
                            totalRead += read
                            require(totalRead <= maxBytes) {
                                "Selected file is too large to be a Valoris backup."
                            }
                            out.write(buffer, 0, read)
                        }
                        out.toByteArray().toString(Charsets.UTF_8)
                    }
                }
                val parsed = repository.parseBackupPreview(rawJson)
                parsed.onSuccess { preview ->
                    _pendingRestorePreview.value = preview
                }.onFailure { err ->
                    _snackbarEvents.emit(err.message ?: "Selected file is not a valid Valoris backup.")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _snackbarEvents.emit(e.message ?: "Could not read selected backup file.")
            } finally {
                _isBackupBusy.value = false
            }
        }
    }

    fun dismissRestorePreview() {
        _pendingRestorePreview.value = null
    }

    fun confirmRestoreBackup() {
        val preview = _pendingRestorePreview.value ?: return
        if (_isBackupBusy.value) return
        viewModelScope.launch {
            _isBackupBusy.value = true
            try {
                val result = repository.restoreFromBackup(preview.payload)
                _pendingRestorePreview.value = null
                _detailState.value = null
                hasUserOverriddenMovementTimeframe = false
                syncWidgets()
                _snackbarEvents.emit(
                    "Restored ${result.holdingsCount} assets & ${result.transactionsCount} history entries"
                )
                refreshMarketData(silent = true)
            } catch (e: Exception) {
                _snackbarEvents.emit(e.message ?: "Failed to restore backup.")
            } finally {
                _isBackupBusy.value = false
            }
        }
    }

    fun searchSymbol(query: String) {
        symbolSearchJob?.cancel()
        symbolSearchJob = viewModelScope.launch {
            if (query.isNotBlank()) delay(280)
            _isSearchingSymbol.value = true
            try {
                _searchSuggestions.value = repository.searchSymbols(query)
            } finally {
                _isSearchingSymbol.value = false
            }
        }
    }

    fun saveHolding(holding: Holding) {
        viewModelScope.launch {
            try {
                repository.saveHolding(holding)
                syncWidgets()
                _snackbarEvents.emit("Saved ${holding.symbol.uppercase()}")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _snackbarEvents.emit(e.message ?: "Unable to save holding.")
            }
        }
    }

    fun addBuyToHolding(
        holdingId: Long,
        symbol: String,
        addedQuantity: Double,
        buyPricePerUnit: Double?,
        buyDateMillis: Long?
    ) {
        viewModelScope.launch {
            try {
                repository.addBuyToHolding(
                    holdingId = holdingId,
                    addedQuantity = addedQuantity,
                    buyPricePerUnit = buyPricePerUnit,
                    buyDateMillis = buyDateMillis
                )
                syncWidgets()
                _snackbarEvents.emit("Added new buy to $symbol")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _snackbarEvents.emit(e.message ?: "Unable to record buy lot.")
            }
        }
    }

    fun sellFromHolding(
        holdingId: Long,
        sellQuantity: Double,
        sellPricePerUnit: Double,
        depositProceedsToCash: Boolean,
        sellDateMillis: Long = System.currentTimeMillis()
    ) {
        viewModelScope.launch {
            try {
                val result = repository.sellFromHolding(
                    holdingId = holdingId,
                    sellQuantity = sellQuantity,
                    sellPricePerUnit = sellPricePerUnit,
                    depositProceedsToCash = depositProceedsToCash,
                    sellDateMillis = sellDateMillis
                ) ?: return@launch

                if (result.soldAll && _detailState.value?.holdingId == holdingId) {
                    detailChartJob?.cancel()
                    _detailState.value = null
                }
                syncWidgets()

                val qtyText = com.example.util.CurrencyFormatter.formatQuantity(result.soldQuantity, result.unitType)
                val realizedSuffix = result.realizedGainNative?.let { gain ->
                    " · Realized ${
                        com.example.util.CurrencyFormatter.formatAmount(
                            gain,
                            result.nativeCurrency,
                            showSign = true
                        )
                    }"
                } ?: ""
                val cashSuffix = if (result.depositedToCash) " -> Cash" else ""
                _snackbarEvents.emit("Sold $qtyText of ${result.symbol}$realizedSuffix$cashSuffix")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _snackbarEvents.emit(e.message ?: "Unable to record sale.")
            }
        }
    }

    fun setHistoryFilter(filter: HistoryFilterType) {
        _historyFilter.value = filter
    }

    fun setHistorySearchQuery(query: String) {
        _historySearchQuery.value = query
    }

    fun deleteTransaction(transactionId: Long, symbol: String) {
        viewModelScope.launch {
            try {
                repository.deleteTransaction(transactionId)
                syncWidgets()
                _snackbarEvents.emit("Deleted $symbol transaction and updated holding")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _snackbarEvents.emit(e.message ?: "Unable to delete transaction.")
            }
        }
    }

    fun quickAddPreset(preset: AssetPreset, quantity: Double) {
        viewModelScope.launch {
            try {
                repository.saveHolding(
                    Holding(
                        symbol = preset.symbol,
                        name = preset.name,
                        assetClass = preset.assetClass,
                        quantity = quantity,
                        unitType = preset.unitType,
                        nativeCurrency = preset.nativeCurrency,
                        accountLabel = if (preset.assetClass == AssetClass.CASH) "Primary Account" else "Portfolio"
                    )
                )
                syncWidgets()
                _snackbarEvents.emit("Added ${preset.name}")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _snackbarEvents.emit(e.message ?: "Unable to add preset asset.")
            }
        }
    }

    fun deleteHolding(holdingId: Long, label: String) {
        viewModelScope.launch {
            try {
                repository.deleteHolding(holdingId)
                if (_detailState.value?.holdingId == holdingId) {
                    detailChartJob?.cancel()
                    _detailState.value = null
                }
                syncWidgets()
                _snackbarEvents.emit("Removed $label")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _snackbarEvents.emit(e.message ?: "Unable to remove holding.")
            }
        }
    }

    fun openHoldingDetail(item: ValuedHolding) {
        val isMarketAsset = item.holding.assetClass != AssetClass.CASH && item.holding.manualPriceOverride == null
        val defaultTimeframe = (uiState.value as? PortfolioContentState.Ready)
            ?.dashboard?.defaultChartTimeframe ?: ChartTimeframe.MONTH
        detailChartJob?.cancel()
        _detailState.value = HoldingDetailState(
            holdingId = item.holding.id,
            timeframe = defaultTimeframe,
            chartPoints = item.sparklinePoints.map { HistoricalPoint(0L, it) },
            isLoadingChart = isMarketAsset
        )
        if (isMarketAsset) {
            loadDetailChart(
                holdingId = item.holding.id,
                symbol = item.holding.symbol,
                assetClass = item.holding.assetClass,
                unitType = item.holding.unitType,
                timeframe = defaultTimeframe
            )
        }
    }

    fun selectDetailTimeframe(item: ValuedHolding, timeframe: ChartTimeframe) {
        val current = _detailState.value ?: return
        _detailState.value = current.copy(timeframe = timeframe, isLoadingChart = true)
        loadDetailChart(
            holdingId = current.holdingId,
            symbol = item.holding.symbol,
            assetClass = item.holding.assetClass,
            unitType = item.holding.unitType,
            timeframe = timeframe
        )
    }

    fun closeHoldingDetail() {
        detailChartJob?.cancel()
        _detailState.value = null
    }

    private fun loadDetailChart(
        holdingId: Long,
        symbol: String,
        assetClass: AssetClass,
        unitType: com.example.domain.model.UnitType,
        timeframe: ChartTimeframe
    ) {
        detailChartJob?.cancel()
        detailChartJob = viewModelScope.launch {
            val includeExtended = (uiState.value as? PortfolioContentState.Ready)
                ?.dashboard?.showExtendedHoursPrice ?: false
            val rawPoints = repository.fetchSymbolHistoryWithTimestamps(
                symbol = symbol,
                timeframe = timeframe,
                includeExtendedHours = includeExtended
            )
            val scaledPoints = if (assetClass == AssetClass.COMMODITY && unitType == com.example.domain.model.UnitType.GRAMS) {
                rawPoints.map { pt ->
                    pt.copy(
                        value = com.example.util.CurrencyFormatter.unitQuotePerEnteredUnit(
                            assetClass = assetClass,
                            unitType = unitType,
                            marketQuoteNative = pt.value
                        )
                    )
                }
            } else {
                rawPoints
            }
            _detailState.value = _detailState.value?.let { state ->
                if (state.holdingId == holdingId && state.timeframe == timeframe) {
                    state.copy(chartPoints = scaledPoints, isLoadingChart = false)
                } else state
            }
        }
    }
}
