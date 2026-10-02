package com.exemple.meteo

import retrofit2.http.GET

interface RainViewerService {
    @GET("public/weather-maps.json")
    suspend fun getWeatherMaps(): RainViewerResponse
}

data class RainViewerResponse(
    val host: String = "",
    val radar: RadarData = RadarData()
)

data class RadarData(
    val past: List<RadarFrame> = emptyList(),
    val nowcast: List<RadarFrame> = emptyList()
)

data class RadarFrame(
    val time: Long = 0,
    val path: String = ""
)
