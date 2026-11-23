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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.material3.Switch
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SellHoldingSheet(
    target: ValuedHolding,
    displayCurrency: SupportedCurrency,
    onConfirmSell: (sellQuantity: Double, sellPricePerUnit: Double, depositProceedsToCash: Boolean, sellDateMillis: Long) -> Unit,
    onDismiss: () -> Unit
) {
    BackHandler(onBack = onDismiss)

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val wealthColors = LocalWealthColors.current
    val holding = target.holding
    val isCash = holding.assetClass == AssetClass.CASH

    var sellQtyText by remember { mutableStateOf("") }
    var sellPriceText by remember(target.holding.id) {
        mutableStateOf(
            if (isCash) "1"
            else if (target.unitQuoteNative > 0.0) {
                CurrencyFormatter.formatPlainInputNumber(target.unitQuoteNative)
            } else ""
        )
    }
    var depositToCash by remember(target.holding.id) { mutableStateOf(!isCash) }
    var sellDateMillis by remember { mutableStateOf(System.currentTimeMillis()) }
    var showDatePicker by remember { mutableStateOf(false) }
    var validationError by remember { mutableStateOf<String?>(null) }

    val sellQty = sellQtyText.toDoubleOrNull() ?: 0.0
    val sellPrice = if (isCash) 1.0 else (sellPriceText.toDoubleOrNull() ?: 0.0)
    val sellOutcome = CurrencyFormatter.calculateSellOutcome(
        existingQuantity = holding.quantity,
        existingAvgCost = if (isCash) null else holding.averageCostPerUnit,
        sellQuantity = sellQty,
        sellPricePerUnit = sellPrice
    )

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
                        text = if (isCash) "Withdraw ${holding.nativeCurrency} Cash" else "Sell ${holding.symbol}",
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

            // Current Position Summary
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
                            text = "AVAILABLE TO SELL",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = if (isCash) {
                                CurrencyFormatter.formatAmount(holding.quantity, holding.nativeCurrency)
                            } else {
                                CurrencyFormatter.formatQuantity(holding.quantity, holding.unitType)
                            },
                            style = NumericTextStyles.cardAmount,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    if (!isCash && holding.averageCostPerUnit != null) {
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "AVG COST",
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

            // Quick Percentage Chips
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf(0.25 to "25%", 0.5 to "50%", 0.75 to "75%", 1.0 to "Max").forEach { (frac, label) ->
                    AssistChip(
                        onClick = {
                            val amount = holding.quantity * frac
                            sellQtyText = amount.toBigDecimal().stripTrailingZeros().toPlainString()
                            validationError = null
                        },
                        label = { Text(label) },
                        modifier = Modifier.testTag("sell_fraction_$label")
                    )
                }
            }

            OutlinedTextField(
                value = sellQtyText,
                onValueChange = { input ->
                    if (input.isEmpty() || input.matches(CurrencyFormatter.DECIMAL_INPUT_REGEX)) {
                        sellQtyText = input
                        validationError = null
                    }
                },
                label = {
                    Text(
                        if (isCash) "Withdraw Amount (${holding.nativeCurrency})"
                        else "Quantity to Sell (${holding.unitType.shortLabel})"
                    )
                },
                placeholder = { Text("Max ${CurrencyFormatter.formatQuantity(holding.quantity, holding.unitType)}") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("sell_quantity_input")
            )

            if (!isCash) {
                OutlinedTextField(
                    value = sellPriceText,
                    onValueChange = { input ->
                        if (input.isEmpty() || input.matches(CurrencyFormatter.DECIMAL_INPUT_REGEX)) {
                            sellPriceText = input
                            validationError = null
                        }
                    },
                    label = { Text("Sell Price per ${holding.unitType.shortLabel} (${holding.nativeCurrency})") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("sell_price_input")
                )

                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Deposit proceeds to ${holding.nativeCurrency} Cash",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Keep sale cash inside your tracked portfolio",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = depositToCash,
                            onCheckedChange = { depositToCash = it },
                            modifier = Modifier.testTag("switch_deposit_proceeds")
                        )
                    }
                }
            }

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("sell_date_selector")
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
                            contentDescription = "Sell date",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                        Column {
                            Text(
                                text = if (isCash) "Withdrawal Date" else "Sell Date",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = CurrencyFormatter.formatDate(sellDateMillis),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                    Text(
                        text = "Change",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // Live Sale Preview
            if (sellQty > 0.0 && sellQty <= holding.quantity + 1e-8) {
                val proceedsDisplay = sellOutcome.proceedsNative * target.fxRateToDisplay
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "SALE SUMMARY PREVIEW",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Remaining Position", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                text = if (sellOutcome.soldAll) {
                                    "0 (Closes position)"
                                } else {
                                    CurrencyFormatter.formatQuantity(sellOutcome.remainingQuantity, holding.unitType)
                                },
                                style = NumericTextStyles.cardAmount,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = if (isCash) "Withdrawal (${displayCurrency.code})" else "Gross Proceeds",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = CurrencyFormatter.formatAmount(proceedsDisplay, displayCurrency.code),
                                    style = NumericTextStyles.cardAmount,
                                    fontWeight = FontWeight.Bold
                                )
                                if (holding.nativeCurrency != displayCurrency.code) {
                                    Text(
                                        text = CurrencyFormatter.formatAmount(
                                            sellOutcome.proceedsNative,
                                            holding.nativeCurrency
                                        ),
                                        style = NumericTextStyles.subAmount,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        sellOutcome.realizedGainNative?.let { gainNative ->
                            val isGain = gainNative >= 0.0
                            val gainColor = if (isGain) wealthColors.gain else wealthColors.loss
                            val gainDisplay = gainNative * target.fxRateToDisplay
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Realized P&L", style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    text = "${
                                        CurrencyFormatter.formatAmount(
                                            gainDisplay,
                                            displayCurrency.code,
                                            showSign = true
                                        )
                                    } (${CurrencyFormatter.formatPercent(sellOutcome.realizedGainPercent ?: 0.0)})",
                                    style = NumericTextStyles.cardAmount,
                                    fontWeight = FontWeight.Bold,
                                    color = gainColor
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
                    val qty = sellQtyText.toDoubleOrNull()
                    val price = if (isCash) 1.0 else sellPriceText.toDoubleOrNull()
                    when {
                        qty == null || qty <= 0.0 ->
                            validationError = "Please enter a positive quantity to sell."
                        qty > holding.quantity + 1e-8 ->
                            validationError = "Cannot sell more than you currently hold."
                        price == null || price < 0.0 ->
                            validationError = "Please enter a valid sell price."
                        else -> onConfirmSell(qty, price, depositToCash, sellDateMillis)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("confirm_sell_button"),
                shape = MaterialTheme.shapes.medium,
                colors = ButtonDefaults.buttonColors(
                    containerColor = wealthColors.loss,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Text(if (isCash) "Confirm Withdrawal" else "Confirm Sell")
            }

            Spacer(modifier = Modifier.height(6.dp))
        }
    }

    if (showDatePicker) {
        val todayEnd = remember { CurrencyFormatter.utcTodayEndMillis() }
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = CurrencyFormatter.localMillisToUtcDatePickerMillis(sellDateMillis),
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
                        datePickerState.selectedDateMillis?.let {
                            sellDateMillis = CurrencyFormatter.utcDatePickerMillisToLocalNoon(it)
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
