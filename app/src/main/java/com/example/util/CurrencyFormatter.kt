package com.example.util

import com.example.domain.model.AssetClass
import com.example.domain.model.AssetPreset
import com.example.domain.model.ChartTimeframe
import com.example.domain.model.SupportedCurrency
import com.example.domain.model.UnitType
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs

object CurrencyFormatter {
    const val GRAMS_PER_TROY_OZ = 31.1034768
    val DECIMAL_INPUT_REGEX = Regex("^\\d*\\.?\\d*$")

    private val idrSymbols = DecimalFormatSymbols(Locale.forLanguageTag("id-ID")).apply {
        groupingSeparator = '.'
        decimalSeparator = ','
    }

    private val intlSymbols = DecimalFormatSymbols(Locale.US).apply {
        groupingSeparator = ','
        decimalSeparator = '.'
    }

    private val idrIntFmt = ThreadLocal.withInitial { DecimalFormat("#,##0", idrSymbols) }
    private val idrDec1Fmt = ThreadLocal.withInitial { DecimalFormat("#,##0.0", idrSymbols) }
    private val idrDec2Fmt = ThreadLocal.withInitial { DecimalFormat("#,##0.00", idrSymbols) }
    private val intlIntFmt = ThreadLocal.withInitial { DecimalFormat("#,##0", intlSymbols) }
    private val intlDec1Fmt = ThreadLocal.withInitial { DecimalFormat("#,##0.0", intlSymbols) }
    private val intlDec2Fmt = ThreadLocal.withInitial { DecimalFormat("#,##0.00", intlSymbols) }
    private val intlQtyFmt = ThreadLocal.withInitial { DecimalFormat("#,##0.####", intlSymbols) }
    private val oneDecFmt = ThreadLocal.withInitial { DecimalFormat("0.0", intlSymbols) }
    private val percentFmt = ThreadLocal.withInitial { DecimalFormat("0.00", intlSymbols) }

    private val dateFmt = ThreadLocal.withInitial { java.text.SimpleDateFormat("MMM d, yyyy", Locale.US) }
    private val dateTimeFmt = ThreadLocal.withInitial { java.text.SimpleDateFormat("MMM d, HH:mm", Locale.US) }
    private val shortDateFmt = ThreadLocal.withInitial { java.text.SimpleDateFormat("MMM d", Locale.US) }
    private val monthYearFmt = ThreadLocal.withInitial { java.text.SimpleDateFormat("MMM yyyy", Locale.US) }
    private val timeFmt = ThreadLocal.withInitial { java.text.SimpleDateFormat("HH:mm", Locale.US) }

