package com.example.data.remote

import com.squareup.moshi.Moshi
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

object NetworkModule {
    private const val YAHOO_BASE_URL = "https://query1.finance.yahoo.com/"
    private const val OPEN_ER_BASE_URL = "https://open.er-api.com/"
    private const val FRANKFURTER_BASE_URL = "https://api.frankfurter.dev/"

    private val moshi: Moshi by lazy {
        Moshi.Builder().build()
    }

    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectionPool(ConnectionPool(6, 5, TimeUnit.MINUTES))
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header(
                        "User-Agent",
                        "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36"
                    )
                    .header("Accept", "application/json")
                    .build()
                chain.proceed(request)
            }
            .build()
    }

    val marketApiService: MarketApiService by lazy {
        Retrofit.Builder()
            .baseUrl(YAHOO_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(MarketApiService::class.java)
    }

    val openErFxService: OpenErFxService by lazy {
        Retrofit.Builder()
            .baseUrl(OPEN_ER_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(OpenErFxService::class.java)
    }

    val frankfurterFxService: FrankfurterFxService by lazy {
        Retrofit.Builder()
            .baseUrl(FRANKFURTER_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(FrankfurterFxService::class.java)
    }
}
