package com.exemple.meteo

import retrofit2.http.GET
import retrofit2.http.Query

interface GeocodingService {
    @GET("v1/search")
    suspend fun searchCity(
        @Query("name") cityName: String,
        @Query("count") count: Int = 1,
        @Query("language") language: String = "fr",
        @Query("format") format: String = "json"
    ): GeocodingResponse
}

data class GeocodingResponse(
    val results: List<CityResult>?
)

data class CityResult(
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val country: String?
)
