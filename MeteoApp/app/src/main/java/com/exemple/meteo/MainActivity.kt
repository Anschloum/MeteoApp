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
import coil.load
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.launch
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class MainActivity : AppCompatActivity() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient

    private lateinit var tvCity: TextView
    private lateinit var tvTemp: TextView
    private lateinit var citySearchEditText: EditText
    private lateinit var searchButton: Button
    private lateinit var gpsButton: Button
    private lateinit var btnRefresh: Button
    private lateinit var radarImageView: ImageView

    // Instance Retrofit pour l'API de Geocoding (Recherche de ville)
    private val geocodingApi: GeocodingService by lazy {
        Retrofit.Builder()
            .baseUrl("https://geocoding-api.open-meteo.com/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(GeocodingService::class.java)
    }

    // Instance Retrofit pour l'API Open-Meteo (Prévisions)
    private val openMeteoApi: OpenMeteoService by lazy {
        Retrofit.Builder()
            .baseUrl("https://api.open-meteo.com/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(OpenMeteoService::class.java)
    }

    // Coordonnées courantes (Paris par défaut)
    private var currentLat = 48.8566
    private var currentLon = 2.3522

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialisation du client GPS
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // Liaison des composants graphiques du layout XML
        tvCity = findViewById(R.id.tvCity)
        tvTemp = findViewById(R.id.tvTemp)
        citySearchEditText = findViewById(R.id.citySearchEditText)
        searchButton = findViewById(R.id.searchButton)
        gpsButton = findViewById(R.id.gpsButton)
        btnRefresh = findViewById(R.id.btnRefresh)
        radarImageView = findViewById(R.id.radarImageView)

        // Action : Recherche manuelle par ville
        searchButton.setOnClickListener {
            val cityName = citySearchEditText.text.toString().trim()
            if (cityName.isNotEmpty()) {
                searchCityAndFetchWeather(cityName)
            } else {
                Toast.makeText(this, "Veuillez saisir une ville", Toast.LENGTH_SHORT).show()
            }
        }

        // Action : Géolocalisation GPS
        gpsButton.setOnClickListener {
            fetchLocationAndWeather()
        }

        // Action : Bouton Actualiser
        btnRefresh.setOnClickListener {
            fetchWeather(currentLat, currentLon, tvCity.text.toString())
        }

        // Chargement initial des données météo
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
                val response = geocodingApi.searchCity(cityName)
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
                val response = openMeteoApi.get14DaysForecast(lat = lat, lon = lon)
                
                tvCity.text = cityName
                val maxTemp = response.daily.temperature_2m_max.firstOrNull()
                tvTemp.text = if (maxTemp != null) "$maxTemp °C" else "-- °C"

                loadRadarImage()

            } catch (e: Exception) {
                tvTemp.text = "Erreur"
                Toast.makeText(this@MainActivity, "Erreur de chargement météo", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun loadRadarImage() {
        val radarUrl = "https://tilecache.rainviewer.com/v2/radar/nowcast_0/256/6/32/21/1/1_1.png"
        radarImageView.load(radarUrl) {
            crossfade(true)
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
