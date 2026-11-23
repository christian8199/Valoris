package com.example.ui.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CurrencyExchange
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.domain.model.AssetPreset
import com.example.domain.model.PortfolioDashboardData
import com.example.domain.model.PortfolioMovementData
import com.example.domain.model.SupportedCurrency
import com.example.domain.model.ValuedHolding
import com.example.ui.components.AllocationDonutChart
import com.example.ui.components.HoldingLedgerCard
import com.example.ui.components.SyncStatusBadge
import com.example.ui.components.TrendAreaChart
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import com.example.ui.components.colorForAssetClass
import com.example.ui.components.downsampleDoubles
import com.example.ui.theme.LocalWealthColors
import com.example.ui.theme.NumericTextStyles
import com.example.util.CuratedAssets
import com.example.util.CurrencyFormatter

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun OverviewScreen(
    dashboard: PortfolioDashboardData,
    movement: PortfolioMovementData,
    isRefreshing: Boolean,
    onRefreshClick: () -> Unit,
    onTogglePrivacy: () -> Unit,
    onSelectCurrency: (SupportedCurrency) -> Unit,
    onAddHoldingClick: () -> Unit,
    onQuickAddPreset: (AssetPreset, Double) -> Unit,
    onHoldingClick: (ValuedHolding) -> Unit,
    onViewAllHoldings: () -> Unit,
    onViewMovement: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val wealthColors = LocalWealthColors.current
    var showCurrencyMenu by remember { mutableStateOf(false) }
    val topHoldings = remember(dashboard.valuedHoldings) {
        dashboard.valuedHoldings
            .sortedByDescending { it.totalValueDisplay }
            .take(5)
    }
    val previewTimelineValues = remember(movement.totalTimeline, dashboard.trendValuesDisplay) {
        val raw = if (movement.totalTimeline.size >= 2) {
            movement.totalTimeline.map { it.value }
        } else {
            dashboard.trendValuesDisplay
        }
        downsampleDoubles(raw, maxPoints = 56)
    }

    val scrollState = rememberScrollState()
    val heroBrush = remember(wealthColors.heroCardStart, wealthColors.heroCardEnd) {
        Brush.linearGradient(
            colors = listOf(wealthColors.heroCardStart, wealthColors.heroCardEnd)
        )
    }

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefreshClick,
        modifier = modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
        // Editorial Top Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 760.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.displayMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    SyncStatusBadge(
                        isRefreshing = isRefreshing,
                        hasStaleQuotes = dashboard.hasStaleQuotes,
                        lastSyncEpoch = dashboard.lastSyncEpoch
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box {
                        Surface(
                            onClick = { showCurrencyMenu = true },
                            modifier = Modifier
                                .testTag("currency_selector_pill")
                                .minimumInteractiveComponentSize(),
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.CurrencyExchange,
                                    contentDescription = "Preferred currency",
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = dashboard.displayCurrency.code,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }

                        DropdownMenu(
                            expanded = showCurrencyMenu,
                            onDismissRequest = { showCurrencyMenu = false }
                        ) {
                            SupportedCurrency.entries.forEach { currency ->
                                DropdownMenuItem(
                                    text = {
                                        Text("${currency.code} · ${currency.displayName}")
                                    },
                                    onClick = {
                                        onSelectCurrency(currency)
                                        showCurrencyMenu = false
                                    }
                                )
                            }
                        }
                    }

                    IconButton(
                        onClick = onTogglePrivacy,
                        modifier = Modifier
                            .testTag("toggle_privacy_button")
                            .minimumInteractiveComponentSize()
                    ) {
                        Icon(
                            imageVector = if (dashboard.isBalanceHidden) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                            contentDescription = stringResource(R.string.cd_toggle_privacy),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    RefreshIconButton(
                        isRefreshing = isRefreshing,
                        onClick = onRefreshClick
                    )

                    IconButton(
                        onClick = onOpenSettings,
                        modifier = Modifier
                            .testTag("open_settings_button")
                            .minimumInteractiveComponentSize()
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Settings,
                            contentDescription = stringResource(R.string.cd_settings),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
        }

        // Hero Net Worth Ledger Card
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 760.dp)
                .testTag("hero_net_worth_card"),
            shape = MaterialTheme.shapes.large,
            color = wealthColors.heroCardStart
        ) {
            Box(
                modifier = Modifier
                    .background(heroBrush)
                    .padding(22.dp)
            ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "TOTAL NET WORTH (${dashboard.displayCurrency.code})",
                                style = MaterialTheme.typography.labelMedium,
                                color = wealthColors.onHeroCardMuted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            if (dashboard.displayCurrency != SupportedCurrency.USD) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "1 USD = ${
                                        CurrencyFormatter.formatAmount(
                                            dashboard.usdToDisplayRate,
                                            dashboard.displayCurrency.code
                                        )
                                    }",
                                    style = NumericTextStyles.subAmount,
                                    color = wealthColors.goldAccent,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Text(
                            text = CurrencyFormatter.formatPreferenceAmount(
                                amount = dashboard.totalValueDisplay,
                                currencyCode = dashboard.displayCurrency.code,
                                isHidden = dashboard.isBalanceHidden,
                                useCompact = dashboard.useCompactNumbers
                            ),
                            style = NumericTextStyles.heroAmount,
                            color = wealthColors.onHeroCard
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val isDayGain = dashboard.dayChangeDisplay >= 0.0
                            val dayBadgeColor = if (isDayGain) {
                                wealthColors.heroBadgeGain
                            } else {
                                wealthColors.heroBadgeLoss
                            }
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = wealthColors.heroBadgeSurface
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = "24h",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = wealthColors.onHeroCardMuted
                                    )
                                    Text(
                                        text = "${
                                            CurrencyFormatter.formatCompact(
                                                dashboard.dayChangeDisplay,
                                                dashboard.displayCurrency.code,
                                                dashboard.isBalanceHidden,
                                                showSign = true
                                            )
                                        } (${CurrencyFormatter.formatPercent(dashboard.dayChangePercent)})",
                                        style = NumericTextStyles.badge,
                                        color = dayBadgeColor,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }

                            dashboard.totalUnrealizedGainDisplay?.let { totalGain ->
                                val isPnlGain = totalGain >= 0.0
                                val pnlColor = if (isPnlGain) {
                                    wealthColors.heroBadgeGain
                                } else {
                                    wealthColors.heroBadgeLoss
                                }
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = wealthColors.heroBadgeSurface
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = "P&L",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = wealthColors.onHeroCardMuted,
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                        Text(
                                            text = "${
                                                CurrencyFormatter.formatCompact(
                                                    amount = totalGain,
                                                    currencyCode = dashboard.displayCurrency.code,
                                                    isHidden = dashboard.isBalanceHidden,
                                                    showSign = true
                                                )
                                            } (${
                                                CurrencyFormatter.formatPercent(
                                                    dashboard.totalUnrealizedGainPercent ?: 0.0
                                                )
                                            })",
                                            style = NumericTextStyles.badge,
                                            color = pnlColor,
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
        }

        // Empty State Onboarding Card
        if (dashboard.valuedHoldings.isEmpty()) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 760.dp)
                    .testTag("empty_portfolio_card"),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Image(
                        painter = painterResource(id = R.drawable.img_vault_empty_1795080059361),
                        contentDescription = stringResource(R.string.cd_empty_vault),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(168.dp),
                        contentScale = ContentScale.Crop
                    )

                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(
                            text = "Build your live multi-asset ledger",
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Track global stocks, ETFs, cash accounts, and commodities in one private vault—automatically converted to ${dashboard.displayCurrency.code}.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = onAddHoldingClick,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("empty_add_first_button"),
                            shape = MaterialTheme.shapes.medium,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Add,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Add Your First Holding")
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "OR QUICK-ADD A SAMPLE POSITION",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val quickPicks = remember {
                                listOf(
                                    CuratedAssets.presets.first { it.symbol == "VOO" } to 5.0,
                                    CuratedAssets.presets.first { it.symbol == "BBCA.JK" } to 1000.0,
                                    CuratedAssets.presets.first { it.symbol == "GC=F" } to 10.0,
                                    CuratedAssets.presets.first { it.symbol == "CASH-IDR" } to 25_000_000.0
                                )
                            }
                            quickPicks.forEach { (preset, qty) ->
                                AssistChip(
                                    onClick = { onQuickAddPreset(preset, qty) },
                                    label = {
                                        Text(
                                            text = "+ ${preset.symbol.removePrefix("CASH-")}",
                                            style = MaterialTheme.typography.labelLarge
                                        )
                                    }
                                )
                            }
                        }
                    }
                }
            }
        } else {
            // Asset Allocation Card
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 760.dp)
                    .testTag("allocation_card"),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = "Asset Allocation",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(14.dp))

                    AdaptiveAllocationRow(
                        donut = {
                            AllocationDonutChart(
                                slices = dashboard.allocationSlices,
                                totalLabel = if (dashboard.allocationSlices.size == 1) "Class" else "Classes",
                                chartSize = 116.dp,
                                strokeWidth = 14.dp
                            )
                        },
                        legend = {
                            AllocationLegendColumn(
                                slices = dashboard.allocationSlices,
                                displayCurrencyCode = dashboard.displayCurrency.code,
                                isBalanceHidden = dashboard.isBalanceHidden,
                                wealthColors = wealthColors,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    )
                }
            }

            // Total Asset Movement Preview Card
            if (previewTimelineValues.isNotEmpty() || movement.isLoading) {
                Surface(
                    onClick = onViewMovement,
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 760.dp)
                        .testTag("net_worth_trend_card"),
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Total Asset Movement",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                val periodLabel = remember(
                                    movement.totalTimeline.size,
                                    movement.timeframe,
                                    movement.periodChangePercent,
                                    previewTimelineValues.size
                                ) {
                                    if (movement.totalTimeline.size >= 2) {
                                        "${movement.timeframe.label} · ${CurrencyFormatter.formatPercent(movement.periodChangePercent)}"
                                    } else {
                                        "${previewTimelineValues.size} snapshots"
                                    }
                                }
                                Text(
                                    text = periodLabel,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            TextButton(
                                onClick = onViewMovement,
                                modifier = Modifier.testTag("overview_compare_assets_button")
                            ) {
                                Text("Compare Assets")
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = null,
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        val first = previewTimelineValues.firstOrNull() ?: 0.0
                        val last = previewTimelineValues.lastOrNull() ?: 0.0
                        val trendColor = if (last >= first) wealthColors.gain else wealthColors.loss
                        TrendAreaChart(
                            points = previewTimelineValues,
                            lineColor = trendColor,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(110.dp)
                        )
                    }
                }
            }

            // Top Holdings Section
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 760.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Top Holdings",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )
                TextButton(
                    onClick = onViewAllHoldings,
                    modifier = Modifier.testTag("view_all_holdings_button")
                ) {
                    Text("See all (${dashboard.valuedHoldings.size})")
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            for (item in topHoldings) {
                key(item.holding.id) {
                    val handleClick = remember(item, onHoldingClick) { { onHoldingClick(item) } }
                    HoldingLedgerCard(
                        item = item,
                        displayCurrency = dashboard.displayCurrency,
                        isBalanceHidden = dashboard.isBalanceHidden,
                        useCompactNumbers = dashboard.useCompactNumbers,
                        showPnlOnCards = dashboard.showPnlOnCards,
                        onClick = handleClick,
                        modifier = Modifier.widthIn(max = 760.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(72.dp))
        }
    }
}
}

