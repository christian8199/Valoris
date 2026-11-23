package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.domain.model.AssetClass
import com.example.domain.model.Holding
import com.example.domain.model.SearchSuggestion
import com.example.domain.model.SupportedCurrency
import com.example.domain.model.UnitType
import com.example.domain.model.ValuedHolding
import com.example.util.CuratedAssets
import com.example.util.CurrencyFormatter

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddEditHoldingSheet(
    existingHolding: Holding?,
    existingHoldings: List<ValuedHolding>,
    searchSuggestions: List<SearchSuggestion>,
    isSearchingSymbol: Boolean,
    onSearchSymbolChange: (String) -> Unit,
    onAddBuyToExisting: (ValuedHolding) -> Unit,
    onSave: (Holding) -> Unit,
    onDismiss: () -> Unit
) {
    BackHandler(onBack = onDismiss)

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val isEditing = existingHolding != null

    var assetClass by remember(existingHolding) {
        mutableStateOf(existingHolding?.assetClass ?: AssetClass.STOCK)
    }
    var symbol by remember(existingHolding) {
        mutableStateOf(existingHolding?.symbol ?: "")
    }
    var assetName by remember(existingHolding) {
        mutableStateOf(existingHolding?.name ?: "")
    }
    var nativeCurrency by remember(existingHolding) {
        mutableStateOf(existingHolding?.nativeCurrency ?: "USD")
    }
    var unitType by remember(existingHolding) {
        mutableStateOf(existingHolding?.unitType ?: UnitType.SHARES)
    }
    var quantityText by remember(existingHolding) {
        mutableStateOf(existingHolding?.quantity?.let { formatPlainNumber(it) } ?: "")
    }
    var avgCostText by remember(existingHolding) {
        mutableStateOf(existingHolding?.averageCostPerUnit?.let { formatPlainNumber(it) } ?: "")
    }
    var manualPriceText by remember(existingHolding) {
        mutableStateOf(existingHolding?.manualPriceOverride?.let { formatPlainNumber(it) } ?: "")
    }
    var accountLabel by remember(existingHolding) {
        mutableStateOf(existingHolding?.accountLabel ?: "")
    }
    var buyDateMillis by remember(existingHolding) {
        mutableStateOf(existingHolding?.buyDateMillis)
    }
    var showDatePicker by remember { mutableStateOf(false) }
    var validationError by remember { mutableStateOf<String?>(null) }

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
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isEditing) "Edit Holding" else "Add Holding",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = "Close")
                }
            }

            if (!isEditing && existingHoldings.isNotEmpty()) {
                val matchedExisting = remember(symbol, existingHoldings, assetClass) {
                    val clean = symbol.trim()
                    if (clean.isBlank()) null
                    else existingHoldings.firstOrNull {
                        it.holding.symbol.equals(clean, ignoreCase = true)
                    }
                }
                if (matchedExisting != null) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("merge_existing_holding_banner")
                            .clickable { onAddBuyToExisting(matchedExisting) },
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.4f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Already owned: ${matchedExisting.holding.symbol}",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                                Text(
                                    text = "Currently ${
                                        CurrencyFormatter.formatQuantity(
                                            matchedExisting.holding.quantity,
                                            matchedExisting.holding.unitType
                                        )
                                    } · Tap to add new buy",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                            Text(
                                text = "Add Buy ->",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "Add Buy to Owned Asset",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            existingHoldings.take(6).forEach { owned ->
                                AssistChip(
                                    onClick = { onAddBuyToExisting(owned) },
                                    label = { Text("+ Buy ${owned.holding.symbol}") },
                                    modifier = Modifier.testTag("quick_buy_${owned.holding.symbol}")
                                )
                            }
                        }
                    }
                }
            }

            // Asset Class Selector
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Asset Class",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AssetClass.entries.forEach { cls ->
                        FilterChip(
                            selected = assetClass == cls,
                            onClick = {
                                assetClass = cls
                                validationError = null
                                when (cls) {
                                    AssetClass.CASH -> {
                                        unitType = UnitType.CURRENCY_UNIT
                                        if (nativeCurrency.isBlank()) nativeCurrency = "IDR"
                                        symbol = "CASH-$nativeCurrency"
                                        if (assetName.isBlank()) assetName = "$nativeCurrency Cash"
                                    }
                                    AssetClass.COMMODITY -> {
                                        if (unitType == UnitType.CURRENCY_UNIT) unitType = UnitType.GRAMS
                                        if (symbol.startsWith("CASH-")) {
                                            symbol = ""
                                            assetName = ""
                                        }
                                    }
                                    else -> {
                                        unitType = UnitType.SHARES
                                        if (symbol.startsWith("CASH-")) {
                                            symbol = ""
                                            assetName = ""
                                        }
                                    }
                                }
                            },
                            label = { Text(cls.singularName) },
                            modifier = Modifier.testTag("sheet_class_${cls.name.lowercase()}")
                        )
                    }
                }
            }

            if (assetClass == AssetClass.CASH) {
                Text(
                    text = "Cash Currency",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    SupportedCurrency.entries.take(8).forEach { cur ->
                        FilterChip(
                            selected = nativeCurrency.equals(cur.code, true),
                            onClick = {
                                nativeCurrency = cur.code
                                symbol = "CASH-${cur.code}"
                                assetName = "${cur.displayName} Cash"
                            },
                            label = { Text(cur.code) },
                            modifier = Modifier.testTag("cash_currency_${cur.code}")
                        )
                    }
                }

                OutlinedTextField(
                    value = quantityText,
                    onValueChange = { input ->
                        if (input.isEmpty() || input.matches(CurrencyFormatter.DECIMAL_INPUT_REGEX)) {
                            quantityText = input
                            validationError = null
                        }
                    },
                    label = { Text("Cash Balance ($nativeCurrency)") },
                    placeholder = { Text("e.g. 25000000") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_quantity")
                )

                OutlinedTextField(
                    value = accountLabel,
                    onValueChange = { accountLabel = it },
                    label = { Text("Bank / Wallet Label (optional)") },
                    placeholder = { Text("e.g. BCA Savings, Wise USD") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                // Ticker Symbol Search
                OutlinedTextField(
                    value = symbol,
                    onValueChange = { raw ->
                        val upper = raw.uppercase()
                        symbol = upper
                        validationError = null
                        if (!isEditing) {
                            onSearchSymbolChange(upper)
                        }
                    },
                    label = {
                        Text(
                            when (assetClass) {
                                AssetClass.COMMODITY -> "Commodity Ticker (e.g. GC=F, SI=F, CL=F)"
                                AssetClass.ETF -> "ETF Ticker (e.g. VOO, QQQ, VWRA.L)"
                                else -> "Stock Ticker (e.g. AAPL, BBCA.JK, NVDA)"
                            }
                        )
                    },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_symbol")
                )

                if (isSearchingSymbol) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }

                // Quick Picks for Commodity / ETF / Stock
                val classPresets = remember(assetClass) {
                    CuratedAssets.presets.filter { it.assetClass == assetClass }
                }
                if (classPresets.isNotEmpty() && !isEditing) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        classPresets.forEach { preset ->
                            AssistChip(
                                onClick = {
                                    symbol = preset.symbol
                                    assetName = preset.name
                                    nativeCurrency = preset.nativeCurrency
                                    unitType = preset.unitType
                                    validationError = null
                                },
                                label = { Text("${preset.symbol} · ${preset.name.substringBefore(" ")}") }
                            )
                        }
                    }
                }

                // Live Yahoo Search Suggestions
                val matchingSuggestions = remember(searchSuggestions, symbol, assetClass) {
                    if (symbol.isBlank()) emptyList()
                    else searchSuggestions.filter {
                        it.assetClass == assetClass || symbol.length >= 2
                    }.take(4)
                }

                if (matchingSuggestions.isNotEmpty() && !isEditing) {
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            matchingSuggestions.forEachIndexed { index, suggestion ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            symbol = suggestion.symbol
                                            assetName = suggestion.name
                                            assetClass = suggestion.assetClass
                                            nativeCurrency = suggestion.currency
                                            unitType = if (suggestion.assetClass == AssetClass.COMMODITY &&
                                                (suggestion.symbol == "GC=F" || suggestion.symbol == "SI=F")
                                            ) UnitType.GRAMS else UnitType.SHARES
                                        }
                                        .padding(horizontal = 14.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = suggestion.symbol,
                                            style = MaterialTheme.typography.titleSmall,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = suggestion.name,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Text(
                                        text = "${suggestion.assetClass.singularName} · ${suggestion.exchange}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                if (index < matchingSuggestions.lastIndex) {
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                }
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = assetName,
                    onValueChange = { assetName = it },
                    label = { Text("Display Name") },
                    placeholder = { Text("e.g. Vanguard S&P 500 ETF") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_name")
                )

                if (assetClass == AssetClass.COMMODITY) {
                    Text(
                        text = "Measurement Unit",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(UnitType.GRAMS, UnitType.TROY_OZ, UnitType.SHARES).forEach { unit ->
                            FilterChip(
                                selected = unitType == unit,
                                onClick = { unitType = unit },
                                label = { Text(unit.label) }
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = quantityText,
                        onValueChange = { input ->
                            if (input.isEmpty() || input.matches(CurrencyFormatter.DECIMAL_INPUT_REGEX)) {
                                quantityText = input
                                validationError = null
                            }
                        },
                        label = { Text("Quantity (${unitType.shortLabel})") },
                        placeholder = { Text("e.g. 10") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("input_quantity")
                    )

                    OutlinedTextField(
                        value = nativeCurrency,
                        onValueChange = { nativeCurrency = it.uppercase().take(4) },
                        label = { Text("Quote Currency") },
                        placeholder = { Text("USD / IDR") },
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                        singleLine = true,
                        modifier = Modifier.weight(0.75f)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = avgCostText,
                        onValueChange = { input ->
                            if (input.isEmpty() || input.matches(CurrencyFormatter.DECIMAL_INPUT_REGEX)) {
                                avgCostText = input
                            }
                        },
                        label = { Text("Avg Cost / ${unitType.shortLabel} (opt)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )

                    OutlinedTextField(
                        value = manualPriceText,
                        onValueChange = { input ->
                            if (input.isEmpty() || input.matches(CurrencyFormatter.DECIMAL_INPUT_REGEX)) {
                                manualPriceText = input
                            }
                        },
                        label = { Text("Manual Price (opt)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                OutlinedTextField(
                    value = accountLabel,
                    onValueChange = { accountLabel = it },
                    label = { Text("Broker / Vault Label (optional)") },
                    placeholder = { Text("e.g. IBKR, Stockbit, Antam Vault") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Optional Buy Date Picker Field (shared across all asset classes)
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("buy_date_selector")
                    .clickable { showDatePicker = true },
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CalendarToday,
                            contentDescription = "Select buy date",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                        Column {
                            Text(
                                text = "Buy Date (optional)",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = buyDateMillis?.let { CurrencyFormatter.formatDate(it) }
                                    ?: "Not set · tap to choose date",
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (buyDateMillis != null) {
                                    MaterialTheme.colorScheme.onSurface
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                    }

                    if (buyDateMillis != null) {
                        IconButton(
                            onClick = { buyDateMillis = null },
                            modifier = Modifier.testTag("clear_buy_date_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = "Clear buy date",
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            validationError?.let { error ->
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Button(
                onClick = {
                    val qty = quantityText.toDoubleOrNull()
                    val cleanSymbol = if (assetClass == AssetClass.CASH) {
                        "CASH-${nativeCurrency.ifBlank { "IDR" }.uppercase()}"
                    } else {
                        symbol.trim().uppercase()
                    }
                    when {
                        cleanSymbol.isBlank() -> validationError = "Please enter a ticker symbol."
                        qty == null || qty <= 0.0 -> validationError = "Please enter a positive quantity."
                        else -> {
                            val resolvedName = assetName.trim().ifBlank {
                                searchSuggestions.firstOrNull { it.symbol.equals(cleanSymbol, ignoreCase = true) }?.name
                                    ?: cleanSymbol
                            }
                            onSave(
                                Holding(
                                    id = existingHolding?.id ?: 0L,
                                    symbol = cleanSymbol,
                                    name = resolvedName,
                                    assetClass = assetClass,
                                    quantity = qty,
                                    unitType = unitType,
                                    averageCostPerUnit = avgCostText.toDoubleOrNull()?.takeIf { it > 0.0 },
                                    nativeCurrency = nativeCurrency.trim().ifBlank { "USD" }.uppercase(),
                                    manualPriceOverride = manualPriceText.toDoubleOrNull()?.takeIf { it > 0.0 },
                                    accountLabel = accountLabel.trim(),
                                    buyDateMillis = buyDateMillis
                                )
                            )
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("save_holding_button"),
                shape = MaterialTheme.shapes.medium
            ) {
                Text(if (isEditing) "Update Holding" else "Save & Fetch Live Quote")
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }

    if (showDatePicker) {
        val todayEnd = remember { CurrencyFormatter.utcTodayEndMillis() }
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = CurrencyFormatter.localMillisToUtcDatePickerMillis(
                buyDateMillis ?: System.currentTimeMillis()
            ),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                    return utcTimeMillis <= todayEnd
                }
            }
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        buyDateMillis = datePickerState.selectedDateMillis?.let {
                            CurrencyFormatter.utcDatePickerMillisToLocalNoon(it)
                        }
                        showDatePicker = false
                    },
                    modifier = Modifier.testTag("confirm_buy_date_button")
                ) {
                    Text("Confirm")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("Cancel")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

private fun formatPlainNumber(value: Double): String {
    return if (value % 1.0 == 0.0) {
        value.toLong().toString()
    } else {
        value.toBigDecimal().stripTrailingZeros().toPlainString()
    }
}
