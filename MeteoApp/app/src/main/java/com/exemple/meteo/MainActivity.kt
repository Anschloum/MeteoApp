package com.exemple.meteo

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.graphics.Bitmap
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
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

    private val meteoFranceRadarService: MeteoFranceRadarService by lazy {
        Retrofit.Builder()
            .baseUrl("https://portail-api.meteofrance.fr/")
            .build()
            .create(MeteoFranceRadarService::class.java)
    }

    private var currentLat = 48.8566
    private var currentLon = 2.3522
    
    // Jeton d'API Météo-France (À récupérer sur portail-api.meteofrance.fr)
    private val meteoFranceToken = "eyJ4NXQiOiJZV0kxTTJZNE1qWTNOemsyTkRZeU5XTTRPV014TXpjek1UVmhNbU14T1RSa09ETXlOVEE0Tnc9PSIsImtpZCI6ImdhdGV3YXlfY2VydGlmaWNhdGVfYWxpYXMiLCJ0eXAiOiJKV1QiLCJhbGciOiJSUzI1NiJ9.eyJzdWIiOiJBbnNjaGxvdW1AY2FyYm9uLnN1cGVyIiwiYXBwbGljYXRpb24iOnsib3duZXIiOiJBbnNjaGxvdW0iLCJ0aWVyUXVvdGFUeXBlIjpudWxsLCJ0aWVyIjoiVW5saW1pdGVkIiwibmFtZSI6IkRlZmF1bHRBcHBsaWNhdGlvbiIsImlkIjo0ODI0NSwidXVpZCI6ImZlNWI2OTI2LTBiODEtNGU3NC1iNTg3LTdmYzA1NGY5Y2NhNCJ9LCJpc3MiOiJodHRwczpcL1wvcG9ydGFpbC1hcGkubWV0ZW9mcmFuY2UuZnI6NDQzXC9vYXV0aDJcL3Rva2VuIiwidGllckluZm8iOnsiODUwcmVxUGVyNU1pbiI6eyJ0aWVyUXVvdGFUeXBlIjoicmVxdWVzdENvdW50IiwiZ3JhcGhRTE1heENvbXBsZXhpdHkiOjAsImdyYXBoUUxNYXhEZXB0aCI6MCwic3RvcE9uUXVvdGFSZWFjaCI6dHJ1ZSwic3Bpa2VBcnJlc3RMaW1pdCI6MCwic3Bpa2VBcnJlc3RVbml0Ijoic2VjIn19LCJrZXl0eXBlIjoiUFJPRFVDVElPTiIsInN1YnNjcmliZWRBUElzIjpbeyJzdWJzY3JpYmVyVGVuYW50RG9tYWluIjoiY2FyYm9uLnN1cGVyIiwibmFtZSI6IkRvbm5lZXNQdWJsaXF1ZXNSYWRhciIsImNvbnRleHQiOiJcL3B1YmxpY1wvRFBSYWRhclwvdjEiLCJwdWJsaXNoZXIiOiJNRVRFTy5GUlwvbWFydGlubCIsInZlcnNpb24iOiJ2MSIsInN1YnNjcmlwdGlvblRpZXIiOiI4NTByZXFQZXI1TWluIn1dLCJleHAiOjE4ODQ3NTI5NDAsInRva2VuX3R5cGUiOiJhcGlLZXkiLCJpYXQiOjE3OTAwODAxNDAsImp0aSI6IjViMzZmODIzLTgzMzMtNGQ2Mi04MzIxLTM1Zjk3YjkxNWE2NSJ9.vYtgfLq0Ktr7hkFGLFAMfaEUJA_tgeIosEArL8SRil7S2aBZqs9aA_MnTRtVHRtS0JyI1V-V5YgyPNYqBDHrOSCx_EXW9L9ONCWkQflqY0_GEyIvKopOG-jUXSiKvR_iAmiDKYA5PG8bTk0-GWynChC89hjCMcqPwX2Tm17llfyVju738vql2BeYpo8ratKbggK0m9QqZce14SnpKMCMQ3TAAwEx4pp1fewY99swHyZS10-aCj3pJegHmuTbcQ3VDL_XErr-b81lINc-_LOSuZgFx9xsLVN6mbbxGVDCx7UXRSsc5dg-dY3BHaYsKucdXV4eAiSDHERRunTpG2hRqg=="

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

        // Gestionnaire de calque radar
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
                val response = meteoFranceRadarService.fetchLatestMosaic("Bearer $meteoFranceToken")
                if (response.isSuccessful && response.body() != null) {
                    val rawBytes = response.body()!!.bytes()

                    // Extraction des données binaires (Largeur x Hauteur)
                    val width = 512
                    val height = 512
                    val grid = Array(height) { FloatArray(width) }

                    // TODO: Décoder le buffer binaire (rawBytes) dans la grille selon le format Météo-France

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
