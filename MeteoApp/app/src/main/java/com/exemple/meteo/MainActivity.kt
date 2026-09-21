package com.exemple.meteo

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private val api = WeatherApi.create()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val tvTemp = findViewById<TextView>(R.id.tvTemp)
        val btnRefresh = findViewById<Button>(R.id.btnRefresh)

        btnRefresh.setOnClickListener {
            fetchWeather(tvTemp)
        }

        fetchWeather(tvTemp)
    }

    private fun fetchWeather(tvTemp: TextView) {
        lifecycleScope.launch {
            try {
                val response = api.getWeather()
                tvTemp.text = "${response.currentWeather.temperature} °C"
            } catch (e: Exception) {
                tvTemp.text = "Erreur de connexion"
            }
        }
    }
}