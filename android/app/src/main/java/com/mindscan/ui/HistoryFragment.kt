package com.mindscan.ui

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.*
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.github.mikephil.charting.components.LimitLine
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.*
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.github.mikephil.charting.formatter.ValueFormatter
import com.github.mikephil.charting.highlight.Highlight
import com.github.mikephil.charting.listener.OnChartValueSelectedListener
import com.mindscan.databinding.FragmentHistoryBinding
import com.mindscan.network.ApiClient
import com.mindscan.network.StressHistoryItem
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class HistoryFragment : Fragment() {

    private var _binding: FragmentHistoryBinding? = null
    private val binding get() = _binding!!

    private var allHistory: List<StressHistoryItem> = emptyList()
    private var currentPeriod = 7

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentHistoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        setupChart()
        setupTabs()
        loadHistory()
        binding.swipeRefresh.setOnRefreshListener { loadHistory() }
    }

    private fun setupTabs() {
        binding.tab7Day.setOnClickListener  { currentPeriod = 7;  renderPeriod() }
        binding.tab30Day.setOnClickListener { currentPeriod = 30; renderPeriod() }
    }

    private fun setupChart() {
        binding.lineChart.apply {
            description.isEnabled = false
            setTouchEnabled(true); isDragEnabled = true
            setScaleEnabled(false); setPinchZoom(false)
            setDrawGridBackground(false); setBackgroundColor(Color.TRANSPARENT)
            setNoDataText("No stress data yet. Complete your first analysis!")
            setNoDataTextColor(Color.parseColor("#A0A0B8"))
            legend.apply { isEnabled = true; textColor = Color.parseColor("#A0A0B8"); textSize = 11f }
        }
    }

    private fun loadHistory() {
        lifecycleScope.launch {
            try {
                val response = ApiClient.getApiService().getHistory()
                if (response.isSuccessful && response.body()?.success == true) {
                    allHistory = response.body()!!.history ?: emptyList()
                    renderPeriod()
                } else {
                    Toast.makeText(requireContext(), "Failed to load history", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                binding.swipeRefresh.isRefreshing = false
            }
        }
    }

    private fun renderPeriod() {
        if (!isAdded) return
        // Active tab highlight
        val active = Color.parseColor("#7C3AED"); val inactive = Color.parseColor("#3A3A5C")
        binding.tab7Day.setBackgroundColor(if (currentPeriod == 7) active else inactive)
        binding.tab30Day.setBackgroundColor(if (currentPeriod == 30) active else inactive)

        val data = allHistory.take(currentPeriod)
        displayWellbeingChart(data)
        updateSummaryCards(data)
        displayHistory(data)
    }

    private fun displayWellbeingChart(history: List<StressHistoryItem>) {
        if (history.isEmpty()) { binding.lineChart.clear(); binding.lineChart.invalidate(); return }

        val data = history.reversed()
        val entries = data.mapIndexed { i, item -> Entry(i.toFloat(), item.stressScore.toFloat()) }

        val dataSet = LineDataSet(entries, "Stress Score").apply {
            color = Color.parseColor("#7C3AED"); lineWidth = 2.5f
            circleRadius = 5f; circleHoleRadius = 2.5f
            setCircleColor(Color.parseColor("#7C3AED"))
            circleHoleColor = Color.parseColor("#0F0F14")
            setDrawValues(true); valueTextSize = 10f
            valueTextColor = Color.parseColor("#A0A0B8")
            valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(v: Float) = v.toInt().toString()
            }
            mode = LineDataSet.Mode.CUBIC_BEZIER
            fillDrawable = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(Color.parseColor("#557C3AED"), Color.parseColor("#007C3AED")))
            setDrawFilled(true)
            highLightColor = Color.parseColor("#EF4444"); highlightLineWidth = 1.5f
        }

        val labels = data.map { item ->
            try {
                val dt = LocalDateTime.parse(item.createdAt.replace(" ", "T"), DateTimeFormatter.ISO_DATE_TIME)
                "${dt.dayOfMonth}/${dt.monthValue}"
            } catch (e: Exception) { item.createdAt.take(5) }
        }

        binding.lineChart.data = LineData(dataSet)
        binding.lineChart.xAxis.apply {
            position = XAxis.XAxisPosition.BOTTOM
            valueFormatter = IndexAxisValueFormatter(labels)
            granularity = 1f; setDrawGridLines(true)
            gridColor = Color.parseColor("#2E2E4A")
            textColor = Color.parseColor("#A0A0B8"); textSize = 10f
            labelRotationAngle = -30f
        }
        binding.lineChart.axisLeft.apply {
            axisMinimum = 0f; axisMaximum = 100f
            textColor = Color.parseColor("#A0A0B8"); setDrawGridLines(true)
            gridColor = Color.parseColor("#2E2E4A"); removeAllLimitLines()
            addLimitLine(LimitLine(55f, "Low | Medium").apply {
                lineColor = Color.parseColor("#22C55E"); lineWidth = 1f
                textColor = Color.parseColor("#22C55E"); textSize = 9f
                labelPosition = LimitLine.LimitLabelPosition.RIGHT_TOP
                enableDashedLine(10f, 5f, 0f)
            })
            addLimitLine(LimitLine(80f, "Medium | High").apply {
                lineColor = Color.parseColor("#F59E0B"); lineWidth = 1f
                textColor = Color.parseColor("#F59E0B"); textSize = 9f
                labelPosition = LimitLine.LimitLabelPosition.RIGHT_TOP
                enableDashedLine(10f, 5f, 0f)
            })
            setDrawLimitLinesBehindData(true)
        }
        binding.lineChart.axisRight.isEnabled = false
        binding.lineChart.setOnChartValueSelectedListener(object : OnChartValueSelectedListener {
            override fun onValueSelected(e: Entry?, h: Highlight?) {
                val idx = e?.x?.toInt() ?: return
                if (idx < data.size) {
                    val item = data[idx]
                    val emoji = when (item.stressLevel) { "Low" -> "🟢"; "Medium" -> "🟡"; "High" -> "🔴"; else -> "📊" }
                    Toast.makeText(requireContext(),
                        "$emoji ${item.stressLevel} — Score: ${item.stressScore.toInt()}",
                        Toast.LENGTH_SHORT).show()
                }
            }
            override fun onNothingSelected() {}
        })
        binding.lineChart.animateX(900); binding.lineChart.invalidate()
    }

    private fun updateSummaryCards(history: List<StressHistoryItem>) {
        if (history.isEmpty()) return
        val avg     = history.mapNotNull { it.stressScore }.average()
        val highest = history.mapNotNull { it.stressScore }.maxOrNull() ?: 0.0
        val lowest  = history.mapNotNull { it.stressScore }.minOrNull() ?: 0.0
        binding.tvAvgScore.text  = "Avg: ${avg.toInt()}"
        binding.tvHighScore.text = "High: ${highest.toInt()}"
        binding.tvLowScore.text  = "Low: ${lowest.toInt()}"
        binding.tvAvgScore.setTextColor(when {
            avg < 55 -> Color.parseColor("#22C55E")
            avg < 80 -> Color.parseColor("#F59E0B")
            else     -> Color.parseColor("#EF4444")
        })
    }

    private fun displayHistory(history: List<StressHistoryItem>) {
        if (history.isEmpty()) {
            binding.tvNoData.visibility        = View.VISIBLE
            binding.recyclerHistory.visibility = View.GONE
        } else {
            binding.tvNoData.visibility        = View.GONE
            binding.recyclerHistory.visibility = View.VISIBLE
            binding.recyclerHistory.adapter    = HistoryAdapter(history)
        }
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}
