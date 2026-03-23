package com.mindscan.ui

import android.graphics.Color
import android.graphics.PorterDuff
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.mindscan.databinding.ItemAppUsageBinding
import com.mindscan.network.AppUsageInfo

class AppUsageAdapter(private val items: List<AppUsageInfo>) :
    RecyclerView.Adapter<AppUsageAdapter.ViewHolder>() {

    inner class ViewHolder(val binding: ItemAppUsageBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
        ViewHolder(ItemAppUsageBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        val b    = holder.binding

        b.tvAppName.text  = item.appName
        b.tvCategory.text = item.category

        val hours = item.usageMinutes / 60
        val mins  = item.usageMinutes % 60
        b.tvUsageTime.text = if (hours > 0) "${hours}h ${mins}m" else "${mins}m"

        val pct = ((item.usageMinutes.toFloat() / 480f) * 100).toInt().coerceIn(0, 100)
        b.progressUsage.progress = pct

        // Color by category
        val color = when (item.category) {
            "Social Media"  -> Color.parseColor("#6C63FF")
            "Gaming"        -> Color.parseColor("#FF6584")
            "Entertainment" -> Color.parseColor("#43E97B")
            "Messaging"     -> Color.parseColor("#4FACFE")
            "Browser"       -> Color.parseColor("#FFA15A")
            else            -> Color.parseColor("#9E9E9E")
        }
        b.progressUsage.progressDrawable
            .setColorFilter(color, PorterDuff.Mode.SRC_IN)
        b.tvCategory.setTextColor(color)
    }
}
