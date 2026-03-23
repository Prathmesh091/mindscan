package com.mindscan.ui

import android.graphics.Color
import android.graphics.PorterDuff
import android.view.*
import androidx.recyclerview.widget.RecyclerView
import com.mindscan.databinding.ItemHistoryBinding
import com.mindscan.network.StressHistoryItem
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class HistoryAdapter(private val items: List<StressHistoryItem>) :
    RecyclerView.Adapter<HistoryAdapter.ViewHolder>() {

    inner class ViewHolder(val binding: ItemHistoryBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        ViewHolder(ItemHistoryBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]; val b = holder.binding

        b.tvStressLevel.text = item.stressLevel
        b.tvScore.text       = "Score: ${item.stressScore.toInt()}/100"
        b.tvConfidence.text  = "Conf: ${(item.confidence * 100).toInt()}%"

        val color = when (item.stressLevel) {
            "Low"    -> Color.parseColor("#22C55E")
            "Medium" -> Color.parseColor("#F59E0B")
            "High"   -> Color.parseColor("#EF4444")
            else     -> Color.GRAY
        }
        b.tvStressLevel.setTextColor(color)
        b.viewLevelIndicator.setBackgroundColor(color)
        b.progressScore.progressDrawable.setColorFilter(color, PorterDuff.Mode.SRC_IN)
        b.progressScore.progress = item.stressScore.toInt()

        b.tvDate.text = try {
            LocalDateTime.parse(item.createdAt, DateTimeFormatter.ISO_DATE_TIME)
                .format(DateTimeFormatter.ofPattern("MMM dd  HH:mm"))
        } catch (e: Exception) { item.createdAt.take(16).replace("T", "  ") }

        b.tvSleepSnapshot.text  = "😴 ${item.sleepDuration ?: "-"}h"
        b.tvScreenSnapshot.text = "📱 ${item.screenTime ?: "-"}h"

        // Show AI insight if available
        if (!item.insight.isNullOrBlank()) {
            b.tvInsight.visibility = View.VISIBLE
            b.tvInsight.text       = "💡 ${item.insight}"
        } else {
            b.tvInsight.visibility = View.GONE
        }
    }
}