    private val dateCache = object : LinkedHashMap<Long, String>(128, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, String>?): Boolean = size > 128
    }

    private val currencyByCode: Map<String, SupportedCurrency> =
        SupportedCurrency.entries.associateBy { it.code }

    private fun findCurrency(code: String): SupportedCurrency? =
        currencyByCode[code] ?: currencyByCode[code.uppercase(Locale.US)]

    fun formatAmount(
        amount: Double,
        currencyCode: String,
        isHidden: Boolean = false,
        showSign: Boolean = false
    ): String {
        val currency = findCurrency(currencyCode)
        if (isHidden) {
            val sym = currency?.symbol ?: "$currencyCode "
            return "$sym••••••••"
        }

        val symbol = currency?.symbol ?: "$currencyCode "
        val decimals = currency?.decimals ?: 2
        val absVal = abs(amount)
        val isIdr = currency == SupportedCurrency.IDR || currencyCode.equals("IDR", ignoreCase = true)
        val formatter = when {
            decimals == 0 && isIdr -> idrIntFmt.get()!!
            decimals == 0 -> intlIntFmt.get()!!
            decimals == 2 && isIdr -> idrDec2Fmt.get()!!
            decimals == 2 -> intlDec2Fmt.get()!!
            else -> {
                val pattern = "#,##0.${"0".repeat(decimals)}"
                DecimalFormat(pattern, if (isIdr) idrSymbols else intlSymbols)
            }
        }
        val formattedNumber = formatter.format(absVal)

        val prefix = when {
            amount < -0.0001 -> "-"
            showSign && amount > 0.0001 -> "+"
            else -> ""
        }
        return "$prefix$symbol$formattedNumber"
    }

    fun formatCompact(
        amount: Double,
        currencyCode: String,
        isHidden: Boolean = false,
        showSign: Boolean = false
    ): String {
        if (isHidden) return "••••••"
        val absVal = abs(amount)
        val sign = when {
            amount < -0.0001 -> "-"
            showSign && amount > 0.0001 -> "+"
            else -> ""
        }
        val currency = findCurrency(currencyCode)
        val isIdr = currency == SupportedCurrency.IDR || currencyCode.equals("IDR", ignoreCase = true)
        val symbol = currency?.symbol ?: "$currencyCode "

        return if (isIdr) {
            when {
                absVal >= 1_000_000_000_000 -> "${sign}${symbol}${formatDecimal(absVal / 1_000_000_000_000, 2, idrSymbols)} T"
                absVal >= 1_000_000_000 -> "${sign}${symbol}${formatDecimal(absVal / 1_000_000_000, 2, idrSymbols)} M"
                absVal >= 1_000_000 -> "${sign}${symbol}${formatDecimal(absVal / 1_000_000, 1, idrSymbols)} jt"
                else -> formatAmount(amount, currencyCode, showSign = showSign)
            }
        } else {
            when {
                absVal >= 1_000_000_000 -> "${sign}${symbol}${formatDecimal(absVal / 1_000_000_000, 2, intlSymbols)}B"
                absVal >= 1_000_000 -> "${sign}${symbol}${formatDecimal(absVal / 1_000_000, 2, intlSymbols)}M"
                absVal >= 10_000 -> "${sign}${symbol}${formatDecimal(absVal / 1_000, 1, intlSymbols)}K"
                else -> formatAmount(amount, currencyCode, showSign = showSign)
            }
        }
    }

    fun formatPreferenceAmount(
        amount: Double,
        currencyCode: String,
        isHidden: Boolean,
        useCompact: Boolean,
        showSign: Boolean = false
    ): String {
        return if (useCompact) {
            formatCompact(amount, currencyCode, isHidden = isHidden, showSign = showSign)
        } else {
            formatAmount(amount, currencyCode, isHidden = isHidden, showSign = showSign)
        }
    }

    fun calculateWeightedAverageCost(
        existingQuantity: Double,
        existingAvgCost: Double?,
        addedQuantity: Double,
        newBuyPrice: Double?
    ): Double? {
        val totalQty = existingQuantity + addedQuantity
        if (totalQty <= 0.0) return existingAvgCost
        return when {
            existingAvgCost != null && existingAvgCost > 0.0 && newBuyPrice != null && newBuyPrice > 0.0 ->
                ((existingQuantity * existingAvgCost) + (addedQuantity * newBuyPrice)) / totalQty
            newBuyPrice != null && newBuyPrice > 0.0 -> newBuyPrice
            else -> existingAvgCost
        }
    }

    data class SellCalculation(
        val remainingQuantity: Double,
        val soldAll: Boolean,
        val proceedsNative: Double,
        val realizedGainNative: Double?,
        val realizedGainPercent: Double?
    )

    fun calculateSellOutcome(
        existingQuantity: Double,
        existingAvgCost: Double?,
        sellQuantity: Double,
        sellPricePerUnit: Double
    ): SellCalculation {
        val clampedSell = sellQuantity.coerceIn(0.0, existingQuantity)
        val remaining = (existingQuantity - clampedSell).coerceAtLeast(0.0)
        val soldAll = remaining <= 1e-8
        val proceeds = clampedSell * sellPricePerUnit
        val realizedGain: Double?
        val realizedPct: Double?
        if (existingAvgCost != null && existingAvgCost > 0.0 && clampedSell > 0.0) {
            val costOfSold = clampedSell * existingAvgCost
            realizedGain = proceeds - costOfSold
            realizedPct = (realizedGain / costOfSold) * 100.0
        } else {
            realizedGain = null
            realizedPct = null
        }
        return SellCalculation(
            remainingQuantity = if (soldAll) 0.0 else remaining,
            soldAll = soldAll,
            proceedsNative = proceeds,
            realizedGainNative = realizedGain,
            realizedGainPercent = realizedPct
        )
    }

    fun unitQuotePerEnteredUnit(
        assetClass: AssetClass,
        unitType: UnitType,
        marketQuoteNative: Double
    ): Double {
        return if (assetClass == AssetClass.COMMODITY && unitType == UnitType.GRAMS) {
            marketQuoteNative / GRAMS_PER_TROY_OZ
        } else {
            marketQuoteNative
        }
    }

    private fun formatDecimal(value: Double, decimals: Int, symbols: DecimalFormatSymbols): String {
        val formatter = when {
            symbols === idrSymbols && decimals == 1 -> idrDec1Fmt.get()!!
            symbols === idrSymbols && decimals == 2 -> idrDec2Fmt.get()!!
            symbols === intlSymbols && decimals == 1 -> intlDec1Fmt.get()!!
            symbols === intlSymbols && decimals == 2 -> intlDec2Fmt.get()!!
            else -> DecimalFormat("#,##0.${"0".repeat(decimals)}", symbols)
        }
        return formatter.format(value)
    }

    fun formatPercent(percent: Double, showSign: Boolean = true): String {
        val prefix = when {
            percent > 0.005 && showSign -> "+"
            percent < -0.005 -> "-"
            else -> ""
        }
        return "$prefix${percentFmt.get()!!.format(abs(percent))}%"
    }

    fun formatOneDecimal(value: Double): String = oneDecFmt.get()!!.format(value)

    fun formatTwoDecimals(value: Double): String = percentFmt.get()!!.format(value)

    fun formatQuantity(quantity: Double, unitType: UnitType): String {
        val formatted = if (quantity % 1.0 == 0.0) {
            intlIntFmt.get()!!.format(quantity)
        } else {
            intlQtyFmt.get()!!.format(quantity)
        }
        return when (unitType) {
            UnitType.SHARES -> "$formatted sh"
            UnitType.TROY_OZ -> "$formatted oz"
            UnitType.GRAMS -> "$formatted g"
            UnitType.CURRENCY_UNIT -> formatted
        }
    }

    fun formatRelativeTime(timestampMs: Long?, nowMs: Long = System.currentTimeMillis()): String {
        if (timestampMs == null || timestampMs <= 0L) return "Not synced yet"
        val diffSec = ((nowMs - timestampMs) / 1000L).coerceAtLeast(0L)
        return when {
            diffSec < 45 -> "Just now"
            diffSec < 3600 -> "${diffSec / 60}m ago"
            diffSec < 86400 -> "${diffSec / 3600}h ago"
            else -> "${diffSec / 86400}d ago"
        }
    }

    fun formatPlainInputNumber(value: Double): String {
        if (!value.isFinite() || value == 0.0) return "0"
        val decimals = if (abs(value) < 0.01) 8 else 4
        val bd = BigDecimal.valueOf(value)
            .setScale(decimals, RoundingMode.HALF_UP)
            .stripTrailingZeros()
        return if (bd.compareTo(BigDecimal.ZERO) == 0) "0" else bd.toPlainString()
    }

    fun utcDatePickerMillisToLocalNoon(
        utcMidnightMillis: Long,
        localTz: TimeZone = TimeZone.getDefault()
    ): Long {
        val utcCal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            timeInMillis = utcMidnightMillis
        }
        val localCal = Calendar.getInstance(localTz).apply {
            clear()
            set(
                utcCal.get(Calendar.YEAR),
                utcCal.get(Calendar.MONTH),
                utcCal.get(Calendar.DAY_OF_MONTH),
                12,
                0,
                0
            )
            set(Calendar.MILLISECOND, 0)
        }
        return localCal.timeInMillis
    }

    fun localMillisToUtcDatePickerMillis(
        localMillis: Long,
        localTz: TimeZone = TimeZone.getDefault()
    ): Long {
        val localCal = Calendar.getInstance(localTz).apply {
            timeInMillis = localMillis
        }
        val utcCal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            clear()
            set(
                localCal.get(Calendar.YEAR),
                localCal.get(Calendar.MONTH),
                localCal.get(Calendar.DAY_OF_MONTH),
                0,
                0,
                0
            )
            set(Calendar.MILLISECOND, 0)
        }
        return utcCal.timeInMillis
    }

    fun utcTodayEndMillis(
        nowMillis: Long = System.currentTimeMillis(),
        localTz: TimeZone = TimeZone.getDefault()
    ): Long {
        val localCal = Calendar.getInstance(localTz).apply {
            timeInMillis = nowMillis
        }
        val utcCal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            clear()
            set(
                localCal.get(Calendar.YEAR),
                localCal.get(Calendar.MONTH),
                localCal.get(Calendar.DAY_OF_MONTH),
                23,
                59,
                59
            )
            set(Calendar.MILLISECOND, 999)
        }
        return utcCal.timeInMillis
    }

    fun formatDate(epochMillis: Long): String {
        synchronized(dateCache) {
            dateCache[epochMillis]?.let { return it }
        }
        val formatted = dateFmt.get()!!.format(java.util.Date(epochMillis))
        synchronized(dateCache) {
            dateCache[epochMillis] = formatted
        }
        return formatted
    }

    fun formatDateTime(epochMillis: Long): String {
        return dateTimeFmt.get()!!.format(java.util.Date(epochMillis))
    }

    fun formatShortDate(timestampSec: Long): String {
        return shortDateFmt.get()!!.format(java.util.Date(timestampSec * 1000L))
    }

    fun formatTime(timestampSec: Long): String {
        return timeFmt.get()!!.format(java.util.Date(timestampSec * 1000L))
    }

    fun formatMonthYear(timestampSec: Long): String {
        return monthYearFmt.get()!!.format(java.util.Date(timestampSec * 1000L))
    }

    fun formatChartAxisDate(timestampSec: Long, timeframe: ChartTimeframe): String = when (timeframe) {
        ChartTimeframe.ONE_DAY -> formatTime(timestampSec)
        ChartTimeframe.FIVE_YEARS, ChartTimeframe.ALL -> formatMonthYear(timestampSec)
        ChartTimeframe.YEAR -> formatDate(timestampSec * 1000L)
        else -> formatShortDate(timestampSec)
    }

    fun formatChartScrubDate(timestampSec: Long, timeframe: ChartTimeframe): String = when (timeframe) {
        ChartTimeframe.ONE_DAY, ChartTimeframe.FIVE_DAYS -> formatDateTime(timestampSec * 1000L)
        else -> formatDate(timestampSec * 1000L)
    }

    fun formatHeldDuration(buyDateMillis: Long, nowMillis: Long = System.currentTimeMillis()): String {
        val days = ((nowMillis - buyDateMillis) / 86_400_000L).coerceAtLeast(0L)
        return when {
            days == 0L -> "Today"
            days == 1L -> "1 day"
            days < 30L -> "$days days"
            days < 365L -> "${days / 30} mo"
            else -> {
                val years = days / 365
                val remMonths = (days % 365) / 30
                if (remMonths > 0) "${years}y ${remMonths}m" else "${years}y"
            }
        }
    }

    /**
     * Converts an amount from [fromCurrency] to [toCurrency] given a map of currency -> ratePerUsd.
     */
    fun convert(
        amount: Double,
        fromCurrency: String,
        toCurrency: String,
        ratesPerUsd: Map<String, Double>
    ): Double {
        if (fromCurrency == toCurrency || fromCurrency.equals(toCurrency, ignoreCase = true)) {
            return amount
        }
        val fromRate = ratesPerUsd[fromCurrency]
            ?: ratesPerUsd[fromCurrency.uppercase(Locale.US)]
            ?: defaultFallbackRatePerUsd(fromCurrency)
        val toRate = ratesPerUsd[toCurrency]
            ?: ratesPerUsd[toCurrency.uppercase(Locale.US)]
            ?: defaultFallbackRatePerUsd(toCurrency)
        if (fromRate <= 0.0) return amount
        val amountInUsd = amount / fromRate
        return amountInUsd * toRate
    }

    fun defaultFallbackRatePerUsd(currencyCode: String): Double = when (currencyCode.uppercase(Locale.US)) {
        "USD" -> 1.0
        "IDR" -> 15850.0
        "SGD" -> 1.34
        "EUR" -> 0.92
        "GBP" -> 0.79
        "JPY" -> 154.0
        "AUD" -> 1.53
        "MYR" -> 4.46
        "CHF" -> 0.88
        "HKD" -> 7.78
        else -> 1.0
    }
}