@Composable
private fun AdaptiveAllocationRow(
    donut: @Composable () -> Unit,
    legend: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    Layout(
        modifier = modifier.fillMaxWidth(),
        content = {
            Box { donut() }
            Box { legend() }
        }
    ) { measurables, constraints ->
        val compactBreakpointPx = 310.dp.roundToPx()
        val gapPx = 14.dp.roundToPx()
        val isCompact = constraints.maxWidth < compactBreakpointPx

        if (isCompact) {
            val donutPlaceable = measurables[0].measure(constraints.copy(minWidth = 0))
            val legendPlaceable = measurables[1].measure(
                Constraints(
                    minWidth = constraints.maxWidth,
                    maxWidth = constraints.maxWidth,
                    minHeight = 0,
                    maxHeight = constraints.maxHeight
                )
            )
            val totalHeight = donutPlaceable.height + gapPx + legendPlaceable.height
            layout(constraints.maxWidth, totalHeight) {
                val donutX = ((constraints.maxWidth - donutPlaceable.width) / 2).coerceAtLeast(0)
                donutPlaceable.placeRelative(donutX, 0)
                legendPlaceable.placeRelative(0, donutPlaceable.height + gapPx)
            }
        } else {
            val donutPlaceable = measurables[0].measure(constraints.copy(minWidth = 0))
            val remainingWidth = (constraints.maxWidth - donutPlaceable.width - gapPx).coerceAtLeast(0)
            val legendPlaceable = measurables[1].measure(
                Constraints(
                    minWidth = remainingWidth,
                    maxWidth = remainingWidth,
                    minHeight = 0,
                    maxHeight = constraints.maxHeight
                )
            )
            val totalHeight = maxOf(donutPlaceable.height, legendPlaceable.height)
            layout(constraints.maxWidth, totalHeight) {
                val donutY = (totalHeight - donutPlaceable.height) / 2
                val legendY = (totalHeight - legendPlaceable.height) / 2
                donutPlaceable.placeRelative(0, donutY)
                legendPlaceable.placeRelative(donutPlaceable.width + gapPx, legendY)
            }
        }
    }
}

