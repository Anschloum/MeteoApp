import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class ForecastAdapter(private val daily: Daily) : RecyclerView.Adapter<ForecastAdapter.ForecastViewHolder>() {

    class ForecastViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvDate: TextView = view.findViewById(R.id.tvForecastDate)
        val tvMaxTemp: TextView = view.findViewById(R.id.tvForecastMaxTemp)
        val tvMinTemp: TextView = view.findViewById(R.id.tvForecastMinTemp)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ForecastViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_forecast, parent, false)
        return ForecastViewHolder(view)
    }

    override fun onBindViewHolder(holder: ForecastViewHolder, position: Int) {
        holder.tvDate.text = daily.time[position]
        holder.tvMaxTemp.text = "${daily.temperature_2m_max[position]}°C"
        holder.tvMinTemp.text = "${daily.temperature_2m_min[position]}°C"
    }

    override fun getItemCount(): Int = daily.time.size
}
