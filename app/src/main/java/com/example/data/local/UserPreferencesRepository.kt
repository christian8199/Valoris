package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.domain.model.ChartTimeframe
import com.example.domain.model.RefreshInterval
import com.example.domain.model.SupportedCurrency
import com.example.domain.model.ThemeMode
import com.example.domain.model.WidgetDisplayMetric
import com.example.util.AppLockSecurity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "valoris_prefs")

data class UserPreferences(
    val displayCurrency: SupportedCurrency = SupportedCurrency.IDR,
    val isBalanceHidden: Boolean = false,
    val refreshInterval: RefreshInterval = RefreshInterval.FIVE_MINUTES,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val useCompactNumbers: Boolean = false,
    val showPnlOnCards: Boolean = true,
    val showExtendedHoursPrice: Boolean = false,
    val defaultChartTimeframe: ChartTimeframe = ChartTimeframe.MONTH,
    val widgetDisplayMetric: WidgetDisplayMetric = WidgetDisplayMetric.ASSET_VALUE,
    val widgetChartTimeframe: ChartTimeframe = ChartTimeframe.MONTH,
    val appLockEnabled: Boolean = false,
    val biometricUnlockEnabled: Boolean = true,
    val appLockPinHash: String = "",
    val appLockPinSalt: String = ""
)

data class AppLockConfig(
    val isLoaded: Boolean = false,
    val isEnabled: Boolean = false,
    val biometricEnabled: Boolean = true
)

class UserPreferencesRepository(context: Context) {
    private val appContext = context.applicationContext

    private object Keys {
        val DISPLAY_CURRENCY = stringPreferencesKey("display_currency")
        val HIDE_BALANCE = booleanPreferencesKey("hide_balance")
        val REFRESH_MINUTES = intPreferencesKey("refresh_minutes")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val USE_COMPACT_NUMBERS = booleanPreferencesKey("use_compact_numbers")
        val SHOW_PNL_ON_CARDS = booleanPreferencesKey("show_pnl_on_cards")
        val SHOW_EXTENDED_HOURS_PRICE = booleanPreferencesKey("show_extended_hours_price")
        val DEFAULT_CHART_TIMEFRAME = stringPreferencesKey("default_chart_timeframe")
        val WIDGET_DISPLAY_METRIC = stringPreferencesKey("widget_display_metric")
        val WIDGET_CHART_TIMEFRAME = stringPreferencesKey("widget_chart_timeframe")
        val APP_LOCK_ENABLED = booleanPreferencesKey("app_lock_enabled")
        val BIOMETRIC_UNLOCK_ENABLED = booleanPreferencesKey("biometric_unlock_enabled")
        val APP_LOCK_PIN_HASH = stringPreferencesKey("app_lock_pin_hash")
        val APP_LOCK_PIN_SALT = stringPreferencesKey("app_lock_pin_salt")
        val INITIAL_HISTORY_SEEDED = booleanPreferencesKey("initial_history_seeded")
    }

    val preferencesFlow: Flow<UserPreferences> = appContext.dataStore.data.map { prefs ->
        val pinHash = prefs[Keys.APP_LOCK_PIN_HASH] ?: ""
        val pinSalt = prefs[Keys.APP_LOCK_PIN_SALT] ?: ""
        val lockRequested = prefs[Keys.APP_LOCK_ENABLED] ?: false
        UserPreferences(
            displayCurrency = SupportedCurrency.fromCode(
                prefs[Keys.DISPLAY_CURRENCY] ?: SupportedCurrency.IDR.code
            ),
            isBalanceHidden = prefs[Keys.HIDE_BALANCE] ?: false,
            refreshInterval = RefreshInterval.fromMinutes(
                prefs[Keys.REFRESH_MINUTES] ?: RefreshInterval.FIVE_MINUTES.minutes
            ),
            themeMode = ThemeMode.fromName(prefs[Keys.THEME_MODE]),
            useCompactNumbers = prefs[Keys.USE_COMPACT_NUMBERS] ?: false,
            showPnlOnCards = prefs[Keys.SHOW_PNL_ON_CARDS] ?: true,
            showExtendedHoursPrice = prefs[Keys.SHOW_EXTENDED_HOURS_PRICE] ?: false,
            defaultChartTimeframe = ChartTimeframe.fromName(prefs[Keys.DEFAULT_CHART_TIMEFRAME]),
            widgetDisplayMetric = WidgetDisplayMetric.fromName(prefs[Keys.WIDGET_DISPLAY_METRIC]),
            widgetChartTimeframe = ChartTimeframe.fromName(prefs[Keys.WIDGET_CHART_TIMEFRAME]),
            appLockEnabled = lockRequested && pinHash.isNotBlank() && pinSalt.isNotBlank(),
            biometricUnlockEnabled = prefs[Keys.BIOMETRIC_UNLOCK_ENABLED] ?: true,
            appLockPinHash = pinHash,
            appLockPinSalt = pinSalt
        )
    }

    val appLockConfigFlow: Flow<AppLockConfig> = preferencesFlow.map { prefs ->
        AppLockConfig(
            isLoaded = true,
            isEnabled = prefs.appLockEnabled,
            biometricEnabled = prefs.biometricUnlockEnabled
        )
    }

    suspend fun setDisplayCurrency(currency: SupportedCurrency) {
        appContext.dataStore.edit { prefs ->
            prefs[Keys.DISPLAY_CURRENCY] = currency.code
        }
    }

