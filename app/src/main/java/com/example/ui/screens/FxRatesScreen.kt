package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.domain.model.PortfolioDashboardData
import com.example.domain.model.RefreshInterval
import com.example.domain.model.SupportedCurrency
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import com.example.ui.theme.LocalWealthColors
import com.example.ui.theme.NumericTextStyles
import com.example.util.CurrencyFormatter

private val DECIMAL_INPUT_REGEX = Regex("^\\d*\\.?\\d*$")

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun FxRatesScreen(
    dashboard: PortfolioDashboardData,
    isRefreshing: Boolean,
    onSelectDisplayCurrency: (SupportedCurrency) -> Unit,
    onSelectRefreshInterval: (RefreshInterval) -> Unit,
    onRefreshClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val wealthColors = LocalWealthColors.current
    var calcAmountText by remember { mutableStateOf("1000") }
    var calcFromCurrency by remember { mutableStateOf(SupportedCurrency.USD) }
    var showFromMenu by remember { mutableStateOf(false) }

    val ratesPerUsd = remember(dashboard.fxRatesList, dashboard.displayCurrency, dashboard.usdToDisplayRate) {
        buildMap {
            SupportedCurrency.entries.forEach { put(it.code, CurrencyFormatter.defaultFallbackRatePerUsd(it.code)) }
            put("USD", 1.0)
            put(dashboard.displayCurrency.code, dashboard.usdToDisplayRate)
            dashboard.fxRatesList.forEach { fx ->
                if (fx.rateInBaseCurrency > 0.0 && dashboard.usdToDisplayRate > 0.0) {
                    // rateInBaseCurrency = (1 FX in Display), usdToDisplay = (1 USD in Display)
                    // => 1 USD in FX = usdToDisplay / rateInBaseCurrency
                    put(fx.currencyCode, dashboard.usdToDisplayRate / fx.rateInBaseCurrency)
                }
            }
        }
    }

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefreshClick,
        modifier = modifier.fillMaxSize()
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
        item(key = "fx_header", contentType = "header") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 760.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "FX & Preferences",
                        style = MaterialTheme.typography.displayMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "Source: ${dashboard.fxSourceLabel}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                TextButton(
                    onClick = onRefreshClick,
                    enabled = !isRefreshing,
                    modifier = Modifier.testTag("fx_refresh_button")
                ) {
                    Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (isRefreshing) "Syncing" else "Refresh")
                }
            }
        }

        // Preferred Display Currency
        item(key = "fx_display_currency", contentType = "settings_card") {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 760.dp),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = "Preferred Display Currency",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "All stocks, ETFs, cash, and commodities auto-convert into this currency.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SupportedCurrency.entries.forEach { currency ->
                            val selected = dashboard.displayCurrency == currency
                            FilterChip(
                                selected = selected,
                                onClick = { onSelectDisplayCurrency(currency) },
                                label = { Text("${currency.code} (${currency.symbol.trim()})") },
                                leadingIcon = if (selected) {
                                    { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                } else null,
                                modifier = Modifier.testTag("base_currency_${currency.code}")
                            )
                        }
                    }
                }
            }
        }

        // Inline Live Currency Converter
        item(key = "fx_calculator", contentType = "calculator_card") {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 760.dp),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = "Quick FX Calculator",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = calcAmountText,
                            onValueChange = { input ->
                                if (input.isEmpty() || input.matches(DECIMAL_INPUT_REGEX)) {
                                    calcAmountText = input
                                }
                            },
                            label = { Text("Amount") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("fx_calc_input")
                        )

                        Column {
                            OutlinedButton(
                                onClick = { showFromMenu = true },
                                modifier = Modifier.testTag("fx_calc_from_button")
                            ) {
                                Text(calcFromCurrency.code)
                            }
                            DropdownMenu(
                                expanded = showFromMenu,
                                onDismissRequest = { showFromMenu = false }
                            ) {
                                SupportedCurrency.entries.forEach { cur ->
                                    DropdownMenuItem(
                                        text = { Text("${cur.code} · ${cur.displayName}") },
                                        onClick = {
                                            calcFromCurrency = cur
                                            showFromMenu = false
                                        }
                                    )
                                }
                            }
                        }

                        IconButton(
                            onClick = {
                                val previousBase = dashboard.displayCurrency
                                onSelectDisplayCurrency(calcFromCurrency)
                                calcFromCurrency = previousBase
                            }
                        ) {
                            Icon(Icons.Filled.SwapHoriz, contentDescription = "Swap currencies")
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    val amountVal = calcAmountText.toDoubleOrNull() ?: 0.0
                    val converted = CurrencyFormatter.convert(
                        amount = amountVal,
                        fromCurrency = calcFromCurrency.code,
                        toCurrency = dashboard.displayCurrency.code,
                        ratesPerUsd = ratesPerUsd
                    )

                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Converted (${dashboard.displayCurrency.code})",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = CurrencyFormatter.formatAmount(converted, dashboard.displayCurrency.code),
                                style = NumericTextStyles.cardAmount,
                                fontWeight = FontWeight.Bold,
                                color = wealthColors.goldAccent
                            )
                        }
                    }
                }
            }
        }

        // Auto-Refresh Interval
        item(key = "fx_refresh_interval", contentType = "settings_card") {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 760.dp),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = "Live Quote Auto-Refresh",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        RefreshInterval.entries.forEach { interval ->
                            val selected = dashboard.refreshInterval == interval
                            FilterChip(
                                selected = selected,
                                onClick = { onSelectRefreshInterval(interval) },
                                label = { Text(interval.label) },
                                modifier = Modifier.testTag("refresh_interval_${interval.minutes}")
                            )
                        }
                    }
                }
            }
        }

        // Live FX Rate Table
        item(key = "fx_rates_header", contentType = "section_header") {
            Text(
                text = "Live Rates -> ${dashboard.displayCurrency.code}",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 760.dp)
            )
        }

        items(
            items = dashboard.fxRatesList,
            key = { it.currencyCode },
            contentType = { "fx_rate_row" }
        ) { fx ->
            val formattedRate = remember(fx.rateInBaseCurrency, dashboard.displayCurrency.code) {
                CurrencyFormatter.formatAmount(fx.rateInBaseCurrency, dashboard.displayCurrency.code)
            }
            val relativeTime = remember(fx.fetchedAt) {
                CurrencyFormatter.formatRelativeTime(fx.fetchedAt)
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
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "1 ${fx.currencyCode}",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = fx.currencyName,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = formattedRate,
                            style = NumericTextStyles.cardAmount,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = relativeTime,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        item(key = "fx_bottom_spacer", contentType = "spacer") { Spacer(modifier = Modifier.height(72.dp)) }
    }
    }
}
