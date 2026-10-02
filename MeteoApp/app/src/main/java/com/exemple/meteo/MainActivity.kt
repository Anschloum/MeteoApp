package com.exemple.meteo

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.MotionEvent
import android.widget.Button
import android.widget.EditText
import android.widget.SeekBar
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
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

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
    private lateinit var radarPlayButton: Button
    private lateinit var radarSeekBar: SeekBar
    private lateinit var radarTimeTextView: TextView

    private lateinit var radarOverlayManager: RadarMapOverlayManager

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
    private var radarHost = ""
    private var radarFrames: List<RadarTimelineFrame> = emptyList()
    private var latestRadarObservationTime = 0L
    private var currentRadarFrameIndex = 0
    private var radarAnimationJob: Job? = null
    private var radarLoadJob: Job? = null
    private var radarAnimationHasStarted = false
    private val radarTimeFormatter = SimpleDateFormat("HH:mm", Locale.getDefault())

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
        radarPlayButton = findViewById(R.id.radarPlayButton)
        radarSeekBar = findViewById(R.id.radarSeekBar)
        radarTimeTextView = findViewById(R.id.radarTimeTextView)

        mapView = findViewById(R.id.mapView)
        mapView.setTileSource(TileSourceFactory.MAPNIK)
        mapView.setMultiTouchControls(true)
        mapView.minZoomLevel = 3.0
        mapView.maxZoomLevel = 20.0

        radarOverlayManager = RadarMapOverlayManager(mapView)
        configureRadarControls()

        findViewById<Button>(R.id.radarZoomInButton).setOnClickListener {
            mapView.controller.zoomIn()
        }
        findViewById<Button>(R.id.radarZoomOutButton).setOnClickListener {
            mapView.controller.zoomOut()
        }

        mapView.setOnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN,
                MotionEvent.ACTION_POINTER_DOWN,
                MotionEvent.ACTION_MOVE ->
                    view.parent?.requestDisallowInterceptTouchEvent(true)

                MotionEvent.ACTION_UP,
                MotionEvent.ACTION_CANCEL ->
                    view.parent?.requestDisallowInterceptTouchEvent(false)
            }
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
        mapView.controller.setZoom(8.8)
        mapView.controller.setCenter(GeoPoint(lat, lon))
        loadRainViewerRadar()

        lifecycleScope.launch {
            try {
                val forecastResponse = openMeteoService.get14DaysForecast(lat = lat, lon = lon)

                tvCity.text = cityName
                val maxTemp = forecastResponse.daily.temperature_2m_max.firstOrNull()
                tvTemp.text = if (maxTemp != null) "$maxTemp °C" else "-- °C"

                hourlyRecyclerView.adapter = HourlyForecastAdapter(forecastResponse.hourly)
                forecastRecyclerView.adapter = ForecastAdapter(forecastResponse.daily)
            } catch (e: Exception) {
                tvTemp.text = "Erreur"
                Toast.makeText(this@MainActivity, "Erreur de chargement des données", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun configureRadarControls() {
        radarPlayButton.setOnClickListener {
            if (radarAnimationJob == null) {
                startRadarAnimation()
            } else {
                stopRadarAnimation()
            }
        }

        radarSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    stopRadarAnimation()
                    radarAnimationHasStarted = true
                    showRadarFrame(progress)
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
        })
    }

    private fun loadRainViewerRadar() {
        radarLoadJob?.cancel()
        stopRadarAnimation()
        radarPlayButton.isEnabled = false
        radarSeekBar.isEnabled = false
        radarTimeTextView.text = "Chargement du radar…"

        radarLoadJob = lifecycleScope.launch {
            try {
                val response = rainViewerService.getWeatherMaps()
                val pastFrames = response.radar.past
                    .filter { it.time > 0 && it.path.isNotBlank() }
                    .sortedBy { it.time }

                val latestObservation = pastFrames.lastOrNull()
                    ?: throw IllegalStateException("Aucune image radar disponible")
                latestRadarObservationTime = latestObservation.time

                val firstWantedTime = latestObservation.time - RADAR_HISTORY_SECONDS
                val observations = pastFrames
                    .filter { it.time >= firstWantedTime }
                    .map { RadarTimelineFrame(it, isForecast = false) }
                val forecasts = response.radar.nowcast
                    .filter {
                        it.time > latestObservation.time &&
                            it.path.isNotBlank()
                    }
                    .map { RadarTimelineFrame(it, isForecast = true) }

                radarFrames = (observations + forecasts)
                    .distinctBy { it.frame.time }
                    .sortedBy { it.frame.time }

                if (response.host.isBlank() || radarFrames.isEmpty()) {
                    throw IllegalStateException("Réponse radar incomplète")
                }

                radarHost = response.host
                radarOverlayManager.clearOverlays()
                radarAnimationHasStarted = false
                radarSeekBar.max = radarFrames.lastIndex
                radarSeekBar.isEnabled = radarFrames.size > 1
                radarPlayButton.isEnabled = radarFrames.size > 1

                currentRadarFrameIndex = radarFrames.indexOfLast { !it.isForecast }
                    .coerceAtLeast(0)
                radarSeekBar.progress = currentRadarFrameIndex
                showRadarFrame(currentRadarFrameIndex)
            } catch (e: Exception) {
                radarFrames = emptyList()
                radarOverlayManager.clearOverlays()
                radarTimeTextView.text = "Radar indisponible — touchez Actualiser"
                Toast.makeText(
                    this@MainActivity,
                    "Impossible de charger le radar de pluie",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun showRadarFrame(index: Int) {
        val timelineFrame = radarFrames.getOrNull(index) ?: return
        currentRadarFrameIndex = index
        radarOverlayManager.showFrame(radarHost, timelineFrame.frame)

        val differenceMinutes =
            abs(timelineFrame.frame.time - latestRadarObservationTime) / 60
        val frameKind = when {
            timelineFrame.isForecast -> "Prévision +" + differenceMinutes + " min"
            differenceMinutes == 0L -> "Dernière observation"
            else -> "Observation -" + differenceMinutes + " min"
        }
        val localTime = radarTimeFormatter.format(Date(timelineFrame.frame.time * 1000))
        radarTimeTextView.text = frameKind + " • " + localTime
    }

    private fun startRadarAnimation() {
        if (radarFrames.size < 2) return

        if (!radarAnimationHasStarted || currentRadarFrameIndex >= radarFrames.lastIndex) {
            currentRadarFrameIndex = 0
            radarSeekBar.progress = currentRadarFrameIndex
            showRadarFrame(currentRadarFrameIndex)
        }

        radarAnimationHasStarted = true
        radarPlayButton.text = "Pause"
        radarAnimationJob = lifecycleScope.launch {
            while (isActive) {
                val pause = if (currentRadarFrameIndex == radarFrames.lastIndex) {
                    RADAR_LAST_FRAME_DURATION_MS
                } else {
                    RADAR_FRAME_DURATION_MS
                }
                delay(pause)

                currentRadarFrameIndex =
                    if (currentRadarFrameIndex >= radarFrames.lastIndex) 0
                    else currentRadarFrameIndex + 1
                radarSeekBar.progress = currentRadarFrameIndex
                showRadarFrame(currentRadarFrameIndex)
            }
        }
    }

    private fun stopRadarAnimation() {
        radarAnimationJob?.cancel()
        radarAnimationJob = null
        if (::radarPlayButton.isInitialized) {
            radarPlayButton.text = "Lecture"
        }
    }

    override fun onResume() {
        super.onResume()
        mapView.onResume()
    }

    override fun onPause() {
        stopRadarAnimation()
        super.onPause()
        mapView.onPause()
    }

    override fun onDestroy() {
        radarLoadJob?.cancel()
        radarOverlayManager.clearOverlays()
        mapView.onDetach()
        super.onDestroy()
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
        private const val RADAR_HISTORY_SECONDS = 60 * 60L
        private const val RADAR_FRAME_DURATION_MS = 800L
        private const val RADAR_LAST_FRAME_DURATION_MS = 1_400L
    }

    private data class RadarTimelineFrame(
        val frame: RadarFrame,
        val isForecast: Boolean
    )
}
