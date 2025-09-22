package com.example.billyapp.core.api

import retrofit2.http.Body
import retrofit2.http.POST

data class MatchReq(val uuid: String, val timestamp: Long)
data class MatchRes(val userId: String?, val name: String?)

interface MatchApi {
    @POST("match")
    suspend fun match(@Body req: MatchReq): MatchRes
}
