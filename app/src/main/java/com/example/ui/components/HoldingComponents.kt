package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Diamond
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.domain.model.AssetClass
import com.example.domain.model.MarketStatus
import com.example.domain.model.QuoteStatus
import com.example.domain.model.SupportedCurrency
import com.example.domain.model.ValuedHolding
import com.example.ui.theme.LocalWealthColors
import com.example.ui.theme.NumericTextStyles
import com.example.util.CurrencyFormatter

@Composable
fun HoldingLedgerCard(
    item: ValuedHolding,
    displayCurrency: SupportedCurrency,
    isBalanceHidden: Boolean,
    useCompactNumbers: Boolean = false,
    showPnlOnCards: Boolean = true,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val wealthColors = LocalWealthColors.current
    val accent = colorForAssetClass(item.holding.assetClass, wealthColors)
    val isGain = item.dayChangePercent >= 0.0
    val changeColor = if (isGain) wealthColors.gain else wealthColors.loss
    val changeBg = if (isGain) wealthColors.gainContainer else wealthColors.lossContainer
    val holding = item.holding
    val isCash = holding.assetClass == AssetClass.CASH

    val qtyLine = remember(
        isCash,
        holding.nativeCurrency,
        holding.accountLabel,
        holding.quantity,
        holding.unitType,
        item.unitQuoteNative
    ) {
        if (isCash) {
            val accountSuffix = if (holding.accountLabel.isNotBlank()) " · ${holding.accountLabel}" else ""
            "${holding.nativeCurrency} Reserve$accountSuffix"
        } else {
            val unitPriceText = CurrencyFormatter.formatAmount(
                amount = item.unitQuoteNative,
                currencyCode = holding.nativeCurrency,
                isHidden = false
            )
            "${CurrencyFormatter.formatQuantity(holding.quantity, holding.unitType)} · $unitPriceText/${holding.unitType.shortLabel}"
        }
    }

    val totalDisplayText = remember(
        item.totalValueDisplay,
        displayCurrency.code,
        isBalanceHidden,
        useCompactNumbers
    ) {
        CurrencyFormatter.formatPreferenceAmount(
            amount = item.totalValueDisplay,
            currencyCode = displayCurrency.code,
            isHidden = isBalanceHidden,
            useCompact = useCompactNumbers
        )
    }

    val dayChangeText = remember(isCash, item.dayChangePercent) {
        if (isCash) "" else CurrencyFormatter.formatPercent(item.dayChangePercent)
    }

    val pnlDisplayText = remember(
        showPnlOnCards,
        isCash,
        item.unrealizedGainDisplay,
        item.unrealizedGainPercent,
        displayCurrency.code,
        isBalanceHidden
    ) {
        val gain = item.unrealizedGainDisplay
        if (!isCash && showPnlOnCards && gain != null) {
            val pnlAmountText = CurrencyFormatter.formatCompact(
                amount = gain,
                currencyCode = displayCurrency.code,
                isHidden = isBalanceHidden,
                showSign = true
            )
            val pnlPctText = CurrencyFormatter.formatPercent(item.unrealizedGainPercent ?: 0.0)
            "P&L $pnlAmountText ($pnlPctText)"
        } else {
            null
        }
    }

    val cashWeightText = remember(isCash, item.portfolioWeightPercent) {
        if (isCash) "${CurrencyFormatter.formatOneDecimal(item.portfolioWeightPercent)}% weight" else ""
    }

    Surface(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .testTag("holding_card_${holding.symbol}"),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(accent.copy(alpha = 0.14f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = iconForAssetClass(holding.assetClass),
                    contentDescription = holding.assetClass.singularName,
                    tint = accent,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = holding.symbol,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    AssetClassPill(
                        label = holding.assetClass.singularName,
                        color = accent
                    )
                    if (item.quoteStatus == QuoteStatus.CACHED) {
                        Text(
                            text = "CACHED",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = holding.name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = qtyLine,
                    style = NumericTextStyles.subAmount,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                item.marketStatus?.let { market ->
                    Spacer(modifier = Modifier.height(5.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        MarketStateBadge(marketStatus = market)
                        if (item.isUsingExtendedHoursPrice) {
                            ExtendedHoursBadge()
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = totalDisplayText,
                    style = NumericTextStyles.cardAmount,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    softWrap = false
                )

                Spacer(modifier = Modifier.height(4.dp))

                if (!isCash) {
                    Row(
                        modifier = Modifier
                            .background(changeBg, BadgeShape8)
                            .padding(horizontal = 7.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Icon(
                            imageVector = if (isGain) Icons.AutoMirrored.Filled.TrendingUp else Icons.AutoMirrored.Filled.TrendingDown,
                            contentDescription = null,
                            tint = changeColor,
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = dayChangeText,
                            style = NumericTextStyles.badge,
                            color = changeColor,
                            maxLines = 1,
                            softWrap = false
                        )
                    }

                    if (pnlDisplayText != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        val isPnlGain = (item.unrealizedGainDisplay ?: 0.0) >= 0.0
                        val pnlColor = if (isPnlGain) wealthColors.gain else wealthColors.loss
                        Text(
                            text = pnlDisplayText,
                            style = NumericTextStyles.subAmount,
                            color = pnlColor,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                } else {
                    Text(
                        text = cashWeightText,
                        style = NumericTextStyles.subAmount,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }
    }
}

private val BadgeShape6 = RoundedCornerShape(6.dp)
private val BadgeShape8 = RoundedCornerShape(8.dp)

@Composable
fun AssetClassPill(label: String, color: Color) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = color,
        maxLines = 1,
        softWrap = false,
        modifier = Modifier
            .background(color.copy(alpha = 0.12f), BadgeShape6)
            .padding(horizontal = 6.dp, vertical = 2.dp)
    )
}

@Composable
fun MarketStateBadge(
    marketStatus: MarketStatus,
    modifier: Modifier = Modifier
) {
    val wealthColors = LocalWealthColors.current
    val isOpen = marketStatus == MarketStatus.OPEN
    val dotColor = if (isOpen) wealthColors.gain else MaterialTheme.colorScheme.onSurfaceVariant
    val containerColor = if (isOpen) {
        wealthColors.gainContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.75f)
    }
    val textColor = if (isOpen) wealthColors.gain else MaterialTheme.colorScheme.onSurfaceVariant

    Row(
        modifier = modifier
            .background(containerColor, BadgeShape6)
            .padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .background(dotColor, CircleShape)
        )
        Text(
            text = "Market ${marketStatus.label}",
            style = MaterialTheme.typography.labelSmall,
            color = textColor,
            maxLines = 1,
            softWrap = false
        )
    }
}

@Composable
fun ExtendedHoursBadge(modifier: Modifier = Modifier) {
    val wealthColors = LocalWealthColors.current
    Text(
        text = "24h Ext",
        style = MaterialTheme.typography.labelSmall,
        color = wealthColors.goldAccent,
        maxLines = 1,
        softWrap = false,
        modifier = modifier
            .background(wealthColors.goldAccent.copy(alpha = 0.16f), BadgeShape6)
            .padding(horizontal = 6.dp, vertical = 2.dp)
    )
}

@Composable
fun SyncStatusBadge(
    isRefreshing: Boolean,
    hasStaleQuotes: Boolean,
    lastSyncEpoch: Long?
) {
    val wealthColors = LocalWealthColors.current
    val (dotColor, text) = when {
        isRefreshing -> wealthColors.goldAccent to "Syncing..."
        lastSyncEpoch == null -> MaterialTheme.colorScheme.onSurfaceVariant to "Offline ready"
        hasStaleQuotes -> wealthColors.goldAccent to "Cached · ${CurrencyFormatter.formatRelativeTime(lastSyncEpoch)}"
        else -> wealthColors.gain to "Live · ${CurrencyFormatter.formatRelativeTime(lastSyncEpoch)}"
    }

    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(dotColor)
            )
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

fun iconForAssetClass(assetClass: AssetClass): ImageVector = when (assetClass) {
    AssetClass.STOCK -> Icons.AutoMirrored.Filled.ShowChart
    AssetClass.ETF -> Icons.Filled.PieChart
    AssetClass.CASH -> Icons.Filled.AccountBalanceWallet
    AssetClass.COMMODITY -> Icons.Filled.Diamond
}
