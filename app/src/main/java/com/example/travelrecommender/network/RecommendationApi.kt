package com.example.travelrecommender.network

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST

data class RecommendationRequestDto(
    val latitude: Double,
    val longitude: Double,
    val startDateTime: String,
    val availableMinutes: Int,
    val request: String
)

data class RecommendationDto(
    val name: String,
    val reason: String,
    val durationMinutes: Int
)

data class RecommendationResponseDto(val recommendations: List<RecommendationDto>)

interface RecommendationApi {
    @POST("recommendations")
    suspend fun recommendations(@Body request: RecommendationRequestDto): RecommendationResponseDto
}

object RecommendationNetwork {
    val api: RecommendationApi by lazy {
        Retrofit.Builder()
            .baseUrl("http://10.0.2.2:8080/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(RecommendationApi::class.java)
    }
}
