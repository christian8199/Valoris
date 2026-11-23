package com.example.data.remote

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class YahooChartResponse(
    val chart: YahooChartContainer?
)

@JsonClass(generateAdapter = true)
data class YahooChartContainer(
    val result: List<YahooChartResult>?
)

@JsonClass(generateAdapter = true)
data class YahooChartResult(
    val meta: YahooChartMeta?,
    val timestamp: List<Long>?,
    val indicators: YahooIndicators?
)

@JsonClass(generateAdapter = true)
data class YahooChartMeta(
    val currency: String?,
    val symbol: String?,
    val exchangeName: String?,
    val instrumentType: String?,
    val shortName: String?,
    val longName: String?,
    val regularMarketPrice: Double?,
    val regularMarketTime: Long? = null,
    val preMarketPrice: Double? = null,
    val postMarketPrice: Double? = null,
    val chartPreviousClose: Double?,
    val previousClose: Double?,
    val marketState: String?,
    val currentTradingPeriod: YahooTradingPeriods? = null
)

@JsonClass(generateAdapter = true)
data class YahooTradingPeriods(
    val pre: YahooTradingPeriodWindow? = null,
    val regular: YahooTradingPeriodWindow? = null,
    val post: YahooTradingPeriodWindow? = null
)

@JsonClass(generateAdapter = true)
data class YahooTradingPeriodWindow(
    val start: Long? = null,
    val end: Long? = null
)

@JsonClass(generateAdapter = true)
data class YahooIndicators(
    val quote: List<YahooQuoteIndicator>?
)

@JsonClass(generateAdapter = true)
data class YahooQuoteIndicator(
    val close: List<Double?>?
)

@JsonClass(generateAdapter = true)
data class YahooSearchResponse(
    val quotes: List<YahooSearchQuote>?
)

@JsonClass(generateAdapter = true)
data class YahooSearchQuote(
    val symbol: String?,
    val shortname: String?,
    val longname: String?,
    val quoteType: String?,
    val exchange: String?,
    val exchDisp: String?
)

@JsonClass(generateAdapter = true)
data class OpenErApiResponse(
    val result: String?,
    @Json(name = "base_code") val baseCode: String?,
    val rates: Map<String, Double>?
)

@JsonClass(generateAdapter = true)
data class FrankfurterResponse(
    val base: String?,
    val date: String?,
    val rates: Map<String, Double>?
)
