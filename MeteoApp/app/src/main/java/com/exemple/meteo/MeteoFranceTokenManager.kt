package com.exemple.meteo

import android.util.Base64

class MeteoFranceTokenManager(
    private val authService: MeteoFranceAuthService,
    private val consumerKey: String,
    private val consumerSecret: String
) {
    private var cachedToken: String? = null
    private var tokenExpirationTimeMs: Long = 0

    suspend fun getValidToken(): String? {
        val currentTime = System.currentTimeMillis()

        // Réutilisation du jeton s'il est encore valide (avec une marge de sécurité de 60 secondes)
        if (cachedToken != null && currentTime < (tokenExpirationTimeMs - 60_000)) {
            return cachedToken
        }

        // Sinon, génération d'un nouveau jeton
        val credentials = "$consumerKey:$consumerSecret"
        val basicAuthHeader = "Basic " + Base64.encodeToString(credentials.toByteArray(), Base64.NO_WRAP)

        return try {
            val response = authService.fetchToken(basicAuthHeader)
            if (response.isSuccessful && response.body() != null) {
                val tokenBody = response.body()!!
                cachedToken = tokenBody.accessToken
                tokenExpirationTimeMs = currentTime + (tokenBody.expiresIn * 1000)
                cachedToken
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
