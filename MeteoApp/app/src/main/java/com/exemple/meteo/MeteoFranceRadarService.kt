package com.exemple.meteo

import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.Header

interface MeteoFranceRadarService {
    @GET("v1/donneespubliques/radar/mosaique")
    suspend fun fetchLatestMosaic(
        @Header("Authorization") bearerToken: String
    ): Response<ResponseBody>

    companion object {
        fun create(): MeteoFranceRadarService {
            return Retrofit.Builder()
                .baseUrl("https://portail-api.meteofrance.fr/")
                .build()
                .create(MeteoFranceRadarService::class.java)
        }
    }
}
