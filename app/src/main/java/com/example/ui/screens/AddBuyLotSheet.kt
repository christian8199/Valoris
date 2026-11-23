package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.domain.model.AssetClass
import com.example.domain.model.SupportedCurrency
import com.example.domain.model.ValuedHolding
import com.example.ui.theme.LocalWealthColors
import com.example.ui.theme.NumericTextStyles
import com.example.util.CurrencyFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddBuyLotSheet(
    target: ValuedHolding,
    displayCurrency: SupportedCurrency,
    onConfirmBuy: (addedQuantity: Double, buyPricePerUnit: Double?, buyDateMillis: Long?) -> Unit,
    onDismiss: () -> Unit
) {
    BackHandler(onBack = onDismiss)

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val wealthColors = LocalWealthColors.current
    val holding = target.holding
    val isCash = holding.assetClass == AssetClass.CASH

    var addedQtyText by remember { mutableStateOf("") }
    var buyPriceText by remember(target.holding.id) {
        mutableStateOf(
            if (isCash || target.unitQuoteNative <= 0.0) ""
            else CurrencyFormatter.formatPlainInputNumber(target.unitQuoteNative)
        )
    }
    var buyDateMillis by remember { mutableStateOf<Long?>(System.currentTimeMillis()) }
    var showDatePicker by remember { mutableStateOf(false) }
    var validationError by remember { mutableStateOf<String?>(null) }

    val addedQty = addedQtyText.toDoubleOrNull() ?: 0.0
    val buyPrice = buyPriceText.toDoubleOrNull()
    val projectedQty = holding.quantity + addedQty
    val projectedAvgCost = if (isCash) {
        null
    } else {
        CurrencyFormatter.calculateWeightedAverageCost(
            existingQuantity = holding.quantity,
            existingAvgCost = holding.averageCostPerUnit,
            addedQuantity = addedQty,
            newBuyPrice = buyPrice
        )
    }

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
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Add Buy to ${holding.symbol}",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = holding.name,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = "Close")
                }
            }

            // Current Position Context
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "CURRENTLY OWNED",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = CurrencyFormatter.formatQuantity(holding.quantity, holding.unitType),
                            style = NumericTextStyles.cardAmount,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    if (!isCash && holding.averageCostPerUnit != null) {
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "CURRENT AVG COST",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = CurrencyFormatter.formatAmount(
                                    holding.averageCostPerUnit,
                                    holding.nativeCurrency
                                ),
                                style = NumericTextStyles.cardAmount,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            OutlinedTextField(
                value = addedQtyText,
                onValueChange = { input ->
                    if (input.isEmpty() || input.matches(CurrencyFormatter.DECIMAL_INPUT_REGEX)) {
                        addedQtyText = input
                        validationError = null
                    }
                },
                label = {
                    Text(
                        if (isCash) "Additional Deposit (${holding.nativeCurrency})"
                        else "Additional Quantity (${holding.unitType.shortLabel})"
                    )
                },
                placeholder = { Text(if (isCash) "e.g. 5000000" else "e.g. 5") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("add_buy_quantity_input")
            )

            if (!isCash) {
                OutlinedTextField(
                    value = buyPriceText,
                    onValueChange = { input ->
                        if (input.isEmpty() || input.matches(CurrencyFormatter.DECIMAL_INPUT_REGEX)) {
                            buyPriceText = input
                            validationError = null
                        }
                    },
                    label = { Text("Buy Price per ${holding.unitType.shortLabel} (${holding.nativeCurrency})") },
                    placeholder = { Text("e.g. 250.00") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("add_buy_price_input")
                )
            }

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("add_buy_date_selector")
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
                            contentDescription = "Buy date",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                        Column {
                            Text(
                                text = "Buy Date",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = buyDateMillis?.let { CurrencyFormatter.formatDate(it) } ?: "Not set",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                    if (buyDateMillis != null) {
                        IconButton(onClick = { buyDateMillis = null }) {
                            Icon(Icons.Filled.Close, contentDescription = "Clear date", modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }

            // Live Weighted Position Preview
            if (addedQty > 0.0) {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = wealthColors.gainContainer.copy(alpha = 0.45f),
                    border = BorderStroke(1.dp, wealthColors.gain.copy(alpha = 0.35f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "UPDATED POSITION PREVIEW",
                            style = MaterialTheme.typography.labelSmall,
                            color = wealthColors.gain
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("New Total Quantity", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                text = CurrencyFormatter.formatQuantity(projectedQty, holding.unitType),
                                style = NumericTextStyles.cardAmount,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        if (projectedAvgCost != null) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("New Weighted Avg Cost", style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    text = CurrencyFormatter.formatAmount(projectedAvgCost, holding.nativeCurrency),
                                    style = NumericTextStyles.cardAmount,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        if (target.unitQuoteNative > 0.0) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            val newTotalDisplay = if (holding.quantity > 0.0) {
                                target.totalValueDisplay * (projectedQty / holding.quantity)
                            } else 0.0
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Est. Value (${displayCurrency.code})", style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    text = CurrencyFormatter.formatAmount(newTotalDisplay, displayCurrency.code),
                                    style = NumericTextStyles.cardAmount,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            validationError?.let { err ->
                Text(
                    text = err,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Button(
                onClick = {
                    val qty = addedQtyText.toDoubleOrNull()
                    if (qty == null || qty <= 0.0) {
                        validationError = "Please enter a positive quantity to add."
                    } else {
                        onConfirmBuy(qty, buyPriceText.toDoubleOrNull(), buyDateMillis)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("confirm_add_buy_button"),
                shape = MaterialTheme.shapes.medium
            ) {
                Text("Confirm New Buy")
            }

            Spacer(modifier = Modifier.height(6.dp))
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
                    }
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
