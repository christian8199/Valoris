package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddShoppingCart
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.RemoveShoppingCart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.domain.model.AssetClass
import com.example.domain.model.ChartTimeframe
import com.example.domain.model.MarketStatus
import com.example.domain.model.SupportedCurrency
import com.example.domain.model.ValuedHolding
import com.example.ui.components.AssetClassPill
import com.example.ui.components.ExtendedHoursBadge
import com.example.ui.components.InteractiveTotalMovementChart
import com.example.ui.components.MarketStateBadge
import com.example.ui.components.colorForAssetClass
import com.example.ui.theme.LocalWealthColors
import com.example.ui.theme.NumericTextStyles
import com.example.ui.viewmodel.HoldingDetailState
import com.example.util.CurrencyFormatter

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun HoldingDetailSheet(
    item: ValuedHolding,
    detailState: HoldingDetailState,
    displayCurrency: SupportedCurrency,
    isBalanceHidden: Boolean,
    onTimeframeSelect: (ChartTimeframe) -> Unit,
    onAddBuyClick: () -> Unit,
    onSellClick: () -> Unit,
    onEditClick: () -> Unit,
    onDeleteConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    BackHandler(onBack = onDismiss)

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val wealthColors = LocalWealthColors.current
    val accent = colorForAssetClass(item.holding.assetClass, wealthColors)
    var showDeleteConfirm by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    FlowRow(
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = item.holding.symbol,
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.align(Alignment.CenterVertically)
                        )
                        Box(modifier = Modifier.align(Alignment.CenterVertically)) {
                            AssetClassPill(label = item.holding.assetClass.singularName, color = accent)
                        }
                        item.marketStatus?.let { market ->
                            MarketStateBadge(
                                marketStatus = market,
                                modifier = Modifier.align(Alignment.CenterVertically)
                            )
                        }
                        if (item.isUsingExtendedHoursPrice) {
                            ExtendedHoursBadge(
                                modifier = Modifier.align(Alignment.CenterVertically)
                            )
                        }
                    }
                    Text(
                        text = item.holding.name,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = "Close detail")
                }
            }

            // Valuation Hero Block
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "CONVERTED VALUE (${displayCurrency.code})",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = CurrencyFormatter.formatAmount(
                            item.totalValueDisplay,
                            displayCurrency.code,
                            isBalanceHidden
                        ),
                        style = NumericTextStyles.heroAmount,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (item.holding.nativeCurrency != displayCurrency.code) {
                        Text(
                            text = "Native: ${
                                CurrencyFormatter.formatAmount(
                                    item.totalValueNative,
                                    item.holding.nativeCurrency,
                                    isBalanceHidden
                                )
                            } · FX 1 ${item.holding.nativeCurrency} = ${
                                CurrencyFormatter.formatAmount(item.fxRateToDisplay, displayCurrency.code)
                            }",
                            style = NumericTextStyles.subAmount,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Historical Price Chart for Live Market Assets
            if (item.holding.assetClass != AssetClass.CASH && item.holding.manualPriceOverride == null) {
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    var scrubIndex by remember(detailState.holdingId, detailState.timeframe) {
                        mutableStateOf<Int?>(null)
                    }
                    var rangeSelection by remember(
                        detailState.holdingId,
                        detailState.timeframe,
                        detailState.chartPoints.size
                    ) {
                        mutableStateOf<IntRange?>(null)
                    }
                    val pts = detailState.chartPoints
                    val first = pts.firstOrNull()?.value ?: 0.0
                    val last = pts.lastOrNull()?.value ?: 0.0
                    val rangeStartPt = rangeSelection?.let { pts.getOrNull(it.first) }
                    val rangeEndPt = rangeSelection?.let { pts.getOrNull(it.last) }
                    val isRanging = rangeStartPt != null && rangeEndPt != null

                    val activePoint = if (rangeStartPt != null && rangeEndPt != null) {
                        rangeEndPt
                    } else {
                        scrubIndex?.let { pts.getOrNull(it) }
                    }
                    val shownPrice = activePoint?.value ?: item.unitQuoteNative
                    val periodChangePct = when {
                        rangeStartPt != null && rangeEndPt != null -> {
                            val startV = rangeStartPt.value
                            if (startV > 0.0) ((rangeEndPt.value - startV) / startV) * 100.0 else 0.0
                        }
                        first > 0.0 -> (((activePoint?.value ?: last) - first) / first) * 100.0
                        else -> 0.0
                    }
                    val rangeDeltaNative = if (rangeStartPt != null && rangeEndPt != null) rangeEndPt.value - rangeStartPt.value else 0.0
                    val chartColor = if (last >= first) wealthColors.gain else wealthColors.loss
                    val rangeColor = if (isRanging) {
                        if (rangeDeltaNative >= 0.0) wealthColors.gain else wealthColors.loss
                    } else {
                        chartColor
                    }

                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                val unitSuffix = if (item.holding.assetClass == AssetClass.COMMODITY) {
                                    " / ${item.holding.unitType.shortLabel}"
                                } else ""
                                Text(
                                    text = "Price History (${item.holding.nativeCurrency}$unitSuffix)",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                val subtitleText = when {
                                    rangeStartPt != null && rangeEndPt != null && rangeStartPt.timestampSec > 0L && rangeEndPt.timestampSec > 0L -> {
                                        val fromDate = CurrencyFormatter.formatChartScrubDate(
                                            rangeStartPt.timestampSec,
                                            detailState.timeframe
                                        )
                                        val toDate = CurrencyFormatter.formatChartScrubDate(
                                            rangeEndPt.timestampSec,
                                            detailState.timeframe
                                        )
                                        val deltaText = CurrencyFormatter.formatAmount(
                                            amount = rangeDeltaNative,
                                            currencyCode = item.holding.nativeCurrency,
                                            showSign = true
                                        )
                                        "$fromDate → $toDate · $deltaText (${CurrencyFormatter.formatPercent(periodChangePct)})"
                                    }
                                    isRanging -> {
                                        val deltaText = CurrencyFormatter.formatAmount(
                                            amount = rangeDeltaNative,
                                            currencyCode = item.holding.nativeCurrency,
                                            showSign = true
                                        )
                                        "Selected range · $deltaText (${CurrencyFormatter.formatPercent(periodChangePct)})"
                                    }
                                    activePoint != null && activePoint.timestampSec > 0L ->
                                        "${CurrencyFormatter.formatChartScrubDate(activePoint.timestampSec, detailState.timeframe)} · ${CurrencyFormatter.formatPercent(periodChangePct)}"
                                    activePoint != null ->
                                        "Selected point · ${CurrencyFormatter.formatPercent(periodChangePct)}"
                                    pts.size >= 2 && first > 0.0 ->
                                        "Drag to inspect · 2 fingers for range (${detailState.timeframe.label} ${CurrencyFormatter.formatPercent(periodChangePct)})"
                                    else ->
                                        "Drag across chart to inspect prices"
                                }
                                Text(
                                    text = subtitleText,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (isRanging || activePoint != null) rangeColor else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = CurrencyFormatter.formatAmount(shownPrice, item.holding.nativeCurrency),
                                    style = NumericTextStyles.cardAmount,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                if (rangeStartPt != null && rangeEndPt != null) {
                                    Text(
                                        text = "from ${CurrencyFormatter.formatAmount(rangeStartPt.value, item.holding.nativeCurrency)}",
                                        style = NumericTextStyles.subAmount,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            ChartTimeframe.entries.forEach { tf ->
                                FilterChip(
                                    selected = detailState.timeframe == tf,
                                    onClick = {
                                        scrubIndex = null
                                        rangeSelection = null
                                        onTimeframeSelect(tf)
                                    },
                                    label = { Text(tf.label) },
                                    modifier = Modifier.testTag("detail_tf_${tf.label}")
                                )
                            }
                        }

                        if (detailState.isLoadingChart) {
                            LinearProgressIndicator(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        InteractiveTotalMovementChart(
                            points = pts,
                            lineColor = chartColor,
                            scrubIndex = scrubIndex,
                            onScrubIndexChange = { scrubIndex = it },
                            rangeSelection = rangeSelection,
                            onRangeSelectionChange = { rangeSelection = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(155.dp)
                                .testTag("holding_detail_chart")
                        )

                        if (pts.size >= 2 && pts.first().timestampSec > 0L && pts.last().timestampSec > 0L) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = CurrencyFormatter.formatChartAxisDate(
                                        pts.first().timestampSec,
                                        detailState.timeframe
                                    ),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = CurrencyFormatter.formatChartAxisDate(
                                        pts.last().timestampSec,
                                        detailState.timeframe
                                    ),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // Breakdown Rows
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    DetailMetricRow(
                        label = "Quantity Held",
                        value = CurrencyFormatter.formatQuantity(item.holding.quantity, item.holding.unitType)
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    DetailMetricRow(
                        label = "Portfolio Weight",
                        value = "${CurrencyFormatter.formatTwoDecimals(item.portfolioWeightPercent)}%"
                    )
                    if (item.holding.assetClass != AssetClass.CASH) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        DetailMetricRow(
                            label = "Unit Price (${item.holding.nativeCurrency} / ${item.holding.unitType.shortLabel})",
                            value = CurrencyFormatter.formatAmount(item.unitQuoteNative, item.holding.nativeCurrency)
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        val isGain = item.dayChangePercent >= 0.0
                        DetailMetricRow(
                            label = "24h Change",
                            value = "${
                                CurrencyFormatter.formatCompact(
                                    item.dayChangeDisplay,
                                    displayCurrency.code,
                                    isBalanceHidden,
                                    showSign = true
                                )
                            } (${CurrencyFormatter.formatPercent(item.dayChangePercent)})",
                            valueColor = if (isGain) wealthColors.gain else wealthColors.loss
                        )
                    }
                    item.unrealizedGainDisplay?.let { gain ->
                        val isPnlGain = gain >= 0.0
                        val costBasisDisplay = item.totalValueDisplay - gain
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        DetailMetricRow(
                            label = "Total Cost Basis",
                            value = CurrencyFormatter.formatAmount(
                                costBasisDisplay,
                                displayCurrency.code,
                                isBalanceHidden
                            )
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        DetailMetricRow(
                            label = "Unrealized P&L",
                            value = "${
                                CurrencyFormatter.formatAmount(
                                    gain,
                                    displayCurrency.code,
                                    isBalanceHidden,
                                    showSign = true
                                )
                            } (${CurrencyFormatter.formatPercent(item.unrealizedGainPercent ?: 0.0)})",
                            valueColor = if (isPnlGain) wealthColors.gain else wealthColors.loss
                        )
                    }
                    if (item.holding.buyDateMillis != null) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        DetailMetricRow(
                            label = "Buy Date",
                            value = "${CurrencyFormatter.formatDate(item.holding.buyDateMillis)} (${CurrencyFormatter.formatHeldDuration(item.holding.buyDateMillis)})"
                        )
                    }
                    if (item.holding.accountLabel.isNotBlank()) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        DetailMetricRow(
                            label = "Account / Vault",
                            value = item.holding.accountLabel
                        )
                    }
                    item.marketStatus?.let { market ->
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        DetailMetricRow(
                            label = "Market State",
                            value = market.label,
                            valueColor = if (market == MarketStatus.OPEN) {
                                wealthColors.gain
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    val extSuffix = if (item.isUsingExtendedHoursPrice) " · 24h Ext" else ""
                    DetailMetricRow(
                        label = "Quote Source",
                        value = "${item.quoteStatus.name}$extSuffix · ${CurrencyFormatter.formatRelativeTime(item.quoteUpdatedAt)}"
                    )
                }
            }

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = onAddBuyClick,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("detail_add_buy_button")
                ) {
                    Icon(Icons.Filled.AddShoppingCart, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Add Buy")
                }

                Button(
                    onClick = onSellClick,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("detail_sell_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = wealthColors.loss,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Icon(Icons.Filled.RemoveShoppingCart, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Sell")
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = { showDeleteConfirm = true },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("detail_delete_button"),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Remove")
                }

                OutlinedButton(
                    onClick = onEditClick,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("detail_edit_button")
                ) {
                    Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Edit")
                }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Remove ${item.holding.symbol}?") },
            text = { Text("This holding will be removed from your local Valoris portfolio.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        onDeleteConfirm()
                    }
                ) {
                    Text("Remove", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun DetailMetricRow(
    label: String,
    value: String,
    valueColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.45f)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = value,
            style = NumericTextStyles.cardAmount,
            fontWeight = FontWeight.Medium,
            color = valueColor,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(0.55f)
        )
    }
}
