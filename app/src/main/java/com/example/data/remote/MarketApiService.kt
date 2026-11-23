package com.example.data.remote

import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface MarketApiService {
    @GET("v8/finance/chart/{symbol}")
    suspend fun getChartQuote(
        @Path("symbol") symbol: String,
        @Query("range") range: String = "1d",
        @Query("interval") interval: String = "15m",
        @Query("includePrePost") includePrePost: Boolean = false
    ): YahooChartResponse

    @GET("v1/finance/search")
    suspend fun searchSymbols(
        @Query("q") query: String,
        @Query("quotesCount") quotesCount: Int = 10,
        @Query("newsCount") newsCount: Int = 0
    ): YahooSearchResponse
}
