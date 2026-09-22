package com.exemple.meteo

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Bundle
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.MapTileProviderBasic
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.tileprovider.tilesource.XYTileSource
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.TilesOverlay
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
    private lateinit var mapView: MapView
    private lateinit var forecastRecyclerView: RecyclerView
    private lateinit var hourlyRecyclerView: RecyclerView

    private var animationJob: Job? = null
    private var radarOverlays: List<TilesOverlay> = emptyList()

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
        
        // Bornes de zoom pour éviter les messages "zoom level not supported"
        mapView.minZoomLevel = 4.0
        mapView.maxZoomLevel = 12.0

        // Permet le déplacement sur la carte dans un ScrollView
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

                // Zoom à 8.8 (environ 100 km autour de la ville)
                mapView.controller.setZoom(8.8)
                mapView.controller.setCenter(GeoPoint(lat, lon))

                loadAndAnimateRadar()

            } catch (e: Exception) {
                tvTemp.text = "Erreur"
                Toast.makeText(this@MainActivity, "Erreur de chargement des données", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private suspend fun loadAndAnimateRadar() {
        try {
            val mapsData = rainViewerService.getWeatherMaps()
            val currentTimeSeconds = System.currentTimeMillis() / 1000
            val oneHourInSeconds = 3600

            val pastFrames = mapsData.radar.past.filter { it.time >= currentTimeSeconds - oneHourInSeconds }
            val futureFrames = mapsData.radar.nowcast.filter { it.time <= currentTimeSeconds + oneHourInSeconds }
            val framesSequence = pastFrames + futureFrames

            if (framesSequence.isNotEmpty()) {
                setupRadarOverlays(mapsData.host, framesSequence)
                startRadarAnimation()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun setupRadarOverlays(host: String, frames: List<RadarFrame>) {
        // Nettoyage des calques précédents
        radarOverlays.forEach { mapView.overlays.remove(it) }

        radarOverlays = frames.map { frame ->
            val tileSource = XYTileSource(
                "RainViewer_${frame.time}",
                3,
                12,
                256,
                "/2/1_1.png",
                arrayOf("$host${frame.path}/256/")
            )
            val tileProvider = MapTileProviderBasic(applicationContext, tileSource)
            TilesOverlay(tileProvider, applicationContext).apply {
                isEnabled = false // Masqué par défaut
            }
        }

        // Ajout de tous les calques à la carte (conservés en mémoire)
        radarOverlays.forEach { mapView.overlays.add(it) }
    }

    private fun startRadarAnimation() {
        animationJob?.cancel()
        if (radarOverlays.isEmpty()) return

        animationJob = lifecycleScope.launch {
            var index = 0
            while (isActive) {
                // Active uniquement l'image courante
                radarOverlays.forEachIndexed { i, overlay ->
                    overlay.isEnabled = (i == index)
                }
                mapView.postInvalidate()

                index = (index + 1) % radarOverlays.size
                delay(500)
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
        animationJob?.cancel()
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
