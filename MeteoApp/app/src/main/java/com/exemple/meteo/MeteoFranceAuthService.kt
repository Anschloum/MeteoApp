package com.exemple.meteo

import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.Header
import retrofit2.http.POST

data class TokenResponse(
    val access_token: String,
    val token_type: String,
    val expires_in: Long
)

interface MeteoFranceAuthService {
    @FormUrlEncoded
    @POST("token")
    suspend fun fetchToken(
        @Header("Authorization") basicAuthHeader: String,
        @Field("grant_type") grantType: String = "client_credentials"
    ): Response<TokenResponse>

    companion object {
        fun create(): MeteoFranceAuthService {
            return Retrofit.Builder()
                .baseUrl("https://portail-api.meteofrance.fr/")
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(MeteoFranceAuthService::class.java)
        }
    }
}
