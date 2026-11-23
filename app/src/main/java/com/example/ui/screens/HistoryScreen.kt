package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.domain.model.AssetClass
import com.example.domain.model.HistoryFilterType
import com.example.domain.model.PortfolioDashboardData
import com.example.domain.model.PortfolioHistorySummary
import com.example.domain.model.TransactionDisplayItem
import com.example.domain.model.TransactionType
import com.example.ui.theme.LocalWealthColors
import com.example.ui.theme.NumericTextStyles
import com.example.util.CurrencyFormatter

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HistoryScreen(
    dashboard: PortfolioDashboardData,
    historySummary: PortfolioHistorySummary,
    selectedFilter: HistoryFilterType,
    searchQuery: String,
    onFilterSelect: (HistoryFilterType) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onDeleteTransaction: (transactionId: Long, symbol: String) -> Unit,
    onAddHoldingClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val wealthColors = LocalWealthColors.current
    var pendingDelete by remember { mutableStateOf<TransactionDisplayItem?>(null) }

    val filteredItems = remember(historySummary.items, selectedFilter, searchQuery) {
        val q = searchQuery.trim()
        historySummary.items.filter { item ->
            val tx = item.transaction
            val matchesType = when (selectedFilter) {
                HistoryFilterType.ALL -> true
                HistoryFilterType.BUY -> tx.type == TransactionType.BUY
                HistoryFilterType.SELL -> tx.type == TransactionType.SELL
            }
            val matchesQuery = q.isEmpty() ||
                tx.symbol.contains(q, ignoreCase = true) ||
                tx.assetName.contains(q, ignoreCase = true) ||
                tx.note.contains(q, ignoreCase = true)
            matchesType && matchesQuery
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item(key = "history_header", contentType = "header") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 760.dp)
            ) {
                Text(
                    text = "Trade History",
                    style = MaterialTheme.typography.displayMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "Buy & sell ledger (${dashboard.displayCurrency.code})",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (historySummary.items.isEmpty()) {
            item(key = "history_empty", contentType = "empty_card") {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 760.dp)
                        .testTag("empty_history_card"),
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ReceiptLong,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(40.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "No transactions recorded yet",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Every Buy and Sell action you log is saved here and automatically drives your Asset Value and Profit & Loss movement graphs.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = onAddHoldingClick) {
                            Text("Add Holding or Buy")
                        }
                    }
                }
            }
            return@LazyColumn
        }

        // Trade Summary Card
        item(key = "history_summary", contentType = "summary_card") {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 760.dp)
                    .testTag("history_summary_card"),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "CUMULATIVE TRADE SUMMARY",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Total Bought",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = CurrencyFormatter.formatPreferenceAmount(
                                    amount = historySummary.totalInvestedDisplay,
                                    currencyCode = dashboard.displayCurrency.code,
                                    isHidden = dashboard.isBalanceHidden,
                                    useCompact = true
                                ),
                                style = NumericTextStyles.cardAmount,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Total Sold",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = CurrencyFormatter.formatPreferenceAmount(
                                    amount = historySummary.totalProceedsDisplay,
                                    currencyCode = dashboard.displayCurrency.code,
                                    isHidden = dashboard.isBalanceHidden,
                                    useCompact = true
                                ),
                                style = NumericTextStyles.cardAmount,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.End
                        ) {
                            val realized = historySummary.totalRealizedPnlDisplay
                            val isGain = realized >= 0.0
                            val realizedColor = if (isGain) wealthColors.gain else wealthColors.loss
                            Text(
                                text = "Realized P&L",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = CurrencyFormatter.formatPreferenceAmount(
                                    amount = realized,
                                    currencyCode = dashboard.displayCurrency.code,
                                    isHidden = dashboard.isBalanceHidden,
                                    useCompact = true,
                                    showSign = true
                                ),
                                style = NumericTextStyles.cardAmount,
                                fontWeight = FontWeight.Bold,
                                color = realizedColor,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }
            }
        }

        // Filter Chips + Search
        item(key = "history_controls", contentType = "controls") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 760.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    placeholder = { Text("Search ticker, asset, or note") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { onSearchQueryChange("") }) {
                                Icon(Icons.Filled.Close, contentDescription = "Clear search")
                            }
                        }
                    },
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("history_search_input")
                )

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    HistoryFilterType.entries.forEach { filter ->
                        val count = when (filter) {
                            HistoryFilterType.ALL -> historySummary.items.size
                            HistoryFilterType.BUY -> historySummary.buyCount
                            HistoryFilterType.SELL -> historySummary.sellCount
                        }
                        FilterChip(
                            selected = selectedFilter == filter,
                            onClick = { onFilterSelect(filter) },
                            label = { Text("${filter.label} ($count)") },
                            modifier = Modifier.testTag("history_filter_${filter.name.lowercase()}")
                        )
                    }
                }
            }
        }

        if (filteredItems.isEmpty()) {
            item(key = "history_no_matches", contentType = "empty_card") {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 760.dp),
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "No matching trades",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Try switching the Buy/Sell filter or clearing the search box.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(
                items = filteredItems,
                key = { it.transaction.id },
                contentType = { "transaction_card" }
            ) { item ->
                val handleDelete = remember(item) { { pendingDelete = item } }
                TransactionHistoryCard(
                    item = item,
                    displayCurrencyCode = dashboard.displayCurrency.code,
                    isBalanceHidden = dashboard.isBalanceHidden,
                    onDeleteClick = handleDelete,
                    modifier = Modifier.widthIn(max = 760.dp)
                )
            }
        }

        item(key = "history_bottom_spacer", contentType = "spacer") { Spacer(modifier = Modifier.height(72.dp)) }
    }

    pendingDelete?.let { item ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete ${item.transaction.type.label} Record?") },
            text = {
                val tx = item.transaction
                val isCash = tx.assetClass == AssetClass.CASH
                val actionLabel = when {
                    isCash && tx.type == TransactionType.BUY -> "deposit"
                    isCash -> "withdrawal"
                    else -> tx.type.label.lowercase()
                }
                val cashNote = if (tx.type == TransactionType.SELL && tx.depositedToCash) {
                    ", settled cash proceeds,"
                } else {
                    ""
                }
                Text(
                    "Remove this ${tx.symbol} $actionLabel (${CurrencyFormatter.formatDate(tx.timestampMillis)}) record? Your ${tx.symbol} holding balance$cashNote and movement graphs will be recalculated."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteTransaction(item.transaction.id, item.transaction.symbol)
                        pendingDelete = null
                    },
                    modifier = Modifier.testTag("confirm_delete_tx_button")
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

private val HistoryBadgeShape8 = RoundedCornerShape(8.dp)
private val HistoryBadgeShape6 = RoundedCornerShape(6.dp)

@Composable
private fun TransactionHistoryCard(
    item: TransactionDisplayItem,
    displayCurrencyCode: String,
    isBalanceHidden: Boolean,
    onDeleteClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val wealthColors = LocalWealthColors.current
    val tx = item.transaction
    val isBuy = tx.type == TransactionType.BUY
    val isCash = tx.assetClass == AssetClass.CASH
    val typeColor = if (isBuy) wealthColors.gain else wealthColors.loss
    val typeBg = if (isBuy) wealthColors.gainContainer else wealthColors.lossContainer

    val primaryAmountText = remember(
        tx.unitPriceNative,
        isCash,
        item.totalAmountDisplay,
        displayCurrencyCode,
        isBalanceHidden
    ) {
        if (tx.unitPriceNative > 0.0 || isCash) {
            CurrencyFormatter.formatAmount(
                amount = item.totalAmountDisplay,
                currencyCode = displayCurrencyCode,
                isHidden = isBalanceHidden
            )
        } else {
            "Cost not set"
        }
    }

    val nativeAmountText = remember(
        tx.nativeCurrency,
        displayCurrencyCode,
        tx.unitPriceNative,
        isCash,
        tx.totalAmountNative,
        isBalanceHidden
    ) {
        if (tx.nativeCurrency != displayCurrencyCode && (tx.unitPriceNative > 0.0 || isCash)) {
            CurrencyFormatter.formatAmount(
                amount = tx.totalAmountNative,
                currencyCode = tx.nativeCurrency,
                isHidden = isBalanceHidden
            )
        } else {
            null
        }
    }

    val qtyPriceDateLine = remember(
        isCash,
        tx.quantity,
        tx.nativeCurrency,
        tx.unitPriceNative,
        tx.unitType,
        tx.timestampMillis,
        isBalanceHidden
    ) {
        val qtyPriceLabel = when {
            isCash -> CurrencyFormatter.formatAmount(tx.quantity, tx.nativeCurrency, isHidden = isBalanceHidden)
            tx.unitPriceNative > 0.0 -> "${CurrencyFormatter.formatQuantity(tx.quantity, tx.unitType)} × ${
                CurrencyFormatter.formatAmount(tx.unitPriceNative, tx.nativeCurrency, isHidden = isBalanceHidden)
            }"
            else -> CurrencyFormatter.formatQuantity(tx.quantity, tx.unitType)
        }
        "$qtyPriceLabel · ${CurrencyFormatter.formatDate(tx.timestampMillis)}"
    }

    val pnlBadgeText = remember(item.realizedPnlDisplay, displayCurrencyCode, isBalanceHidden) {
        item.realizedPnlDisplay?.let { pnlDisplay ->
            "P&L ${
                CurrencyFormatter.formatAmount(
                    amount = pnlDisplay,
                    currencyCode = displayCurrencyCode,
                    isHidden = isBalanceHidden,
                    showSign = true
                )
            }"
        }
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("tx_card_${tx.id}"),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = when {
                            isCash && isBuy -> "DEPOSIT"
                            isCash && !isBuy -> "WITHDRAW"
                            isBuy -> "BUY"
                            else -> "SELL"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = typeColor,
                        modifier = Modifier
                            .background(typeBg, HistoryBadgeShape8)
                            .padding(horizontal = 9.dp, vertical = 4.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = tx.symbol,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = tx.assetName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = primaryAmountText,
                            style = NumericTextStyles.cardAmount,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            softWrap = false
                        )
                        if (nativeAmountText != null) {
                            Text(
                                text = nativeAmountText,
                                style = NumericTextStyles.subAmount,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    IconButton(
                        onClick = onDeleteClick,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("delete_tx_${tx.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.DeleteOutline,
                            contentDescription = "Delete transaction",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = qtyPriceDateLine,
                    style = NumericTextStyles.subAmount,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (pnlBadgeText != null || tx.depositedToCash) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (pnlBadgeText != null) {
                            val pnlGain = (item.realizedPnlDisplay ?: 0.0) >= 0.0
                            val pnlColor = if (pnlGain) wealthColors.gain else wealthColors.loss
                            val pnlBg = if (pnlGain) wealthColors.gainContainer else wealthColors.lossContainer
                            Text(
                                text = pnlBadgeText,
                                style = NumericTextStyles.badge,
                                color = pnlColor,
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier
                                    .background(pnlBg, HistoryBadgeShape6)
                                    .padding(horizontal = 7.dp, vertical = 2.dp)
                            )
                        }

                        if (tx.depositedToCash) {
                            Row(
                                modifier = Modifier
                                    .background(MaterialTheme.colorScheme.secondaryContainer, HistoryBadgeShape6)
                                    .padding(horizontal = 7.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.AccountBalanceWallet,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.size(12.dp)
                                )
                                Text(
                                    text = "Settled to Cash",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
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
}
