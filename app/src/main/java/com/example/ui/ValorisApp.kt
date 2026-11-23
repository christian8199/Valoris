package com.example.ui

import android.app.Activity
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CurrencyExchange
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.CurrencyExchange
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Timeline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.R
import com.example.domain.model.Holding
import com.example.domain.model.ThemeMode
import com.example.ui.screens.AddBuyLotSheet
import com.example.ui.screens.AddEditHoldingSheet
import com.example.ui.screens.FxRatesScreen
import com.example.ui.screens.HistoryScreen
import com.example.ui.screens.HoldingDetailSheet
import com.example.ui.screens.HoldingsScreen
import com.example.ui.screens.MovementScreen
import com.example.ui.screens.OverviewScreen
import com.example.ui.screens.PinLockScreen
import com.example.ui.screens.SellHoldingSheet
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.ValorisTheme
import com.example.ui.viewmodel.PortfolioContentState
import com.example.ui.viewmodel.PortfolioViewModel

private sealed class ValorisDestination(
    val route: String,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    data object Overview : ValorisDestination(
        route = "overview",
        labelRes = R.string.nav_overview,
        selectedIcon = Icons.Filled.AccountBalance,
        unselectedIcon = Icons.Outlined.AccountBalance
    )

    data object Holdings : ValorisDestination(
        route = "holdings",
        labelRes = R.string.nav_holdings,
        selectedIcon = Icons.AutoMirrored.Filled.FormatListBulleted,
        unselectedIcon = Icons.AutoMirrored.Filled.FormatListBulleted
    )

    data object Movement : ValorisDestination(
        route = "movement",
        labelRes = R.string.nav_movement,
        selectedIcon = Icons.Filled.Timeline,
        unselectedIcon = Icons.Outlined.Timeline
    )

    data object History : ValorisDestination(
        route = "history",
        labelRes = R.string.nav_history,
        selectedIcon = Icons.AutoMirrored.Filled.ReceiptLong,
        unselectedIcon = Icons.AutoMirrored.Outlined.ReceiptLong
    )

    data object Markets : ValorisDestination(
        route = "markets",
        labelRes = R.string.nav_markets,
        selectedIcon = Icons.Filled.CurrencyExchange,
        unselectedIcon = Icons.Outlined.CurrencyExchange
    )
}

private const val ROUTE_SETTINGS = "settings"

