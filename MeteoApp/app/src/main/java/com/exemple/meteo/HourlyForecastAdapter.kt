package com.exemple.meteo

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class HourlyForecastAdapter(private val hourlyData: HourlyData) :
    RecyclerView.Adapter<HourlyForecastAdapter.HourlyViewHolder>() {

    class HourlyViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvHour: TextView = view.findViewById(R.id.tvHour)
        val tvHourlyTemp: TextView = view.findViewById(R.id.tvHourlyTemp)
        val tvHourlyRain: TextView = view.findViewById(R.id.tvHourlyRain)
        val tvHourlyWind: TextView = view.findViewById(R.id.tvHourlyWind)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HourlyViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_hourly_forecast, parent, false)
        return HourlyViewHolder(view)
    }

    override fun onBindViewHolder(holder: HourlyViewHolder, position: Int) {
        val fullTime = hourlyData.time[position] // Format "YYYY-MM-DDTHH:MM"
        val hourFormatted = if (fullTime.contains("T")) fullTime.substringAfter("T") else fullTime

        holder.tvHour.text = hourFormatted
        holder.tvHourlyTemp.text = "${hourlyData.temperature_2m[position]} °C"
        holder.tvHourlyRain.text = "🌧 ${hourlyData.precipitation[position]} mm"
        holder.tvHourlyWind.text = "💨 ${hourlyData.wind_speed_10m[position]} km/h"
    }

    override fun getItemCount(): Int = hourlyData.time.size
}
