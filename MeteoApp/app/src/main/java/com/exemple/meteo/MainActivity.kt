package com.exemple.meteo // Remplacez par le nom de votre package

import android.os.Bundle
import android.util.Base64
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST

// --- MODÈLES ET SERVICES AUTHENTIFICATION ---

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

class MeteoFranceTokenManager(
    private val authService: MeteoFranceAuthService,
    private val consumerKey: String,
    private val consumerSecret: String
) {
    private var cachedToken: String? = null
    private var tokenExpirationTimeMs: Long = 0

    suspend fun getValidToken(): String? {
        val currentTime = System.currentTimeMillis()

        // Réutilisation du token s'il reste plus de 60 secondes de validité
        if (cachedToken != null && currentTime < (tokenExpirationTimeMs - 60_000)) {
            return cachedToken
        }

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

// --- SERVICE API RADAR ---

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

// --- MAIN ACTIVITY ---

class MainActivity : AppCompatActivity() {

    private val consumerKey = "SxmEZh3U2pIniTws1NQu7u0S4o4a"
    private val consumerSecret = "kXbE4mb8QI7_ETz_dAXeB2qTS4Ma"

    private val tokenManager: MeteoFranceTokenManager by lazy {
        MeteoFranceTokenManager(
            authService = MeteoFranceAuthService.create(),
            consumerKey = consumerKey,
            consumerSecret = consumerSecret
        )
    }

    private val meteoFranceRadarService: MeteoFranceRadarService by lazy {
        MeteoFranceRadarService.create()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        loadMeteoFranceRadar()
    }

    private fun loadMeteoFranceRadar() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // 1. Obtention dynamique du token
                val token = tokenManager.getValidToken()
                if (token == null) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@MainActivity, "Échec d'obtention du token", Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }

                // 2. Requête API avec le token d'accès
                val response = meteoFranceRadarService.fetchLatestMosaic("Bearer $token")
                if (response.isSuccessful && response.body() != null) {
                    val rawBytes = response.body()!!.bytes()

                    // Insérer ici la logique de traitement binaire et de mise à jour de la carte

                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            this@MainActivity,
                            "Données radar reçues : ${rawBytes.size} octets",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            this@MainActivity,
                            "Erreur API : ${response.code()}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@MainActivity,
                        "Erreur réseau : ${e.localizedMessage}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }
}
