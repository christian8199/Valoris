package com.example.widget

import androidx.compose.runtime.Immutable
import com.example.domain.model.PortfolioDashboardData
import com.example.domain.model.PortfolioMovementData
import com.example.domain.model.WidgetDisplayMetric
import com.example.util.CurrencyFormatter

@Immutable
data class ValorisWidgetSnapshot(
    val isEmpty: Boolean,
    val metricMode: WidgetDisplayMetric,
    val rangeLabel: String,
    val eyebrowLabel: String,
    val currencyCode: String,
    val isBalanceHidden: Boolean,
    val formattedPrimaryValue: String,
    val formattedSecondaryDelta: String,
    val formattedCompactDelta: String,
    val isPositive: Boolean,
    val isNegative: Boolean,
    val syncStatusLabel: String,
    val chartPoints: List<Double>
)

fun buildWidgetSnapshot(
    dashboard: PortfolioDashboardData,
    movement: PortfolioMovementData? = null,
    nowMillis: Long = System.currentTimeMillis()
): ValorisWidgetSnapshot {
    val currencyCode = dashboard.displayCurrency.code
    val isHidden = dashboard.isBalanceHidden
    val metric = dashboard.widgetDisplayMetric
    val range = dashboard.widgetChartTimeframe
    val isEmpty = dashboard.valuedHoldings.isEmpty()
    val syncLabel = CurrencyFormatter.formatRelativeTime(dashboard.lastSyncEpoch, nowMs = nowMillis)

    if (isEmpty) {
        return ValorisWidgetSnapshot(
            isEmpty = true,
            metricMode = metric,
            rangeLabel = range.label,
            eyebrowLabel = metric.widgetTitle,
            currencyCode = currencyCode,
            isBalanceHidden = isHidden,
            formattedPrimaryValue = "Add Asset",
            formattedSecondaryDelta = "Tap to track portfolio",
            formattedCompactDelta = "Tap to start",
            isPositive = false,
            isNegative = false,
            syncStatusLabel = syncLabel,
            chartPoints = emptyList()
        )
    }

    return when (metric) {
        WidgetDisplayMetric.ASSET_VALUE -> {
            val primaryText = CurrencyFormatter.formatPreferenceAmount(
                amount = dashboard.totalValueDisplay,
                currencyCode = currencyCode,
                isHidden = isHidden,
                useCompact = dashboard.useCompactNumbers
            )

            val hasTimeline = movement != null && movement.totalTimeline.size >= 2
            val deltaAmt = if (hasTimeline) movement!!.periodChangeDisplay else dashboard.dayChangeDisplay
            val deltaPct = if (hasTimeline) movement!!.periodChangePercent else dashboard.dayChangePercent
            val periodTag = if (hasTimeline) range.label else "Today"

            val pctStr = CurrencyFormatter.formatPercent(deltaPct, showSign = true)
            val secondaryText = if (isHidden) {
                "Hidden · $pctStr · $periodTag"
            } else {
                val amtStr = CurrencyFormatter.formatCompact(
                    amount = deltaAmt,
                    currencyCode = currencyCode,
                    isHidden = false,
                    showSign = true
                )
                "$amtStr ($pctStr) · $periodTag"
            }

            val chartPts = if (hasTimeline) {
                movement!!.totalTimeline.map { it.value }
            } else {
                dashboard.trendValuesDisplay
            }

            ValorisWidgetSnapshot(
                isEmpty = false,
                metricMode = metric,
                rangeLabel = range.label,
                eyebrowLabel = metric.widgetTitle,
                currencyCode = currencyCode,
                isBalanceHidden = isHidden,
                formattedPrimaryValue = primaryText,
                formattedSecondaryDelta = secondaryText,
                formattedCompactDelta = "$pctStr · $periodTag",
                isPositive = deltaAmt > 0.005 || deltaPct > 0.005,
                isNegative = deltaAmt < -0.005 || deltaPct < -0.005,
                syncStatusLabel = syncLabel,
                chartPoints = chartPts
            )
        }

        WidgetDisplayMetric.TOTAL_RETURN -> {
            val hasPnlTimeline = movement != null && movement.pnlTimeline.size >= 2
            val returnAmt = if (hasPnlTimeline) {
                movement!!.latestTotalReturnDisplay
            } else {
                dashboard.totalUnrealizedGainDisplay ?: 0.0
            }
            val returnPct = if (hasPnlTimeline) {
                movement!!.latestReturnPercent
            } else {
                dashboard.totalUnrealizedGainPercent ?: 0.0
            }

            val primaryText = CurrencyFormatter.formatPreferenceAmount(
                amount = returnAmt,
                currencyCode = currencyCode,
                isHidden = isHidden,
                useCompact = dashboard.useCompactNumbers,
                showSign = true
            )
            val pctStr = CurrencyFormatter.formatPercent(returnPct, showSign = true)
            val secondaryText = if (isHidden) {
                "Hidden · $pctStr · ${range.label}"
            } else {
                "$pctStr overall · ${range.label}"
            }

            val chartPts = when {
                hasPnlTimeline -> movement!!.pnlTimeline.map { it.totalReturnDisplay }
                returnAmt != 0.0 -> listOf(0.0, returnAmt)
                else -> emptyList()
            }

            ValorisWidgetSnapshot(
                isEmpty = false,
                metricMode = metric,
                rangeLabel = range.label,
                eyebrowLabel = metric.widgetTitle,
                currencyCode = currencyCode,
                isBalanceHidden = isHidden,
                formattedPrimaryValue = primaryText,
                formattedSecondaryDelta = secondaryText,
                formattedCompactDelta = "$pctStr · ${range.label}",
                isPositive = returnAmt > 0.005 || returnPct > 0.005,
                isNegative = returnAmt < -0.005 || returnPct < -0.005,
                syncStatusLabel = syncLabel,
                chartPoints = chartPts
            )
        }
    }
}
