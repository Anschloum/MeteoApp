package com.exemple.meteo

import retrofit2.http.GET
import retrofit2.http.Query

interface OpenMeteoService {
    @GET("v1/forecast")
    suspend fun get14DaysForecast(
        @Query("latitude") lat: Double = 48.8566,
        @Query("longitude") lon: Double = 2.3522,
        @Query("hourly") hourly: String = "temperature_2m,precipitation,wind_speed_10m",
        @Query("daily") daily: String = "temperature_2m_max,temperature_2m_min,precipitation_sum",
        @Query("forecast_days") days: Int = 14,
        @Query("timezone") timezone: String = "auto"
    ): ForecastResponse
}

data class ForecastResponse(
    val daily: DailyData,
    val hourly: HourlyData
)

data class DailyData(
    val time: List<String>,
    val temperature_2m_max: List<Double>,
    val temperature_2m_min: List<Double>,
    val precipitation_sum: List<Double>
)

data class HourlyData(
    val time: List<String>,
    val temperature_2m: List<Double>,
    val precipitation: List<Double>,
    val wind_speed_10m: List<Double>
)
