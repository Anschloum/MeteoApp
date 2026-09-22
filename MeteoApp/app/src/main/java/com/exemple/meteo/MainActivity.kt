package com.exemple.meteo // Remplacez par le nom exact de votre package

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Base64
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.ResponseBody
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST

// --- AUTHENTIFICATION MÉTÉO-FRANCE ---

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

// --- API RADAR MÉTÉO-FRANCE ---

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

    private lateinit var fusedLocationClient: FusedLocationProviderClient

    private lateinit var tvCity: TextView
    private lateinit var tvTemp: TextView
    private lateinit var citySearchEditText: EditText
    private lateinit var searchButton: Button
    private lateinit var gpsButton: Button
    private lateinit var btnRefresh: Button
    private lateinit var mapView: MapView
    private lateinit var forecastRecyclerView: RecyclerView
    private lateinit var hourlyRecyclerView: RecyclerView

    private lateinit var radarOverlayManager: RadarMapOverlayManager

    // Identifiants Météo-France
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

    private val geocodingService: GeocodingService by lazy {
        Retrofit.Builder()
            .baseUrl("https://geocoding-api.open-meteo.com/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(GeocodingService::class.java)
    }

    private val openMeteoService: OpenMeteoService by lazy {
        Retrofit.Builder()
            .baseUrl("https://api.open-meteo.com/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(OpenMeteoService::class.java)
    }

    private var currentLat = 48.8566
    private var currentLon = 2.3522

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        Configuration.getInstance().userAgentValue = packageName

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        tvCity = findViewById(R.id.tvCity)
        tvTemp = findViewById(R.id.tvTemp)
        citySearchEditText = findViewById(R.id.citySearchEditText)
        searchButton = findViewById(R.id.searchButton)
        gpsButton = findViewById(R.id.gpsButton)
        btnRefresh = findViewById(R.id.btnRefresh)

        mapView = findViewById(R.id.mapView)
        mapView.setTileSource(TileSourceFactory.MAPNIK)
        mapView.setMultiTouchControls(true)
        mapView.minZoomLevel = 4.0
        mapView.maxZoomLevel = 18.0

        radarOverlayManager = RadarMapOverlayManager(mapView)

        mapView.setOnTouchListener { v, _ ->
            v.parent?.requestDisallowInterceptTouchEvent(true)
            false
        }

        forecastRecyclerView = findViewById(R.id.forecastRecyclerView)
        forecastRecyclerView.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)

        hourlyRecyclerView = findViewById(R.id.hourlyRecyclerView)
        hourlyRecyclerView.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)

        searchButton.setOnClickListener {
            val cityName = citySearchEditText.text.toString().trim()
            if (cityName.isNotEmpty()) {
                searchCityAndFetchWeather(cityName)
            } else {
                Toast.makeText(this, "Veuillez saisir une ville", Toast.LENGTH_SHORT).show()
            }
        }

        gpsButton.setOnClickListener {
            fetchLocationAndWeather()
        }

        btnRefresh.setOnClickListener {
            fetchWeather(currentLat, currentLon, tvCity.text.toString())
        }

        fetchWeather(currentLat, currentLon, "Paris")
    }

    private fun fetchLocationAndWeather() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ),
                LOCATION_PERMISSION_REQ_CODE
            )
            return
        }

        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            if (location != null) {
                currentLat = location.latitude
                currentLon = location.longitude
                fetchWeather(currentLat, currentLon, "Ma position")
            } else {
                Toast.makeText(this, "Impossible de récupérer la position GPS", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun searchCityAndFetchWeather(cityName: String) {
        lifecycleScope.launch {
            try {
                val response = geocodingService.searchCity(cityName)
                val cityResult = response.results?.firstOrNull()

                if (cityResult != null) {
                    currentLat = cityResult.latitude
                    currentLon = cityResult.longitude
                    fetchWeather(currentLat, currentLon, cityResult.name)
                } else {
                    Toast.makeText(this@MainActivity, "Ville introuvable", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "Erreur lors de la recherche", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun fetchWeather(lat: Double, lon: Double, cityName: String) {
        lifecycleScope.launch {
            try {
                val forecastResponse = openMeteoService.get14DaysForecast(lat = lat, lon = lon)

                tvCity.text = cityName
                val maxTemp = forecastResponse.daily.temperature_2m_max.firstOrNull()
                tvTemp.text = if (maxTemp != null) "$maxTemp °C" else "-- °C"

                hourlyRecyclerView.adapter = HourlyForecastAdapter(forecastResponse.hourly)
                forecastRecyclerView.adapter = ForecastAdapter(forecastResponse.daily)

                mapView.controller.setZoom(8.8)
                mapView.controller.setCenter(GeoPoint(lat, lon))

                loadMeteoFranceRadar()

            } catch (e: Exception) {
                tvTemp.text = "Erreur"
                Toast.makeText(this@MainActivity, "Erreur de chargement des données", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun loadMeteoFranceRadar() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val token = tokenManager.getValidToken()
                if (token == null) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@MainActivity, "Échec d'authentification Météo-France", Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }

                val response = meteoFranceRadarService.fetchLatestMosaic("Bearer $token")
                if (response.isSuccessful && response.body() != null) {
                    val rawBytes = response.body()!!.bytes()

                    val width = 512
                    val height = 512
                    val grid = Array(height) { FloatArray(width) }

                    val bitmap = RadarPostProcessor.convertGridToBitmap(grid, width, height)

                    withContext(Dispatchers.Main) {
                        radarOverlayManager.updateRadarOverlay(bitmap)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        mapView.onResume()
    }

    override fun onPause() {
        super.onPause()
        mapView.onPause()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_PERMISSION_REQ_CODE &&
            grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED
        ) {
            fetchLocationAndWeather()
        }
    }

    companion object {
        private const val LOCATION_PERMISSION_REQ_CODE = 1001
    }
}
