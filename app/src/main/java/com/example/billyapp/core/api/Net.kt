package com.example.billyapp.core.api

import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

object Net {
    // TODO: sostituisci con il tuo endpoint
    private const val BASE_URL = "https://example.com/"

    val api: MatchApi = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .addConverterFactory(MoshiConverterFactory.create())
        .build()
        .create(MatchApi::class.java)
}