@Composable
private fun RefreshIconButton(
    isRefreshing: Boolean,
    onClick: () -> Unit
) {
    val rotationState = if (isRefreshing) {
        val spinTransition = rememberInfiniteTransition(label = "refresh_spin")
        spinTransition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 900, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "spin_deg"
        )
    } else {
        null
    }

    IconButton(
        onClick = onClick,
        modifier = Modifier
            .testTag("refresh_prices_button")
            .minimumInteractiveComponentSize()
    ) {
        Icon(
            imageVector = Icons.Filled.Refresh,
            contentDescription = stringResource(R.string.cd_refresh_prices),
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.graphicsLayer {
                rotationZ = rotationState?.value ?: 0f
            }
        )
    }
}

@Composable
private fun AllocationLegendColumn(
    slices: List<com.example.domain.model.AllocationSlice>,
    displayCurrencyCode: String,
    isBalanceHidden: Boolean,
    wealthColors: com.example.ui.theme.WealthExtendedColors,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        slices.forEach { slice ->
            val dotColor = colorForAssetClass(slice.assetClass, wealthColors)
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(7.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(9.dp)
                                .clip(CircleShape)
                                .background(dotColor)
                        )
                        Text(
                            text = slice.assetClass.displayName,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "${CurrencyFormatter.formatOneDecimal(slice.percentage)}%",
                        style = NumericTextStyles.badge,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        softWrap = false
                    )
                }
                Text(
                    text = CurrencyFormatter.formatCompact(
                        amount = slice.valueDisplay,
                        currencyCode = displayCurrencyCode,
                        isHidden = isBalanceHidden
                    ),
                    style = NumericTextStyles.subAmount,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.padding(start = 16.dp)
                )
            }
        }
    }
}