    suspend fun toggleBalanceHidden() {
        appContext.dataStore.edit { prefs ->
            val current = prefs[Keys.HIDE_BALANCE] ?: false
            prefs[Keys.HIDE_BALANCE] = !current
        }
    }

    suspend fun setBalanceHidden(hidden: Boolean) {
        appContext.dataStore.edit { prefs ->
            prefs[Keys.HIDE_BALANCE] = hidden
        }
    }

    suspend fun setRefreshInterval(interval: RefreshInterval) {
        appContext.dataStore.edit { prefs ->
            prefs[Keys.REFRESH_MINUTES] = interval.minutes
        }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        appContext.dataStore.edit { prefs ->
            prefs[Keys.THEME_MODE] = mode.name
        }
    }

    suspend fun setUseCompactNumbers(compact: Boolean) {
        appContext.dataStore.edit { prefs ->
            prefs[Keys.USE_COMPACT_NUMBERS] = compact
        }
    }

    suspend fun setShowPnlOnCards(show: Boolean) {
        appContext.dataStore.edit { prefs ->
            prefs[Keys.SHOW_PNL_ON_CARDS] = show
        }
    }

    suspend fun setShowExtendedHoursPrice(show: Boolean) {
        appContext.dataStore.edit { prefs ->
            prefs[Keys.SHOW_EXTENDED_HOURS_PRICE] = show
        }
    }

    suspend fun setDefaultChartTimeframe(timeframe: ChartTimeframe) {
        appContext.dataStore.edit { prefs ->
            prefs[Keys.DEFAULT_CHART_TIMEFRAME] = timeframe.name
        }
    }

    suspend fun setWidgetDisplayMetric(metric: WidgetDisplayMetric) {
        appContext.dataStore.edit { prefs ->
            prefs[Keys.WIDGET_DISPLAY_METRIC] = metric.name
        }
    }

    suspend fun setWidgetChartTimeframe(timeframe: ChartTimeframe) {
        appContext.dataStore.edit { prefs ->
            prefs[Keys.WIDGET_CHART_TIMEFRAME] = timeframe.name
        }
    }

    suspend fun enableAppLock(pin: String, biometricEnabled: Boolean) {
        val salt = AppLockSecurity.generateSaltHex()
        val hash = AppLockSecurity.hashPin(pin, salt)
        appContext.dataStore.edit { prefs ->
            prefs[Keys.APP_LOCK_PIN_SALT] = salt
            prefs[Keys.APP_LOCK_PIN_HASH] = hash
            prefs[Keys.APP_LOCK_ENABLED] = true
            prefs[Keys.BIOMETRIC_UNLOCK_ENABLED] = biometricEnabled
        }
    }

    suspend fun updatePin(newPin: String) {
        val salt = AppLockSecurity.generateSaltHex()
        val hash = AppLockSecurity.hashPin(newPin, salt)
        appContext.dataStore.edit { prefs ->
            prefs[Keys.APP_LOCK_PIN_SALT] = salt
            prefs[Keys.APP_LOCK_PIN_HASH] = hash
        }
    }

    suspend fun disableAppLock() {
        appContext.dataStore.edit { prefs ->
            prefs[Keys.APP_LOCK_ENABLED] = false
            prefs.remove(Keys.APP_LOCK_PIN_HASH)
            prefs.remove(Keys.APP_LOCK_PIN_SALT)
        }
    }

    suspend fun setBiometricUnlockEnabled(enabled: Boolean) {
        appContext.dataStore.edit { prefs ->
            prefs[Keys.BIOMETRIC_UNLOCK_ENABLED] = enabled
        }
    }

    suspend fun verifyPin(pin: String): Boolean {
        val current = preferencesFlow.first()
        return AppLockSecurity.verifyPin(
            pin = pin,
            expectedHashHex = current.appLockPinHash,
            saltHex = current.appLockPinSalt
        )
    }

    suspend fun isInitialHistorySeeded(): Boolean {
        return appContext.dataStore.data.first()[Keys.INITIAL_HISTORY_SEEDED] ?: false
    }

    suspend fun markInitialHistorySeeded() {
        appContext.dataStore.edit { prefs ->
            prefs[Keys.INITIAL_HISTORY_SEEDED] = true
        }
    }

    suspend fun getCurrentPreferences(): UserPreferences {
        return preferencesFlow.first()
    }

    suspend fun restoreNonSecurityPreferences(
        displayCurrency: SupportedCurrency,
        isBalanceHidden: Boolean,
        refreshInterval: RefreshInterval,
        themeMode: ThemeMode,
        useCompactNumbers: Boolean,
        showPnlOnCards: Boolean,
        showExtendedHoursPrice: Boolean,
        defaultChartTimeframe: ChartTimeframe
    ) {
        appContext.dataStore.edit { prefs ->
            prefs[Keys.DISPLAY_CURRENCY] = displayCurrency.code
            prefs[Keys.HIDE_BALANCE] = isBalanceHidden
            prefs[Keys.REFRESH_MINUTES] = refreshInterval.minutes
            prefs[Keys.THEME_MODE] = themeMode.name
            prefs[Keys.USE_COMPACT_NUMBERS] = useCompactNumbers
            prefs[Keys.SHOW_PNL_ON_CARDS] = showPnlOnCards
            prefs[Keys.SHOW_EXTENDED_HOURS_PRICE] = showExtendedHoursPrice
            prefs[Keys.DEFAULT_CHART_TIMEFRAME] = defaultChartTimeframe.name
            prefs[Keys.INITIAL_HISTORY_SEEDED] = true
        }
    }
}
