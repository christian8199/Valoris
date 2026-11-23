package com.example.domain.model

import androidx.compose.runtime.Immutable

enum class AssetClass(val displayName: String, val singularName: String) {
    STOCK("Stocks", "Stock"),
    ETF("ETFs", "ETF"),
    CASH("Cash", "Cash"),
    COMMODITY("Commodities", "Commodity")
}

enum class UnitType(val label: String, val shortLabel: String) {
    SHARES("Shares", "sh"),
    CURRENCY_UNIT("Currency", "units"),
    TROY_OZ("Troy Ounces", "oz"),
    GRAMS("Grams", "g")
}

enum class RefreshInterval(val label: String, val minutes: Int, val millis: Long) {
    MANUAL("Manual only", 0, 0L),
    ONE_MINUTE("Every 1 min", 1, 60_000L),
    FIVE_MINUTES("Every 5 min", 5, 300_000L),
    FIFTEEN_MINUTES("Every 15 min", 15, 900_000L);

    companion object {
        fun fromMinutes(minutes: Int): RefreshInterval =
            entries.firstOrNull { it.minutes == minutes } ?: FIVE_MINUTES
    }
}

enum class ThemeMode(val label: String) {
    SYSTEM("System Default"),
    LIGHT("Light (Alabaster)"),
    DARK("Dark (Obsidian)");

    companion object {
        fun fromName(name: String?): ThemeMode =
            entries.firstOrNull { it.name == name } ?: SYSTEM
    }
}

enum class SupportedCurrency(
    val code: String,
    val symbol: String,
    val displayName: String,
    val decimals: Int
) {
    IDR("IDR", "Rp", "Indonesian Rupiah", 0),
    USD("USD", "$", "US Dollar", 2),
    SGD("SGD", "S$", "Singapore Dollar", 2),
    EUR("EUR", "€", "Euro", 2),
    GBP("GBP", "£", "British Pound", 2),
    JPY("JPY", "¥", "Japanese Yen", 0),
    AUD("AUD", "A$", "Australian Dollar", 2),
    MYR("MYR", "RM", "Malaysian Ringgit", 2),
    CHF("CHF", "CHF ", "Swiss Franc", 2),
    HKD("HKD", "HK$", "Hong Kong Dollar", 2);

    companion object {
        fun fromCode(code: String): SupportedCurrency =
            entries.firstOrNull { it.code.equals(code, ignoreCase = true) } ?: IDR
    }
}

enum class QuoteStatus {
    LIVE,
    CACHED,
    MANUAL,
    CASH_PAR
}

enum class MarketStatus(val label: String) {
    OPEN("Open"),
    CLOSED("Closed")
}

enum class SortOption(val label: String) {
    VALUE_DESC("Highest Value"),
    CHANGE_DESC("Top Gainers"),
    NAME_ASC("Alphabetical")
}

enum class ChartTimeframe(val label: String, val range: String, val interval: String) {
    ONE_DAY("1D", "1d", "15m"),
    FIVE_DAYS("5D", "5d", "1h"),
    MONTH("1M", "1mo", "1d"),
    QUARTER("3M", "3mo", "1d"),
    YTD("YTD", "ytd", "1d"),
    YEAR("1Y", "1y", "1wk"),
    FIVE_YEARS("5Y", "5y", "1wk"),
    ALL("ALL", "max", "1mo");

    companion object {
        fun fromName(name: String?): ChartTimeframe =
            entries.firstOrNull { it.name == name } ?: MONTH
    }
}

@Immutable
data class Holding(
    val id: Long = 0L,
    val symbol: String,
    val name: String,
    val assetClass: AssetClass,
    val quantity: Double,
    val unitType: UnitType,
    val averageCostPerUnit: Double? = null,
    val nativeCurrency: String,
    val manualPriceOverride: Double? = null,
    val accountLabel: String = "",
    val buyDateMillis: Long? = null,
    val updatedAt: Long = System.currentTimeMillis()
)

@Immutable
data class ValuedHolding(
    val holding: Holding,
    val unitQuoteNative: Double,
    val previousCloseNative: Double,
    val dayChangePercent: Double,
    val totalValueNative: Double,
    val totalValueDisplay: Double,
    val totalValueUsd: Double,
    val dayChangeDisplay: Double,
    val unrealizedGainDisplay: Double?,
    val unrealizedGainPercent: Double?,
    val portfolioWeightPercent: Double,
    val fxRateToDisplay: Double,
    val quoteStatus: QuoteStatus,
    val marketStatus: MarketStatus? = null,
    val isUsingExtendedHoursPrice: Boolean = false,
    val quoteUpdatedAt: Long?,
    val sparklinePoints: List<Double>
)

@Immutable
data class AllocationSlice(
    val assetClass: AssetClass,
    val valueDisplay: Double,
    val percentage: Double
)