object CuratedAssets {
    val presets: List<AssetPreset> = listOf(
        AssetPreset("AAPL", "Apple Inc.", AssetClass.STOCK, UnitType.SHARES, "USD", "US Equity · NASDAQ"),
        AssetPreset("NVDA", "NVIDIA Corp.", AssetClass.STOCK, UnitType.SHARES, "USD", "US Equity · NASDAQ"),
        AssetPreset("BBCA.JK", "Bank Central Asia", AssetClass.STOCK, UnitType.SHARES, "IDR", "IDX Bluechip · IDR"),
        AssetPreset("BBRI.JK", "Bank Rakyat Indonesia", AssetClass.STOCK, UnitType.SHARES, "IDR", "IDX Bluechip · IDR"),
        AssetPreset("VOO", "Vanguard S&P 500 ETF", AssetClass.ETF, UnitType.SHARES, "USD", "US Index ETF · NYSE"),
        AssetPreset("VT", "Vanguard Total World Stock Index ETF", AssetClass.ETF, UnitType.SHARES, "USD", "Global Equity ETF · NYSE"),
        AssetPreset("QQQ", "Invesco QQQ Trust", AssetClass.ETF, UnitType.SHARES, "USD", "Nasdaq 100 ETF · NASDAQ"),
        AssetPreset("VWRA.L", "Vanguard FTSE All-World", AssetClass.ETF, UnitType.SHARES, "USD", "Global ETF · LSE"),
        AssetPreset("GC=F", "Gold Futures (Spot Proxy)", AssetClass.COMMODITY, UnitType.GRAMS, "USD", "Precious Metal · oz or g"),
        AssetPreset("SI=F", "Silver Futures", AssetClass.COMMODITY, UnitType.TROY_OZ, "USD", "Precious Metal · oz or g"),
        AssetPreset("CL=F", "WTI Crude Oil", AssetClass.COMMODITY, UnitType.SHARES, "USD", "Energy Commodity · bbl"),
        AssetPreset("BZ=F", "Brent Crude Oil", AssetClass.COMMODITY, UnitType.SHARES, "USD", "Energy Commodity · bbl"),
        AssetPreset("NG=F", "Natural Gas Futures", AssetClass.COMMODITY, UnitType.SHARES, "USD", "Energy Commodity · MMBtu"),
        AssetPreset("CASH-IDR", "Indonesian Rupiah Cash", AssetClass.CASH, UnitType.CURRENCY_UNIT, "IDR", "Bank / Savings · IDR"),
        AssetPreset("CASH-USD", "US Dollar Cash", AssetClass.CASH, UnitType.CURRENCY_UNIT, "USD", "Brokerage / Bank · USD")
    )
}
