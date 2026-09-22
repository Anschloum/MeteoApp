package com.exemple.meteo

import com.google.gson.annotations.SerializedName
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.Header
import retrofit2.http.POST

data class TokenResponse(
    @SerializedName("access_token") val accessToken: String,
    @SerializedName("token_type") val tokenType: String,
    @SerializedName("expires_in") val expiresIn: Long
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
