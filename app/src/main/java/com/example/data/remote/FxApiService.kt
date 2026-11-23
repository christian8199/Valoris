package com.example.data.remote

import retrofit2.http.GET
import retrofit2.http.Query

interface OpenErFxService {
    @GET("v6/latest/USD")
    suspend fun getLatestUsdRates(): OpenErApiResponse
}

interface FrankfurterFxService {
    @GET("v1/latest")
    suspend fun getLatestRates(@Query("base") base: String = "USD"): FrankfurterResponse
}
