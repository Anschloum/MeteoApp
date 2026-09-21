package com.exemple.meteo // Adaptez le nom de votre package ici

import retrofit2.http.GET

data class RainViewerResponse(
    val host: String,
    val radar: RadarData
)

data class RadarData(
    val past: List<RadarFrame>
)

data class RadarFrame(
    val path: String
)

interface RainViewerService {
    @GET("public/weather-maps.json")
    suspend fun getWeatherMaps(): RainViewerResponse
}
data class RainViewerResponse(
    val host: String,
    val radar: RadarData
)

data class RadarData(
    val past: List<RadarFrame>,
    val nowcast: List<RadarFrame>
)

data class RadarFrame(
    val time: Long,
    val path: String
)
