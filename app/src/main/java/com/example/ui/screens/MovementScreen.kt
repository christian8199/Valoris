package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.domain.model.ChartTimeframe
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import com.example.domain.model.HistoricalPoint
import com.example.domain.model.MovementGraphMode
import com.example.domain.model.PortfolioDashboardData
import com.example.domain.model.PortfolioMovementData
import com.example.ui.components.InteractiveTotalMovementChart
import com.example.ui.components.MultiAssetComparisonChart
import com.example.ui.components.comparisonPalette
import com.example.ui.components.computeNormalizedRangePercent
import com.example.ui.theme.LocalWealthColors
import com.example.ui.theme.NumericTextStyles
import com.example.util.CurrencyFormatter

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun MovementScreen(
    dashboard: PortfolioDashboardData,
    movement: PortfolioMovementData,
    onSelectTimeframe: (ChartTimeframe) -> Unit,
    onSelectGraphMode: (MovementGraphMode) -> Unit,
    onToggleRespectBuyDates: () -> Unit,
    onRefreshMovement: () -> Unit,
    onAddHoldingClick: () -> Unit,
    modifier: Modifier = Modifier,
    isRefreshing: Boolean = false
) {
    val wealthColors = LocalWealthColors.current
    val palette = comparisonPalette(isSystemInDarkTheme())
    var totalScrubIndex by remember(movement.timeframe, movement.graphMode) { mutableStateOf<Int?>(null) }
    var totalRange by remember(
        movement.timeframe,
        movement.graphMode,
        movement.totalTimeline.size,
        movement.pnlTimeline.size
    ) { mutableStateOf<IntRange?>(null) }
    var multiScrubIndex by remember(movement.timeframe) { mutableStateOf<Int?>(null) }
    var multiRange by remember(movement.timeframe, movement.assetSeries.size) { mutableStateOf<IntRange?>(null) }
    var hiddenSymbols by remember { mutableStateOf<Set<String>>(emptySet()) }

    val seriesColorMap = remember(movement.assetSeries, palette) {
        movement.assetSeries.mapIndexed { index, series ->
            series.symbol to palette[index % palette.size]
        }.toMap()
    }

    val visibleSeries = remember(movement.assetSeries, hiddenSymbols) {
        movement.assetSeries.filterNot { it.symbol in hiddenSymbols }
    }

    val sortedAssetSeries = remember(movement.assetSeries) {
        movement.assetSeries.sortedByDescending { it.periodChangePercent }
    }

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefreshMovement,
        modifier = modifier.fillMaxSize()
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
        // Header
        item(key = "movement_header", contentType = "header") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 760.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Asset Movement",
                        style = MaterialTheme.typography.displayMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "Total portfolio & cross-asset performance (${dashboard.displayCurrency.code})",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                IconButton(
                    onClick = onRefreshMovement,
                    modifier = Modifier.testTag("movement_refresh_button")
                ) {
                    Icon(Icons.Filled.Refresh, contentDescription = "Refresh movement charts")
                }
            }
        }

        if (dashboard.valuedHoldings.isEmpty() && movement.pnlTimeline.isEmpty() && movement.totalTimeline.isEmpty()) {
            item(key = "movement_empty", contentType = "empty_card") {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 760.dp),
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "No assets to graph yet",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Add stocks, ETFs, cash, or commodities with optional buy dates to compare price movement across your assets and total net worth.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = onAddHoldingClick) {
                            Text("Add First Holding")
                        }
                    }
                }
            }
            return@LazyColumn
        }

        // Shared Timeframe & Buy-Date Controls
        item(key = "movement_controls", contentType = "controls") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 760.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ChartTimeframe.entries.forEach { tf ->
                        FilterChip(
                            selected = movement.timeframe == tf,
                            onClick = { onSelectTimeframe(tf) },
                            label = { Text(tf.label) },
                            modifier = Modifier.testTag("movement_tf_${tf.label}")
                        )
                    }

                    if (movement.hasAnyBuyDates) {
                        FilterChip(
                            selected = movement.respectBuyDates,
                            onClick = onToggleRespectBuyDates,
                            label = { Text("Track Trade Dates") },
                            leadingIcon = {
                                Icon(
                                    imageVector = if (movement.respectBuyDates) Icons.Filled.Check else Icons.Filled.CalendarToday,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            modifier = Modifier.testTag("toggle_respect_buy_dates")
                        )
                    }
                }

                if (movement.isLoading) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }

                movement.errorMessage?.let { err ->
                    Text(
                        text = err,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }

        // Card 1: Total Asset Value / Total Profit & Loss Dual Graph
        item(key = "movement_total_card", contentType = "total_chart_card") {
            val isPnlMode = movement.graphMode == MovementGraphMode.PROFIT_LOSS
            val pnlChartPoints = remember(movement.pnlTimeline) {
                movement.pnlTimeline.map { HistoricalPoint(it.timestampSec, it.totalReturnDisplay) }
            }
            val activeTimeline = if (isPnlMode) pnlChartPoints else movement.totalTimeline

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 760.dp)
                    .testTag("total_asset_movement_card"),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    SingleChoiceSegmentedButtonRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 14.dp)
                    ) {
                        MovementGraphMode.entries.forEachIndexed { index, mode ->
                            SegmentedButton(
                                selected = movement.graphMode == mode,
                                onClick = {
                                    totalScrubIndex = null
                                    totalRange = null
                                    onSelectGraphMode(mode)
                                },
                                shape = SegmentedButtonDefaults.itemShape(
                                    index = index,
                                    count = MovementGraphMode.entries.size
                                ),
                                modifier = Modifier.testTag(
                                    if (mode == MovementGraphMode.ASSET_VALUE) "movement_mode_asset_value"
                                    else "movement_mode_profit_loss"
                                )
                            ) {
                                Text(
                                    text = mode.label,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                    }

                    if (!isPnlMode) {
                        val rangeStartPt = totalRange?.let { movement.totalTimeline.getOrNull(it.first) }
                        val rangeEndPt = totalRange?.let { movement.totalTimeline.getOrNull(it.last) }
                        val isRanging = rangeStartPt != null && rangeEndPt != null

                        val activePoint = if (isRanging) {
                            rangeEndPt
                        } else {
                            totalScrubIndex?.let { idx -> movement.totalTimeline.getOrNull(idx) }
                                ?: movement.totalTimeline.lastOrNull()
                        }

                        val displayVal = activePoint?.value ?: dashboard.totalValueDisplay
                        val shownDeltaDisplay = if (rangeStartPt != null && rangeEndPt != null) {
                            rangeEndPt.value - rangeStartPt.value
                        } else {
                            movement.periodChangeDisplay
                        }
                        val shownDeltaPercent = if (rangeStartPt != null && rangeEndPt != null) {
                            val startV = rangeStartPt.value
                            if (startV > 0.0) ((rangeEndPt.value - startV) / startV) * 100.0 else 0.0
                        } else {
                            movement.periodChangePercent
                        }
                        val isPositive = shownDeltaPercent >= 0.0 && shownDeltaDisplay >= 0.0
                        val lineColor = if (isPositive) wealthColors.gain else wealthColors.loss

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (movement.respectBuyDates) {
                                        "TOTAL ASSET VALUE (BY TRADE HISTORY · ${movement.timeframe.label})"
                                    } else {
                                        "TOTAL ASSET VALUE (CURRENT HOLDINGS · ${movement.timeframe.label})"
                                    },
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = CurrencyFormatter.formatAmount(
                                        displayVal,
                                        dashboard.displayCurrency.code,
                                        dashboard.isBalanceHidden
                                    ),
                                    style = NumericTextStyles.heroAmount.copy(
                                        fontSize = MaterialTheme.typography.headlineMedium.fontSize
                                    ),
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    softWrap = false
                                )
                                if (rangeStartPt != null && rangeEndPt != null) {
                                    val fromLabel = CurrencyFormatter.formatChartScrubDate(
                                        rangeStartPt.timestampSec,
                                        movement.timeframe
                                    )
                                    val toLabel = CurrencyFormatter.formatChartScrubDate(
                                        rangeEndPt.timestampSec,
                                        movement.timeframe
                                    )
                                    val fromAmount = CurrencyFormatter.formatCompact(
                                        rangeStartPt.value,
                                        dashboard.displayCurrency.code,
                                        dashboard.isBalanceHidden
                                    )
                                    Text(
                                        text = "$fromLabel → $toLabel · from $fromAmount",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = lineColor
                                    )
                                } else {
                                    activePoint?.let { pt ->
                                        val scrubLabel = CurrencyFormatter.formatChartScrubDate(pt.timestampSec, movement.timeframe)
                                        Text(
                                            text = if (totalScrubIndex != null) {
                                                "On $scrubLabel"
                                            } else {
                                                "Drag to inspect · 2 fingers to select range"
                                            },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.width(10.dp))

                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (isPositive) wealthColors.gainContainer else wealthColors.lossContainer
                            ) {
                                Column(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    horizontalAlignment = Alignment.End
                                ) {
                                    Text(
                                        text = CurrencyFormatter.formatPercent(shownDeltaPercent),
                                        style = NumericTextStyles.badge,
                                        color = lineColor,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                    Text(
                                        text = CurrencyFormatter.formatCompact(
                                            shownDeltaDisplay,
                                            dashboard.displayCurrency.code,
                                            dashboard.isBalanceHidden,
                                            showSign = true
                                        ),
                                        style = NumericTextStyles.subAmount,
                                        color = lineColor,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        InteractiveTotalMovementChart(
                            points = movement.totalTimeline,
                            lineColor = lineColor,
                            scrubIndex = totalScrubIndex,
                            onScrubIndexChange = { totalScrubIndex = it },
                            showZeroBaseline = false,
                            rangeSelection = totalRange,
                            onRangeSelectionChange = { totalRange = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(165.dp)
                        )
                    } else {
                        val rangeStartPnl = totalRange?.let { movement.pnlTimeline.getOrNull(it.first) }
                        val rangeEndPnl = totalRange?.let { movement.pnlTimeline.getOrNull(it.last) }
                        val isRanging = rangeStartPnl != null && rangeEndPnl != null

                        val activePnl = if (isRanging) {
                            rangeEndPnl
                        } else {
                            totalScrubIndex?.let { idx -> movement.pnlTimeline.getOrNull(idx) }
                                ?: movement.pnlTimeline.lastOrNull()
                        }

                        val pnlVal = if (rangeStartPnl != null && rangeEndPnl != null) {
                            rangeEndPnl.totalReturnDisplay - rangeStartPnl.totalReturnDisplay
                        } else {
                            activePnl?.totalReturnDisplay ?: movement.latestTotalReturnDisplay
                        }
                        val unrealVal = if (rangeStartPnl != null && rangeEndPnl != null) {
                            rangeEndPnl.unrealizedDisplay - rangeStartPnl.unrealizedDisplay
                        } else {
                            activePnl?.unrealizedDisplay ?: movement.latestUnrealizedPnlDisplay
                        }
                        val realVal = if (rangeStartPnl != null && rangeEndPnl != null) {
                            rangeEndPnl.realizedDisplay - rangeStartPnl.realizedDisplay
                        } else {
                            activePnl?.realizedDisplay ?: movement.latestRealizedPnlDisplay
                        }
                        val isPositive = pnlVal >= 0.0
                        val lineColor = if (isPositive) wealthColors.gain else wealthColors.loss

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (isRanging) {
                                        "P&L RANGE CHANGE (${movement.timeframe.label})"
                                    } else {
                                        "TOTAL PROFIT & LOSS (${movement.timeframe.label})"
                                    },
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = CurrencyFormatter.formatAmount(
                                        amount = pnlVal,
                                        currencyCode = dashboard.displayCurrency.code,
                                        isHidden = dashboard.isBalanceHidden,
                                        showSign = true
                                    ),
                                    style = NumericTextStyles.heroAmount.copy(
                                        fontSize = MaterialTheme.typography.headlineMedium.fontSize
                                    ),
                                    color = lineColor,
                                    maxLines = 1,
                                    softWrap = false
                                )
                                val dateNote = if (rangeStartPnl != null && rangeEndPnl != null) {
                                    val fromLabel = CurrencyFormatter.formatChartScrubDate(
                                        rangeStartPnl.timestampSec,
                                        movement.timeframe
                                    )
                                    val toLabel = CurrencyFormatter.formatChartScrubDate(
                                        rangeEndPnl.timestampSec,
                                        movement.timeframe
                                    )
                                    "$fromLabel → $toLabel"
                                } else {
                                    activePnl?.let { pt ->
                                        val scrubLabel = CurrencyFormatter.formatChartScrubDate(pt.timestampSec, movement.timeframe)
                                        if (totalScrubIndex != null) "On $scrubLabel" else "Unrealized + Realized · 2 fingers for range"
                                    } ?: "Unrealized + Realized Return"
                                }
                                Text(
                                    text = dateNote,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (isRanging) lineColor else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Spacer(modifier = Modifier.width(10.dp))

                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (isPositive) wealthColors.gainContainer else wealthColors.lossContainer
                            ) {
                                Column(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    horizontalAlignment = Alignment.End
                                ) {
                                    if (isRanging) {
                                        val startValue = totalRange?.first?.let { movement.totalTimeline.getOrNull(it)?.value }
                                        val rangeRatioPct = if (startValue != null && startValue > 0.0) {
                                            (pnlVal / startValue) * 100.0
                                        } else null
                                        Text(
                                            text = if (rangeRatioPct != null) {
                                                CurrencyFormatter.formatPercent(rangeRatioPct)
                                            } else {
                                                CurrencyFormatter.formatCompact(
                                                    amount = pnlVal,
                                                    currencyCode = dashboard.displayCurrency.code,
                                                    isHidden = dashboard.isBalanceHidden,
                                                    showSign = true
                                                )
                                            },
                                            style = NumericTextStyles.badge,
                                            color = lineColor,
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                        Text(
                                            text = "Range Δ",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = lineColor,
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                    } else {
                                        Text(
                                            text = CurrencyFormatter.formatPercent(movement.latestReturnPercent),
                                            style = NumericTextStyles.badge,
                                            color = lineColor,
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                        Text(
                                            text = "Total Return",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = lineColor,
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                            ) {
                                Text(
                                    text = "${if (isRanging) "Unrealized Δ" else "Unrealized"}: ${
                                        CurrencyFormatter.formatCompact(
                                            amount = unrealVal,
                                            currencyCode = dashboard.displayCurrency.code,
                                            isHidden = dashboard.isBalanceHidden,
                                            showSign = true
                                        )
                                    }",
                                    style = NumericTextStyles.subAmount,
                                    color = if (unrealVal >= 0.0) wealthColors.gain else wealthColors.loss,
                                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                            ) {
                                Text(
                                    text = "${if (isRanging) "Realized Δ" else "Realized"}: ${
                                        CurrencyFormatter.formatCompact(
                                            amount = realVal,
                                            currencyCode = dashboard.displayCurrency.code,
                                            isHidden = dashboard.isBalanceHidden,
                                            showSign = true
                                        )
                                    }",
                                    style = NumericTextStyles.subAmount,
                                    color = if (realVal >= 0.0) wealthColors.gain else wealthColors.loss,
                                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        InteractiveTotalMovementChart(
                            points = pnlChartPoints,
                            lineColor = lineColor,
                            scrubIndex = totalScrubIndex,
                            onScrubIndexChange = { totalScrubIndex = it },
                            showZeroBaseline = true,
                            rangeSelection = totalRange,
                            onRangeSelectionChange = { totalRange = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(165.dp)
                        )
                    }

                    if (activeTimeline.size >= 2) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = CurrencyFormatter.formatChartAxisDate(
                                    activeTimeline.first().timestampSec,
                                    movement.timeframe
                                ),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (isPnlMode) {
                                Text(
                                    text = "0 breakeven",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                text = CurrencyFormatter.formatChartAxisDate(
                                    activeTimeline.last().timestampSec,
                                    movement.timeframe
                                ),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        // Card 2: Price Movement Across Assets (% Comparison)
        if (movement.assetSeries.isNotEmpty()) {
            item(key = "movement_cross_asset_card", contentType = "cross_asset_card") {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 760.dp)
                        .testTag("cross_asset_movement_card"),
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Text(
                            text = "Price Movement Across Assets",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        val rangeStartTs = multiRange?.let { r ->
                            visibleSeries.firstOrNull()?.normalizedPercentPoints?.getOrNull(r.first)?.timestampSec
                        }
                        val rangeEndTs = multiRange?.let { r ->
                            visibleSeries.firstOrNull()?.normalizedPercentPoints?.getOrNull(r.last)?.timestampSec
                        }
                        val scrubTimestamp = multiScrubIndex?.let { idx ->
                            visibleSeries.firstOrNull()?.normalizedPercentPoints?.getOrNull(idx)?.timestampSec
                        }
                        Text(
                            text = when {
                                rangeStartTs != null && rangeEndTs != null -> {
                                    val fromLabel = CurrencyFormatter.formatChartScrubDate(rangeStartTs, movement.timeframe)
                                    val toLabel = CurrencyFormatter.formatChartScrubDate(rangeEndTs, movement.timeframe)
                                    "$fromLabel → $toLabel · range % change"
                                }
                                scrubTimestamp != null -> {
                                    val tsText = CurrencyFormatter.formatChartScrubDate(scrubTimestamp, movement.timeframe)
                                    "Relative % change on $tsText"
                                }
                                else -> "Relative % change over ${movement.timeframe.label} · 2 fingers for range"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (rangeStartTs != null && rangeEndTs != null) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // Toggleable Legend Chips with live / scrubbed / range % readout
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            movement.assetSeries.forEach { series ->
                                val color = seriesColorMap[series.symbol] ?: wealthColors.goldAccent
                                val isVisible = series.symbol !in hiddenSymbols
                                val shownPct = when {
                                    multiRange != null -> {
                                        val r = multiRange!!
                                        val pts = series.normalizedPercentPoints
                                        val pStart = pts.getOrNull(r.first.coerceIn(0, (pts.size - 1).coerceAtLeast(0)))?.value
                                        val pEnd = pts.getOrNull(r.last.coerceIn(0, (pts.size - 1).coerceAtLeast(0)))?.value
                                        if (pStart != null && pEnd != null) {
                                            computeNormalizedRangePercent(pStart, pEnd)
                                        } else {
                                            series.periodChangePercent
                                        }
                                    }
                                    multiScrubIndex != null -> {
                                        series.normalizedPercentPoints.getOrNull(multiScrubIndex!!)?.value
                                            ?: series.periodChangePercent
                                    }
                                    else -> series.periodChangePercent
                                }

                                FilterChip(
                                    selected = isVisible,
                                    onClick = {
                                        hiddenSymbols = if (isVisible) {
                                            if (visibleSeries.size > 1) hiddenSymbols + series.symbol else hiddenSymbols
                                        } else {
                                            hiddenSymbols - series.symbol
                                        }
                                    },
                                    leadingIcon = {
                                        Box(
                                            modifier = Modifier
                                                .size(9.dp)
                                                .clip(CircleShape)
                                                .background(if (isVisible) color else color.copy(alpha = 0.35f))
                                        )
                                    },
                                    label = {
                                        Text(
                                            text = "${series.symbol} ${CurrencyFormatter.formatPercent(shownPct)}",
                                            style = NumericTextStyles.badge
                                        )
                                    },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = color.copy(alpha = 0.12f),
                                        selectedLabelColor = MaterialTheme.colorScheme.onSurface
                                    )
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        MultiAssetComparisonChart(
                            seriesList = visibleSeries,
                            seriesColors = seriesColorMap,
                            scrubIndex = multiScrubIndex,
                            onScrubIndexChange = { multiScrubIndex = it },
                            rangeSelection = multiRange,
                            onRangeSelectionChange = { multiRange = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(190.dp)
                        )

                        val firstAxisTs = visibleSeries.firstOrNull()?.normalizedPercentPoints?.firstOrNull()?.timestampSec
                        val lastAxisTs = visibleSeries.firstOrNull()?.normalizedPercentPoints?.lastOrNull()?.timestampSec
                        if (firstAxisTs != null && lastAxisTs != null) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = CurrencyFormatter.formatChartAxisDate(firstAxisTs, movement.timeframe),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "0% baseline",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = CurrencyFormatter.formatChartAxisDate(lastAxisTs, movement.timeframe),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // Ranked Performance Breakdown
            item(key = "movement_breakdown_header", contentType = "section_header") {
                Text(
                    text = "Performance Breakdown (${movement.timeframe.label})",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 760.dp)
                )
            }

            items(
                items = sortedAssetSeries,
                key = { it.symbol },
                contentType = { "performance_row" }
            ) { series ->
                val color = seriesColorMap[series.symbol] ?: wealthColors.goldAccent
                val isGain = series.periodChangePercent >= 0.0
                val changeColor = if (isGain) wealthColors.gain else wealthColors.loss
                val changeBg = if (isGain) wealthColors.gainContainer else wealthColors.lossContainer
                val priceSubtitle = remember(series.name, series.latestPriceNative, series.nativeCurrency) {
                    "${series.name} · ${
                        CurrencyFormatter.formatAmount(series.latestPriceNative, series.nativeCurrency)
                    }"
                }
                val changeText = remember(series.periodChangePercent) {
                    CurrencyFormatter.formatPercent(series.periodChangePercent)
                }

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 760.dp),
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .background(color, CircleShape)
                            )
                            Column {
                                Text(
                                    text = series.symbol,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = priceSubtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Text(
                            text = changeText,
                            style = NumericTextStyles.badge,
                            fontWeight = FontWeight.SemiBold,
                            color = changeColor,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier
                                .background(changeBg, RoundedCornerShape(8.dp))
                                .padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                }
            }
        }

        item(key = "movement_bottom_spacer", contentType = "spacer") { Spacer(modifier = Modifier.height(72.dp)) }
    }
    }
}
