package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.domain.model.AssetClass
import com.example.domain.model.PortfolioDashboardData
import com.example.domain.model.SortOption
import com.example.domain.model.ValuedHolding
import com.example.ui.components.HoldingLedgerCard
import com.example.ui.theme.NumericTextStyles
import com.example.util.CurrencyFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HoldingsScreen(
    dashboard: PortfolioDashboardData,
    filteredHoldings: List<ValuedHolding>,
    selectedFilter: AssetClass?,
    sortOption: SortOption,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onFilterSelect: (AssetClass?) -> Unit,
    onSortSelect: (SortOption) -> Unit,
    onHoldingClick: (ValuedHolding) -> Unit,
    onAddHoldingClick: () -> Unit,
    modifier: Modifier = Modifier,
    isRefreshing: Boolean = false,
    onRefresh: () -> Unit = {}
) {
    var showSortMenu by remember { mutableStateOf(false) }
    val filteredTotal = remember(filteredHoldings) {
        filteredHoldings.sumOf { it.totalValueDisplay }
    }

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        modifier = modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 760.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Holdings Ledger",
                        style = MaterialTheme.typography.displayMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "${filteredHoldings.size} positions · ${
                            CurrencyFormatter.formatPreferenceAmount(
                                amount = filteredTotal,
                                currencyCode = dashboard.displayCurrency.code,
                                isHidden = dashboard.isBalanceHidden,
                                useCompact = dashboard.useCompactNumbers
                            )
                        }",
                        style = NumericTextStyles.subAmount,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Box {
                    FilterChip(
                        selected = false,
                        onClick = { showSortMenu = true },
                        label = { Text(sortOption.label) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Sort,
                                contentDescription = "Sort holdings",
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        modifier = Modifier.testTag("sort_holdings_chip")
                    )
                    DropdownMenu(
                        expanded = showSortMenu,
                        onDismissRequest = { showSortMenu = false }
                    ) {
                        SortOption.entries.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option.label) },
                                onClick = {
                                    onSortSelect(option)
                                    showSortMenu = false
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 760.dp)
                    .testTag("holdings_search_input"),
                placeholder = { Text("Filter by ticker, asset name, or account...") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onSearchQueryChange("") }) {
                            Icon(Icons.Filled.Close, contentDescription = "Clear search")
                        }
                    }
                },
                singleLine = true,
                shape = MaterialTheme.shapes.medium
            )

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 760.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selectedFilter == null,
                    onClick = { onFilterSelect(null) },
                    label = { Text("All") },
                    modifier = Modifier.testTag("filter_all")
                )
                AssetClass.entries.forEach { assetClass ->
                    FilterChip(
                        selected = selectedFilter == assetClass,
                        onClick = { onFilterSelect(if (selectedFilter == assetClass) null else assetClass) },
                        label = { Text(assetClass.displayName) },
                        modifier = Modifier.testTag("filter_${assetClass.name.lowercase()}")
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 760.dp)
                    .weight(1f),
                contentPadding = PaddingValues(bottom = 88.dp, top = 4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (filteredHoldings.isEmpty()) {
                    item(key = "empty_holdings_card") {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .widthIn(max = 760.dp)
                                .padding(top = 24.dp),
                            shape = MaterialTheme.shapes.large,
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Column(
                                modifier = Modifier.padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = if (dashboard.valuedHoldings.isEmpty()) "No holdings recorded yet" else "No matching holdings",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = if (dashboard.valuedHoldings.isEmpty()) {
                                        "Add stocks, ETFs, cash reserves, or commodities to start tracking live valuations in ${dashboard.displayCurrency.code}."
                                    } else {
                                        "Try clearing your search or selecting another asset class."
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Button(
                                    onClick = onAddHoldingClick,
                                    modifier = Modifier.testTag("holdings_empty_add_button")
                                ) {
                                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Text("  Add Holding")
                                }
                            }
                        }
                    }
                } else {
                    items(
                        items = filteredHoldings,
                        key = { it.holding.id },
                        contentType = { "holding_card" }
                    ) { item ->
                        val handleClick = remember(item, onHoldingClick) { { onHoldingClick(item) } }
                        HoldingLedgerCard(
                            item = item,
                            displayCurrency = dashboard.displayCurrency,
                            isBalanceHidden = dashboard.isBalanceHidden,
                            useCompactNumbers = dashboard.useCompactNumbers,
                            showPnlOnCards = dashboard.showPnlOnCards,
                            onClick = handleClick
                        )
                    }
                }
            }
        }
    }
}
