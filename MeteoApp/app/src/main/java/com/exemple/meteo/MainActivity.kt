package com.exemple.meteo

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.launch
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.tan

class MainActivity : AppCompatActivity() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient

    private lateinit var tvCity: TextView
    private lateinit var tvTemp: TextView
    private lateinit var citySearchEditText: EditText
    private lateinit var searchButton: Button
    private lateinit var gpsButton: Button
    private lateinit var btnRefresh: Button
    private lateinit var mapBackgroundImageView: ImageView
    private lateinit var radarImageView: ImageView
    private lateinit var forecastRecyclerView: RecyclerView
    private lateinit var hourlyRecyclerView: RecyclerView

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

    private val rainViewerService: RainViewerService by lazy {
        Retrofit.Builder()
            .baseUrl("https://api.rainviewer.com/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(RainViewerService::class.java)
    }

    private var currentLat = 48.8566
    private var currentLon = 2.3522

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        tvCity = findViewById(R.id.tvCity)
        tvTemp = findViewById(R.id.tvTemp)
        citySearchEditText = findViewById(R.id.citySearchEditText)
        searchButton = findViewById(R.id.searchButton)
        gpsButton = findViewById(R.id.gpsButton)
        btnRefresh = findViewById(R.id.btnRefresh)
        mapBackgroundImageView = findViewById(R.id.mapBackgroundImageView)
        radarImageView = findViewById(R.id.radarImageView)
        
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
                // Correction du nom de la méthode ici
                val forecastResponse = openMeteoService.get14DaysForecast(lat = lat, lon = lon)

                tvCity.text = cityName
                val maxTemp = forecastResponse.daily.temperature_2m_max.firstOrNull()
                tvTemp.text = if (maxTemp != null) "$maxTemp °C" else "-- °C"

                hourlyRecyclerView.adapter = HourlyForecastAdapter(forecastResponse.hourly)
                forecastRecyclerView.adapter = ForecastAdapter(forecastResponse.daily)

                loadRadarImage(lat, lon)

            } catch (e: Exception) {
                tvTemp.text = "Erreur"
                Toast.makeText(this@MainActivity, "Erreur de chargement des données", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private suspend fun loadRadarImage(lat: Double, lon: Double) {
        try {
            val mapsData = rainViewerService.getWeatherMaps()
            val latestFrame = mapsData.radar.past.lastOrNull()

            if (latestFrame != null) {
                val zoom = 6
                val x = floor((lon + 180.0) / 360.0 * (1 shl zoom)).toInt()
                val latRad = Math.toRadians(lat)
                val y = floor((1.0 - ln(tan(latRad) + 1.0 / Math.cos(latRad)) / PI) / 2.0 * (1 shl zoom)).toInt()

                val mapUrl = "https://tile.openstreetmap.org/$zoom/$x/$y.png"
                mapBackgroundImageView.load(mapUrl) {
                    addHeader("User-Agent", "MeteoApp/1.0")
                    crossfade(true)
                }

                val radarTileUrl = "${mapsData.host}${latestFrame.path}/256/$zoom/$x/$y/2/1_1.png"
                radarImageView.load(radarTileUrl) {
                    crossfade(true)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
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
