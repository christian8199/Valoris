package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.backup.BackupPreview
import com.example.domain.model.ChartTimeframe
import com.example.domain.model.PortfolioDashboardData
import com.example.domain.model.RefreshInterval
import com.example.domain.model.SupportedCurrency
import com.example.domain.model.ThemeMode
import com.example.ui.theme.NumericTextStyles
import com.example.util.CurrencyFormatter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    dashboard: PortfolioDashboardData,
    historyCount: Int,
    isBackupBusy: Boolean,
    pendingRestorePreview: BackupPreview?,
    onSelectThemeMode: (ThemeMode) -> Unit,
    onSelectDisplayCurrency: (SupportedCurrency) -> Unit,
    onToggleCompactNumbers: (Boolean) -> Unit,
    onToggleShowPnlOnCards: (Boolean) -> Unit,
    onToggleExtendedHoursPrice: (Boolean) -> Unit,
    onSelectDefaultChartTimeframe: (ChartTimeframe) -> Unit,
    onToggleBalanceHidden: (Boolean) -> Unit,
    onSelectRefreshInterval: (RefreshInterval) -> Unit,
    onEnableAppLock: (pin: String, biometricEnabled: Boolean) -> Unit,
    onChangeAppLockPin: (currentPin: String, newPin: String, onResult: (Boolean) -> Unit) -> Unit,
    onDisableAppLock: (currentPin: String, onResult: (Boolean) -> Unit) -> Unit,
    onToggleBiometricUnlock: (Boolean) -> Unit,
    onExportBackupUri: (Uri) -> Unit,
    onPrepareRestoreUri: (Uri) -> Unit,
    onConfirmRestoreBackup: () -> Unit,
    onDismissRestorePreview: () -> Unit,
    onSystemPickerStateChanged: (Boolean) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBack)

    val context = LocalContext.current
    val biometricAvailable = remember(context) { isBiometricAvailable(context) }
    var activePinDialog by remember { mutableStateOf<PinDialogMode?>(null) }

    val exportBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        onSystemPickerStateChanged(false)
        if (uri != null) {
            onExportBackupUri(uri)
        }
    }

    val restoreBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        onSystemPickerStateChanged(false)
        if (uri != null) {
            onPrepareRestoreUri(uri)
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Top Header with Back Button
        item(key = "settings_header", contentType = "header") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 760.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .testTag("settings_back_button")
                        .minimumInteractiveComponentSize()
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back"
                    )
                }
                Column {
                    Text(
                        text = "Display Preferences",
                        style = MaterialTheme.typography.displayMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "Appearance, currency, number formatting, and sync",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Theme Appearance Card
        item(key = "settings_theme", contentType = "settings_card") {
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
                        text = "Theme Appearance",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Switch between editorial light alabaster, dark obsidian, or system default.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ThemeMode.entries.forEach { mode ->
                            val selected = dashboard.themeMode == mode
                            val icon = when (mode) {
                                ThemeMode.SYSTEM -> Icons.Filled.PhoneAndroid
                                ThemeMode.LIGHT -> Icons.Filled.LightMode
                                ThemeMode.DARK -> Icons.Filled.DarkMode
                            }
                            FilterChip(
                                selected = selected,
                                onClick = { onSelectThemeMode(mode) },
                                label = { Text(mode.label) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                modifier = Modifier.testTag("theme_mode_${mode.name.lowercase()}")
                            )
                        }
                    }
                }
            }
        }

        // Preferred Display Currency
        item(key = "settings_currency", contentType = "settings_card") {
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
                        text = "All holdings and P&L totals auto-convert into this currency.",
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
                                modifier = Modifier.testTag("settings_currency_${currency.code}")
                            )
                        }
                    }
                }
            }
        }

        // Default Graph Range View
        item(key = "settings_graph_range", contentType = "settings_card") {
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
                        text = "Default Graph Range",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Initial time range shown on Total Asset Movement and holding price charts.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ChartTimeframe.entries.forEach { timeframe ->
                            val selected = dashboard.defaultChartTimeframe == timeframe
                            FilterChip(
                                selected = selected,
                                onClick = { onSelectDefaultChartTimeframe(timeframe) },
                                label = { Text(timeframe.label) },
                                leadingIcon = if (selected) {
                                    { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                } else null,
                                modifier = Modifier.testTag("settings_chart_range_${timeframe.name.lowercase()}")
                            )
                        }
                    }
                }
            }
        }

        // Number & Ledger Display Toggles
        item(key = "settings_display_toggles", contentType = "settings_card") {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 760.dp),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Numbers & Ledger Display",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    val sampleAmount = if (dashboard.totalValueDisplay > 0.0) {
                        dashboard.totalValueDisplay
                    } else if (dashboard.displayCurrency == SupportedCurrency.IDR) {
                        158_420_000.0
                    } else {
                        12_450.0
                    }
                    val previewText = CurrencyFormatter.formatPreferenceAmount(
                        amount = sampleAmount,
                        currencyCode = dashboard.displayCurrency.code,
                        isHidden = dashboard.isBalanceHidden,
                        useCompact = dashboard.useCompactNumbers
                    )

                    SettingsToggleRow(
                        title = "Compact Number Format",
                        subtitle = "Preview: $previewText",
                        checked = dashboard.useCompactNumbers,
                        onCheckedChange = onToggleCompactNumbers,
                        testTag = "switch_compact_numbers"
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    SettingsToggleRow(
                        title = "Show P&L on Holding Cards",
                        subtitle = "Display unrealized gain/loss value & % directly in lists",
                        checked = dashboard.showPnlOnCards,
                        onCheckedChange = onToggleShowPnlOnCards,
                        testTag = "switch_show_pnl_cards"
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    SettingsToggleRow(
                        title = "Outside-Hours (24h) Price",
                        subtitle = "Display live pre-market, after-hours & 24h quotes when regular market is closed",
                        checked = dashboard.showExtendedHoursPrice,
                        onCheckedChange = onToggleExtendedHoursPrice,
                        testTag = "switch_extended_hours_price"
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    SettingsToggleRow(
                        title = "Privacy Mode (Hide Balances)",
                        subtitle = "Mask total net worth and position values across screens",
                        checked = dashboard.isBalanceHidden,
                        onCheckedChange = onToggleBalanceHidden,
                        testTag = "switch_privacy_mode"
                    )
                }
            }
        }

        // Security & App Lock
        item(key = "settings_app_lock", contentType = "settings_card") {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 760.dp),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Lock,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "Security & App Lock",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    SettingsToggleRow(
                        title = "PIN App Lock",
                        subtitle = "Require a 4-digit PIN when opening or returning to Valoris",
                        checked = dashboard.appLockEnabled,
                        onCheckedChange = { wantEnabled ->
                            activePinDialog = if (wantEnabled) PinDialogMode.ENABLE else PinDialogMode.DISABLE
                        },
                        testTag = "switch_pin_app_lock"
                    )

                    if (dashboard.appLockEnabled) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(end = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Fingerprint,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                                Column {
                                    Text(
                                        text = "Fingerprint / Biometric Unlock",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = if (biometricAvailable) {
                                            "Unlock quickly with your enrolled fingerprint"
                                        } else {
                                            "Enroll fingerprint in device Settings to use biometrics"
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Switch(
                                checked = dashboard.biometricUnlockEnabled && biometricAvailable,
                                enabled = biometricAvailable,
                                onCheckedChange = onToggleBiometricUnlock,
                                modifier = Modifier.testTag("switch_biometric_unlock")
                            )
                        }

                        OutlinedButton(
                            onClick = { activePinDialog = PinDialogMode.CHANGE },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("change_pin_button")
                        ) {
                            Text("Change 4-Digit PIN")
                        }
                    }
                }
            }
        }

        // Auto-Refresh Cadence
        item(key = "settings_refresh_interval", contentType = "settings_card") {
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
                        text = "Live Feed Auto-Refresh",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Frequency for updating live stock, ETF, commodity, and FX feeds while active.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        RefreshInterval.entries.forEach { interval ->
                            FilterChip(
                                selected = dashboard.refreshInterval == interval,
                                onClick = { onSelectRefreshInterval(interval) },
                                label = { Text(interval.label) },
                                modifier = Modifier.testTag("settings_refresh_${interval.minutes}")
                            )
                        }
                    }
                }
            }
        }

        // Backup & Restore Assets and History
        item(key = "settings_backup_restore", contentType = "settings_card") {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 760.dp),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Backup,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "Backup & Restore",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Text(
                        text = "Save your assets, buy/sell history, and display settings to a local JSON file, or restore from a previous backup. (${dashboard.valuedHoldings.size} assets · $historyCount history entries)",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = {
                                val stamp = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
                                onSystemPickerStateChanged(true)
                                exportBackupLauncher.launch("valoris_backup_$stamp.json")
                            },
                            enabled = !isBackupBusy,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("backup_export_button"),
                            shape = MaterialTheme.shapes.medium
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Download,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Backup")
                        }
                        OutlinedButton(
                            onClick = {
                                onSystemPickerStateChanged(true)
                                restoreBackupLauncher.launch(
                                    arrayOf("application/json", "text/plain", "*/*")
                                )
                            },
                            enabled = !isBackupBusy,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("backup_restore_button"),
                            shape = MaterialTheme.shapes.medium
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Restore,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Restore")
                        }
                    }
                }
            }
        }

        // Storage & Data Note
        item(key = "settings_storage_note", contentType = "note_card") {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 760.dp),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Private Local Storage",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "All holdings and buy dates remain strictly on your device.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = "${dashboard.valuedHoldings.size} assets",
                        style = NumericTextStyles.badge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        item(key = "settings_bottom_spacer", contentType = "spacer") { Spacer(modifier = Modifier.height(72.dp)) }
    }

    pendingRestorePreview?.let { preview ->
        val exportedDateLabel = remember(preview.exportedAtMillis) {
            CurrencyFormatter.formatDate(preview.exportedAtMillis)
        }
        AlertDialog(
            onDismissRequest = onDismissRestorePreview,
            title = { Text("Restore Backup?") },
            text = {
                Text(
                    "Backup exported on $exportedDateLabel contains ${preview.holdingsCount} assets and ${preview.transactionsCount} history entries.\n\nRestoring will replace your current portfolio and transaction history on this device."
                )
            },
            confirmButton = {
                Button(
                    onClick = onConfirmRestoreBackup,
                    enabled = !isBackupBusy,
                    modifier = Modifier.testTag("confirm_restore_backup_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text("Restore")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = onDismissRestorePreview,
                    enabled = !isBackupBusy
                ) {
                    Text("Cancel")
                }
            }
        )
    }

    activePinDialog?.let { mode ->
        PinSetupDialog(
            mode = mode,
            biometricAvailable = biometricAvailable,
            initialBiometricEnabled = dashboard.biometricUnlockEnabled,
            onConfirmEnable = { pin, bioEnabled ->
                onEnableAppLock(pin, bioEnabled)
                activePinDialog = null
            },
            onConfirmChange = { currentPin, newPin, onError ->
                onChangeAppLockPin(currentPin, newPin) { ok ->
                    if (ok) activePinDialog = null else onError("Incorrect current PIN.")
                }
            },
            onConfirmDisable = { currentPin, onError ->
                onDisableAppLock(currentPin) { ok ->
                    if (ok) activePinDialog = null else onError("Incorrect PIN.")
                }
            },
            onDismiss = { activePinDialog = null }
        )
    }
}

@Composable
private fun SettingsToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 12.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(testTag)
        )
    }
}