@Composable
fun ValorisApp(
    viewModel: PortfolioViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val appLockConfig by viewModel.appLockConfig.collectAsStateWithLifecycle()
    val isAppUnlocked by viewModel.isAppUnlocked.collectAsStateWithLifecycle()
    val pinError by viewModel.pinError.collectAsStateWithLifecycle()
    val pinLockoutRemainingSeconds by viewModel.pinLockoutRemainingSeconds.collectAsStateWithLifecycle()
    var isBiometricPromptActive by remember { mutableStateOf(false) }
    var isExternalPickerActive by rememberSaveable { mutableStateOf(false) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> viewModel.setForegroundActive(true)
                Lifecycle.Event.ON_STOP -> {
                    viewModel.setForegroundActive(false)
                    if (!isBiometricPromptActive && !isExternalPickerActive) {
                        viewModel.lockAppIfEnabled()
                    }
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val themeMode = (uiState as? PortfolioContentState.Ready)?.dashboard?.themeMode ?: ThemeMode.SYSTEM
    val useDarkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        LaunchedEffect(useDarkTheme, view) {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                val controller = WindowCompat.getInsetsController(window, view)
                controller.isAppearanceLightStatusBars = !useDarkTheme
                controller.isAppearanceLightNavigationBars = !useDarkTheme
            }
        }
    }

    ValorisTheme(darkTheme = useDarkTheme) {
        val isLocked = appLockConfig.isLoaded && appLockConfig.isEnabled && !isAppUnlocked
        val state = uiState
        Box(modifier = Modifier.fillMaxSize()) {
            if (!appLockConfig.isLoaded || state is PortfolioContentState.Loading) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
            } else {
                val readyState = state as PortfolioContentState.Ready
                val snackbarHostState = remember { SnackbarHostState() }
                val navController = rememberNavController()
        val navBackStackEntry by navController.currentBackStackEntryAsState()
        val currentDestination = navBackStackEntry?.destination
        val isSettingsRoute = currentDestination?.route == ROUTE_SETTINGS

        var showAddEditSheet by remember { mutableStateOf(false) }
        var editingHolding by remember { mutableStateOf<Holding?>(null) }
        var addBuyTargetHoldingId by remember { mutableStateOf<Long?>(null) }
        var sellTargetHoldingId by remember { mutableStateOf<Long?>(null) }

        LaunchedEffect(Unit) {
            viewModel.snackbarEvents.collect { message ->
                snackbarHostState.showSnackbar(message)
            }
        }

        val destinations = remember {
            listOf(
                ValorisDestination.Overview,
                ValorisDestination.Holdings,
                ValorisDestination.Movement,
                ValorisDestination.History,
                ValorisDestination.Markets
            )
        }

        val navigateTo: (ValorisDestination) -> Unit = remember(navController) {
            { dest ->
                navController.navigate(dest.route) {
                    popUpTo(navController.graph.findStartDestination().id) {
                        saveState = true
                    }
                    launchSingleTop = true
                    restoreState = true
                }
            }
        }
        val openSettings: () -> Unit = remember(navController) {
            {
                navController.navigate(ROUTE_SETTINGS) {
                    launchSingleTop = true
                }
            }
        }
        val openAddHolding: () -> Unit = remember {
            {
                editingHolding = null
                showAddEditSheet = true
            }
        }
        val refreshMarketExplicit: () -> Unit = remember(viewModel) {
            { viewModel.refreshMarketData(silent = false) }
        }

        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val useNavRail = maxWidth >= 600.dp

            Scaffold(
                modifier = Modifier.fillMaxSize(),
                containerColor = MaterialTheme.colorScheme.background,
                contentWindowInsets = WindowInsets.safeDrawing,
                snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
                floatingActionButton = {
                    if (!isSettingsRoute) {
                        FloatingActionButton(
                            onClick = {
                                editingHolding = null
                                showAddEditSheet = true
                            },
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.testTag("fab_add_holding")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Add,
                                contentDescription = stringResource(R.string.cd_add_holding)
                            )
                        }
                    }
                },
                bottomBar = {
                    if (!useNavRail && !isSettingsRoute) {
                        NavigationBar(
                            containerColor = MaterialTheme.colorScheme.surface,
                            tonalElevation = 2.dp
                        ) {
                            destinations.forEach { dest ->
                                val selected = currentDestination?.hierarchy?.any { it.route == dest.route } == true
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = { navigateTo(dest) },
                                    icon = {
                                        Icon(
                                            imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                            contentDescription = stringResource(dest.labelRes)
                                        )
                                    },
                                    label = { Text(stringResource(dest.labelRes)) },
                                    modifier = Modifier.testTag("nav_${dest.route}")
                                )
                            }
                        }
                    }
                }
            ) { innerPadding ->
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    if (useNavRail && !isSettingsRoute) {
                        NavigationRail(
                            modifier = Modifier
                                .fillMaxHeight()
                                .windowInsetsPadding(WindowInsets(0.dp)),
                            containerColor = MaterialTheme.colorScheme.surface
                        ) {
                            destinations.forEach { dest ->
                                val selected = currentDestination?.hierarchy?.any { it.route == dest.route } == true
                                NavigationRailItem(
                                    selected = selected,
                                    onClick = { navigateTo(dest) },
                                    icon = {
                                        Icon(
                                            imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                            contentDescription = stringResource(dest.labelRes)
                                        )
                                    },
                                    label = { Text(stringResource(dest.labelRes)) },
                                    modifier = Modifier.testTag("nav_rail_${dest.route}")
                                )
                            }
                            NavigationRailItem(
                                selected = isSettingsRoute,
                                onClick = {
                                    navController.navigate(ROUTE_SETTINGS) {
                                        launchSingleTop = true
                                    }
                                },
                                icon = {
                                    Icon(
                                        imageVector = Icons.Outlined.Settings,
                                        contentDescription = stringResource(R.string.nav_settings)
                                    )
                                },
                                label = { Text(stringResource(R.string.nav_settings)) },
                                modifier = Modifier.testTag("nav_rail_settings")
                            )
                        }
                    }

                    NavHost(
                        navController = navController,
                        startDestination = ValorisDestination.Overview.route,
                        enterTransition = { fadeIn(animationSpec = tween(140)) },
                        exitTransition = { fadeOut(animationSpec = tween(100)) },
                        popEnterTransition = { fadeIn(animationSpec = tween(140)) },
                        popExitTransition = { fadeOut(animationSpec = tween(100)) },
                        modifier = Modifier.fillMaxSize()
                    ) {
                                composable(ValorisDestination.Overview.route) {
                                    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
                                    val movementState by viewModel.movementState.collectAsStateWithLifecycle()
                                    val toHoldings = remember(navigateTo) { { navigateTo(ValorisDestination.Holdings) } }
                                    val toMovement = remember(navigateTo) { { navigateTo(ValorisDestination.Movement) } }
                                    OverviewScreen(
                                        dashboard = readyState.dashboard,
                                        movement = movementState,
                                        isRefreshing = isRefreshing,
                                        onRefreshClick = refreshMarketExplicit,
                                        onTogglePrivacy = viewModel::toggleBalanceHidden,
                                        onSelectCurrency = viewModel::setDisplayCurrency,
                                        onAddHoldingClick = openAddHolding,
                                        onQuickAddPreset = viewModel::quickAddPreset,
                                        onHoldingClick = viewModel::openHoldingDetail,
                                        onViewAllHoldings = toHoldings,
                                        onViewMovement = toMovement,
                                        onOpenSettings = openSettings
                                    )
                                }

                                composable(ValorisDestination.Holdings.route) {
                                    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
                                    HoldingsScreen(
                                        dashboard = readyState.dashboard,
                                        filteredHoldings = readyState.filteredHoldings,
                                        selectedFilter = readyState.selectedFilter,
                                        sortOption = readyState.sortOption,
                                        searchQuery = readyState.holdingsSearchQuery,
                                        onSearchQueryChange = viewModel::setHoldingsSearchQuery,
                                        onFilterSelect = viewModel::setFilter,
                                        onSortSelect = viewModel::setSortOption,
                                        onHoldingClick = viewModel::openHoldingDetail,
                                        onAddHoldingClick = openAddHolding,
                                        isRefreshing = isRefreshing,
                                        onRefresh = refreshMarketExplicit
                                    )
                                }

                                composable(ValorisDestination.Movement.route) {
                                    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
                                    val movementState by viewModel.movementState.collectAsStateWithLifecycle()
                                    MovementScreen(
                                        dashboard = readyState.dashboard,
                                        movement = movementState,
                                        onSelectTimeframe = viewModel::selectMovementTimeframe,
                                        onSelectGraphMode = viewModel::selectMovementGraphMode,
                                        onToggleRespectBuyDates = viewModel::toggleRespectBuyDates,
                                        onRefreshMovement = viewModel::refreshMovement,
                                        onAddHoldingClick = openAddHolding,
                                        isRefreshing = isRefreshing
                                    )
                                }

                                composable(ValorisDestination.History.route) {
                                    val historySummary by viewModel.historySummary.collectAsStateWithLifecycle()
                                    val historyFilter by viewModel.historyFilter.collectAsStateWithLifecycle()
                                    val historySearchQuery by viewModel.historySearchQuery.collectAsStateWithLifecycle()
                                    HistoryScreen(
                                        dashboard = readyState.dashboard,
                                        historySummary = historySummary,
                                        selectedFilter = historyFilter,
                                        searchQuery = historySearchQuery,
                                        onFilterSelect = viewModel::setHistoryFilter,
                                        onSearchQueryChange = viewModel::setHistorySearchQuery,
                                        onDeleteTransaction = viewModel::deleteTransaction,
                                        onAddHoldingClick = openAddHolding
                                    )
                                }

                                composable(ValorisDestination.Markets.route) {
                                    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
                                    FxRatesScreen(
                                        dashboard = readyState.dashboard,
                                        isRefreshing = isRefreshing,
                                        onSelectDisplayCurrency = viewModel::setDisplayCurrency,
                                        onSelectRefreshInterval = viewModel::setRefreshInterval,
                                        onRefreshClick = refreshMarketExplicit
                                    )
                                }

                                composable(ROUTE_SETTINGS) {
                                    val goBack = remember(navController) { { navController.popBackStack(); Unit } }
                                    val historySummary by viewModel.historySummary.collectAsStateWithLifecycle()
                                    val isBackupBusy by viewModel.isBackupBusy.collectAsStateWithLifecycle()
                                    val pendingRestorePreview by viewModel.pendingRestorePreview.collectAsStateWithLifecycle()
                                    SettingsScreen(
                                        dashboard = readyState.dashboard,
                                        historyCount = historySummary.items.size,
                                        isBackupBusy = isBackupBusy,
                                        pendingRestorePreview = pendingRestorePreview,
                                        onSelectThemeMode = viewModel::setThemeMode,
                                        onSelectDisplayCurrency = viewModel::setDisplayCurrency,
                                        onToggleCompactNumbers = viewModel::setUseCompactNumbers,
                                        onToggleShowPnlOnCards = viewModel::setShowPnlOnCards,
                                        onToggleExtendedHoursPrice = viewModel::setShowExtendedHoursPrice,
                                        onSelectDefaultChartTimeframe = viewModel::setDefaultChartTimeframe,
                                        onToggleBalanceHidden = viewModel::setBalanceHidden,
                                        onSelectRefreshInterval = viewModel::setRefreshInterval,
                                        onEnableAppLock = viewModel::enableAppLock,
                                        onChangeAppLockPin = viewModel::changeAppLockPin,
                                        onDisableAppLock = viewModel::disableAppLock,
                                        onToggleBiometricUnlock = viewModel::setBiometricUnlockEnabled,
                                        onExportBackupUri = viewModel::exportBackupToUri,
                                        onPrepareRestoreUri = viewModel::prepareRestoreFromUri,
                                        onConfirmRestoreBackup = viewModel::confirmRestoreBackup,
                                        onDismissRestorePreview = viewModel::dismissRestorePreview,
                                        onSystemPickerStateChanged = { active -> isExternalPickerActive = active },
                                        onBack = goBack
                                    )
                                }
                            }

                            // Holding Detail Sheet
                            val detailState by viewModel.detailState.collectAsStateWithLifecycle()
                            detailState?.let { dState ->
                                val target = readyState.dashboard.valuedHoldings
                                    .firstOrNull { it.holding.id == dState.holdingId }
                                if (target != null) {
                                    HoldingDetailSheet(
                                        item = target,
                                        detailState = dState,
                                        displayCurrency = readyState.dashboard.displayCurrency,
                                        isBalanceHidden = readyState.dashboard.isBalanceHidden,
                                        onTimeframeSelect = { tf ->
                                            viewModel.selectDetailTimeframe(target, tf)
                                        },
                                        onAddBuyClick = {
                                            viewModel.closeHoldingDetail()
                                            addBuyTargetHoldingId = target.holding.id
                                        },
                                        onSellClick = {
                                            viewModel.closeHoldingDetail()
                                            sellTargetHoldingId = target.holding.id
                                        },
                                        onEditClick = {
                                            editingHolding = target.holding
                                            viewModel.closeHoldingDetail()
                                            showAddEditSheet = true
                                        },
                                        onDeleteConfirm = {
                                            viewModel.deleteHolding(target.holding.id, target.holding.symbol)
                                        },
                                        onDismiss = { viewModel.closeHoldingDetail() }
                                    )
                                }
                            }

                            // Add New Buy Lot into Existing Owned Asset Sheet
                            addBuyTargetHoldingId?.let { targetId ->
                                val target = readyState.dashboard.valuedHoldings
                                    .firstOrNull { it.holding.id == targetId }
                                if (target != null) {
                                    AddBuyLotSheet(
                                        target = target,
                                        displayCurrency = readyState.dashboard.displayCurrency,
                                        onConfirmBuy = { addedQty, buyPrice, buyDate ->
                                            viewModel.addBuyToHolding(
                                                holdingId = target.holding.id,
                                                symbol = target.holding.symbol,
                                                addedQuantity = addedQty,
                                                buyPricePerUnit = buyPrice,
                                                buyDateMillis = buyDate
                                            )
                                            addBuyTargetHoldingId = null
                                        },
                                        onDismiss = { addBuyTargetHoldingId = null }
                                    )
                                }
                            }

                            // Sell Holding Sheet
                            sellTargetHoldingId?.let { targetId ->
                                val target = readyState.dashboard.valuedHoldings
                                    .firstOrNull { it.holding.id == targetId }
                                if (target != null) {
                                    SellHoldingSheet(
                                        target = target,
                                        displayCurrency = readyState.dashboard.displayCurrency,
                                        onConfirmSell = { sellQty, sellPrice, depositToCash, sellDateMillis ->
                                            viewModel.sellFromHolding(
                                                holdingId = target.holding.id,
                                                sellQuantity = sellQty,
                                                sellPricePerUnit = sellPrice,
                                                depositProceedsToCash = depositToCash,
                                                sellDateMillis = sellDateMillis
                                            )
                                            sellTargetHoldingId = null
                                        },
                                        onDismiss = { sellTargetHoldingId = null }
                                    )
                                }
                            }

                            if (showAddEditSheet) {
                                val searchSuggestions by viewModel.searchSuggestions.collectAsStateWithLifecycle()
                                val isSearchingSymbol by viewModel.isSearchingSymbol.collectAsStateWithLifecycle()
                                AddEditHoldingSheet(
                                    existingHolding = editingHolding,
                                    existingHoldings = readyState.dashboard.valuedHoldings,
                                    searchSuggestions = searchSuggestions,
                                    isSearchingSymbol = isSearchingSymbol,
                                    onSearchSymbolChange = { viewModel.searchSymbol(it) },
                                    onAddBuyToExisting = { owned ->
                                        showAddEditSheet = false
                                        editingHolding = null
                                        addBuyTargetHoldingId = owned.holding.id
                                    },
                                    onSave = { holding ->
                                        viewModel.saveHolding(holding)
                                        showAddEditSheet = false
                                        editingHolding = null
                                    },
                                    onDismiss = {
                                        showAddEditSheet = false
                                        editingHolding = null
                                    }
                                )
                            }
                }
            }
        }
            }
            if (isLocked) {
                PinLockScreen(
                    biometricEnabled = appLockConfig.biometricEnabled,
                    errorMessage = pinError,
                    lockoutRemainingSeconds = pinLockoutRemainingSeconds,
                    onPinEntered = { pin -> viewModel.submitUnlockPin(pin) },
                    onPinTypingChanged = { viewModel.clearPinError() },
                    onBiometricUnlockSuccess = { viewModel.unlockWithBiometric() },
                    onBiometricPromptStateChanged = { active -> isBiometricPromptActive = active }
                )
            }
        }
    }
}