@Immutable
data class FxRateDisplay(
    val currencyCode: String,
    val currencyName: String,
    val rateInBaseCurrency: Double,
    val fetchedAt: Long,
    val source: String
)

@Immutable
data class SearchSuggestion(
    val symbol: String,
    val name: String,
    val assetClass: AssetClass,
    val exchange: String,
    val currency: String = "USD"
)

@Immutable
data class AssetPreset(
    val symbol: String,
    val name: String,
    val assetClass: AssetClass,
    val unitType: UnitType,
    val nativeCurrency: String,
    val subtitle: String
)

@Immutable
data class PortfolioDashboardData(
    val displayCurrency: SupportedCurrency,
    val isBalanceHidden: Boolean,
    val refreshInterval: RefreshInterval,
    val themeMode: ThemeMode,
    val useCompactNumbers: Boolean,
    val showPnlOnCards: Boolean,
    val showExtendedHoursPrice: Boolean = false,
    val defaultChartTimeframe: ChartTimeframe = ChartTimeframe.MONTH,
    val appLockEnabled: Boolean = false,
    val biometricUnlockEnabled: Boolean = true,
    val totalValueDisplay: Double,
    val dayChangeDisplay: Double,
    val dayChangePercent: Double,
    val totalUnrealizedGainDisplay: Double?,
    val totalUnrealizedGainPercent: Double?,
    val valuedHoldings: List<ValuedHolding>,
    val allocationSlices: List<AllocationSlice>,
    val fxRatesList: List<FxRateDisplay>,
    val usdToDisplayRate: Double,
    val trendValuesDisplay: List<Double>,
    val lastSyncEpoch: Long?,
    val fxSourceLabel: String,
    val hasStaleQuotes: Boolean
)

enum class TransactionType(val label: String) {
    BUY("Buy"),
    SELL("Sell")
}

enum class MovementGraphMode(val label: String) {
    ASSET_VALUE("Asset Value"),
    PROFIT_LOSS("Profit & Loss")
}

enum class HistoryFilterType(val label: String) {
    ALL("All"),
    BUY("Buys"),
    SELL("Sells")
}

@Immutable
data class AssetTransaction(
    val id: Long = 0L,
    val holdingId: Long? = null,
    val symbol: String,
    val assetName: String,
    val assetClass: AssetClass,
    val type: TransactionType,
    val quantity: Double,
    val unitType: UnitType,
    val unitPriceNative: Double,
    val totalAmountNative: Double,
    val nativeCurrency: String,
    val realizedPnlNative: Double? = null,
    val depositedToCash: Boolean = false,
    val timestampMillis: Long,
    val note: String = ""
)

@Immutable
data class TransactionDisplayItem(
    val transaction: AssetTransaction,
    val totalAmountDisplay: Double,
    val realizedPnlDisplay: Double?
)

@Immutable
data class PortfolioHistorySummary(
    val items: List<TransactionDisplayItem> = emptyList(),
    val totalInvestedDisplay: Double = 0.0,
    val totalProceedsDisplay: Double = 0.0,
    val totalRealizedPnlDisplay: Double = 0.0,
    val buyCount: Int = 0,
    val sellCount: Int = 0
)

@Immutable
data class HistoricalPoint(
    val timestampSec: Long,
    val value: Double
)

@Immutable
data class PnlTimelinePoint(
    val timestampSec: Long,
    val totalReturnDisplay: Double,
    val unrealizedDisplay: Double,
    val realizedDisplay: Double
)

@Immutable
data class AssetMovementSeries(
    val symbol: String,
    val name: String,
    val assetClass: AssetClass,
    val nativeCurrency: String,
    val normalizedPercentPoints: List<HistoricalPoint>,
    val rawPricePoints: List<HistoricalPoint>,
    val periodChangePercent: Double,
    val latestPriceNative: Double
)

@Immutable
data class PortfolioMovementData(
    val timeframe: ChartTimeframe = ChartTimeframe.MONTH,
    val graphMode: MovementGraphMode = MovementGraphMode.ASSET_VALUE,
    val respectBuyDates: Boolean = true,
    val hasAnyBuyDates: Boolean = false,
    val totalTimeline: List<HistoricalPoint> = emptyList(),
    val pnlTimeline: List<PnlTimelinePoint> = emptyList(),
    val assetSeries: List<AssetMovementSeries> = emptyList(),
    val periodChangeDisplay: Double = 0.0,
    val periodChangePercent: Double = 0.0,
    val latestTotalReturnDisplay: Double = 0.0,
    val latestUnrealizedPnlDisplay: Double = 0.0,
    val latestRealizedPnlDisplay: Double = 0.0,
    val latestReturnPercent: Double = 0.0,
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)
