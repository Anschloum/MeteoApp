package com.exemple.meteo

import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query

interface MeteoFranceRadarService {

    @GET("donnees-publiques/v1/radar/mosaique")
    suspend fun fetchLatestMosaic(
        @Header("Authorization") bearerToken: String,
        @Query("zone") zone: String = "METROPOLE",
        @Query("produit") produit: String = "REFLECTIVITE"
    ): Response<ResponseBody>
}
