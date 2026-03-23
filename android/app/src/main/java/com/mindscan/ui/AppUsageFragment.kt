package com.mindscan.ui

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.provider.Settings
import android.view.*
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.charts.HorizontalBarChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.*
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.github.mikephil.charting.formatter.ValueFormatter
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.card.MaterialCardView
import com.mindscan.R
import com.mindscan.databinding.FragmentAppUsageBinding
import com.mindscan.network.ApiClient
import com.mindscan.network.AppUsageInfo
import com.mindscan.util.BehaviorCollector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AppUsageFragment : Fragment() {

    private var _binding: FragmentAppUsageBinding? = null
    private val binding get() = _binding!!

    // Overuse thresholds (hours)
    private val THRESHOLD_SOCIAL  = 2.0
    private val THRESHOLD_GAMING  = 1.5
    private val THRESHOLD_SCREEN  = 5.0
    private val THRESHOLD_INSTA   = 1.5
    private val THRESHOLD_YOUTUBE = 2.0

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentAppUsageBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.btnGrantPermission.setOnClickListener {
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        }
        binding.btnRefresh.setOnClickListener { loadData() }

        // Tab switching: Today / 7-day / 30-day
        binding.tabToday.setOnClickListener { loadData(period = "today") }
        binding.tab7Day.setOnClickListener  { loadData(period = "7day")  }
        binding.tab30Day.setOnClickListener { loadData(period = "30day") }
    }

    override fun onResume() {
        super.onResume()
        if (BehaviorCollector.hasUsagePermission(requireContext())) {
            binding.permissionCard.visibility = View.GONE
            binding.usageContent.visibility   = View.VISIBLE
            loadData()
        } else {
            binding.permissionCard.visibility = View.VISIBLE
            binding.usageContent.visibility   = View.GONE
        }
    }

    private fun loadData(period: String = "today") {
        highlightTab(period)
        lifecycleScope.launch {
            val apps = withContext(Dispatchers.IO) {
                BehaviorCollector.getAppUsageList(requireContext())
            }
            val totalHrs = withContext(Dispatchers.IO) {
                BehaviorCollector.getTodayScreenTimeHours(requireContext())
            }

            if (!isAdded) return@launch

            binding.tvTotalScreen.text = String.format("Total: %.1f hrs today", totalHrs)

            if (apps.isEmpty()) {
                binding.tvNoUsage.visibility  = View.VISIBLE
                binding.chartContainer.visibility = View.GONE
                return@launch
            }
            binding.tvNoUsage.visibility      = View.GONE
            binding.chartContainer.visibility = View.VISIBLE

            // Check overuse and show alert
            checkOveruseAlerts(apps, totalHrs)

            when (period) {
                "today" -> showTodayBreakdown(apps)
                "7day"  -> loadTrendFromBackend("social_media", 7)
                "30day" -> loadTrendFromBackend("social_media", 30)
            }
        }
    }

    // ── Today: horizontal bar chart per app ──────────────────────────────────
    private fun showTodayBreakdown(apps: List<AppUsageInfo>) {
        binding.chartContainer.removeAllViews()

        val dp = resources.displayMetrics.density

        // Per-app horizontal bar chart (top 10)
        val topApps = apps.take(10)
        val entries = topApps.mapIndexed { i, app ->
            BarEntry(i.toFloat(), app.usageMinutes.toFloat())
        }
        val labels = topApps.map { it.appName }
        val colors = topApps.map { appColor(it.category) }

        val dataSet = BarDataSet(entries, "Usage (minutes)").apply {
            this.colors = colors
            valueTextColor = Color.parseColor("#E0E0E0")
            valueTextSize  = 10f
            valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(v: Float): String {
                    val h = (v / 60).toInt(); val m = (v % 60).toInt()
                    return if (h > 0) "${h}h${m}m" else "${m}m"
                }
            }
        }

        val chart = HorizontalBarChart(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, (topApps.size * 44 * dp).toInt())
            data = BarData(dataSet).apply { barWidth = 0.6f }
            description.isEnabled = false
            legend.isEnabled      = false
            setDrawGridBackground(false)
            setBackgroundColor(Color.TRANSPARENT)
            setFitBars(true)
            axisLeft.apply {
                textColor = Color.parseColor("#A0A0B8")
                gridColor = Color.parseColor("#2E2E4A")
                axisMinimum = 0f
            }
            axisRight.isEnabled = false
            xAxis.apply {
                position         = XAxis.XAxisPosition.BOTH_SIDED
                valueFormatter   = IndexAxisValueFormatter(labels)
                granularity      = 1f
                setDrawGridLines(false)
                textColor        = Color.parseColor("#E0E0E0")
                textSize         = 11f
            }
            setOnChartValueSelectedListener(object : com.github.mikephil.charting.listener.OnChartValueSelectedListener {
                override fun onValueSelected(e: Entry?, h: com.github.mikephil.charting.highlight.Highlight?) {
                    val idx = e?.x?.toInt() ?: return
                    if (idx < topApps.size) showAppDetail(topApps[idx])
                }
                override fun onNothingSelected() {}
            })
            animateY(800)
            invalidate()
        }
        binding.chartContainer.addView(chart)

        // Category summary cards below the chart
        addCategorySummaryCards(apps)
    }

    // ── Show trend from backend ───────────────────────────────────────────────
    private fun loadTrendFromBackend(appKey: String, days: Int) {
        binding.chartContainer.removeAllViews()
        val loading = TextView(requireContext()).apply {
            text = "Loading ${days}-day trend..."
            setTextColor(Color.parseColor("#A0A0B8"))
            textSize = 14f
            gravity = android.view.Gravity.CENTER
            setPadding(0, 32, 0, 32)
        }
        binding.chartContainer.addView(loading)

        // Show app selector cards
        val appCards = listOf(
            "instagram" to "Instagram",
            "whatsapp"  to "WhatsApp",
            "youtube"   to "YouTube",
            "snapchat"  to "Snapchat",
            "facebook"  to "Facebook",
            "tiktok"    to "TikTok",
            "gaming"    to "Gaming",
            "social_media" to "All Social"
        )

        lifecycleScope.launch {
            try {
                val resp = ApiClient.getApiService().getAppTrend(appKey, days)
                if (!isAdded) return@launch
                binding.chartContainer.removeAllViews()

                if (resp.isSuccessful && resp.body()?.success == true) {
                    val body = resp.body()!!
                    addAppSelector(appCards, days)
                    showTrendChart(body.appName, body.trend ?: emptyList(), body.avgHours ?: 0.0)
                } else {
                    addAppSelector(appCards, days)
                    showLocalTrend()
                }
            } catch (e: Exception) {
                if (isAdded) {
                    binding.chartContainer.removeAllViews()
                    addAppSelector(appCards, days)
                    showLocalTrend()
                }
            }
        }
    }

    private fun addAppSelector(appCards: List<Pair<String,String>>, days: Int) {
        val dp = resources.displayMetrics.density
        val scroll = HorizontalScrollView(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            isHorizontalScrollBarEnabled = false
        }
        val row = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 0, 0, (12 * dp).toInt())
        }
        appCards.forEach { (key, label) ->
            val chip = com.google.android.material.chip.Chip(requireContext()).apply {
                text = label
                isClickable = true
                setChipBackgroundColorResource(android.R.color.transparent)
                chipStrokeWidth = 1.5f
                setChipStrokeColorResource(R.color.primary)
                setTextColor(Color.parseColor("#E0E0E0"))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    marginEnd = (8 * dp).toInt()
                }
                setOnClickListener {
                    lifecycleScope.launch {
                        try {
                            val resp = ApiClient.getApiService().getAppTrend(key, days)
                            if (!isAdded) return@launch
                            // Remove old chart (keep selector)
                            val cnt = binding.chartContainer.childCount
                            if (cnt > 1) binding.chartContainer.removeViewAt(cnt - 1)
                            if (resp.isSuccessful && resp.body()?.success == true) {
                                val b = resp.body()!!
                                showTrendChart(b.appName, b.trend ?: emptyList(), b.avgHours ?: 0.0)
                            } else showLocalTrend()
                        } catch (e: Exception) { if (isAdded) showLocalTrend() }
                    }
                }
            }
            row.addView(chip)
        }
        scroll.addView(row)
        binding.chartContainer.addView(scroll)
    }

    private fun showTrendChart(appName: String, trend: List<com.mindscan.network.AppTrendPoint>, avgHours: Double) {
        val dp = resources.displayMetrics.density
        val entries = trend.mapIndexed { i, p -> BarEntry(i.toFloat(), (p.hours * 60).toFloat()) }
        val labels  = trend.map { it.date }

        if (entries.isEmpty()) { showLocalTrend(); return }

        val color = Color.parseColor("#7C3AED")
        val dataSet = BarDataSet(entries, "$appName usage (min)").apply {
            setColor(color)
            valueTextColor = Color.parseColor("#E0E0E0")
            valueTextSize  = 9f
            valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(v: Float): String {
                    if (v < 1) return ""
                    val h = (v / 60).toInt(); val m = (v % 60).toInt()
                    return if (h > 0) "${h}h${m}m" else "${m}m"
                }
            }
        }

        val infoCard = MaterialCardView(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = (8 * dp).toInt() }
            radius = 10 * dp; cardElevation = 1 * dp
            setCardBackgroundColor(Color.parseColor("#1E1E2E"))
        }
        val infoRow = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding((12 * dp).toInt(), (10 * dp).toInt(), (12 * dp).toInt(), (10 * dp).toInt())
        }
        val infoText = TextView(requireContext()).apply {
            text = "Avg daily: ${String.format("%.1f", avgHours)}h  ·  App: $appName"
            textSize = 13f; setTextColor(Color.parseColor("#B0B0C8"))
        }
        infoRow.addView(infoText); infoCard.addView(infoRow)
        binding.chartContainer.addView(infoCard)

        val chart = BarChart(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, (220 * dp).toInt())
            data = BarData(dataSet).apply { barWidth = 0.65f }
            description.isEnabled = false; legend.isEnabled = false
            setDrawGridBackground(false); setBackgroundColor(Color.TRANSPARENT)
            setFitBars(true)
            axisLeft.apply {
                textColor = Color.parseColor("#A0A0B8"); gridColor = Color.parseColor("#2E2E4A")
                axisMinimum = 0f
                valueFormatter = object : ValueFormatter() {
                    override fun getFormattedValue(v: Float) =
                        if (v >= 60) "${(v/60).toInt()}h" else "${v.toInt()}m"
                }
            }
            axisRight.isEnabled = false
            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                valueFormatter = IndexAxisValueFormatter(labels)
                granularity = 1f; setDrawGridLines(false)
                textColor = Color.parseColor("#A0A0B8"); textSize = 9f
                labelRotationAngle = -30f
            }
            animateY(700); invalidate()
        }
        binding.chartContainer.addView(chart)
    }

    private fun showLocalTrend() {
        val tv = TextView(requireContext()).apply {
            text = "Backend trend unavailable. Connect to server for historical data."
            setTextColor(Color.parseColor("#A0A0B8"))
            textSize = 13f; gravity = android.view.Gravity.CENTER
            setPadding(0, 24, 0, 24)
        }
        binding.chartContainer.addView(tv)
    }

    // ── Category summary cards ────────────────────────────────────────────────
    private fun addCategorySummaryCards(apps: List<AppUsageInfo>) {
        val dp = resources.displayMetrics.density
        val byCategory = apps.groupBy { it.category }
            .map { (cat, list) -> cat to list.sumOf { it.usageMinutes } }
            .sortedByDescending { it.second }

        byCategory.forEach { (cat, totalMin) ->
            val hrs = totalMin / 60; val mins = totalMin % 60
            val timeStr = if (hrs > 0) "${hrs}h ${mins}m" else "${mins}m"
            val card = MaterialCardView(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    bottomMargin = (6 * dp).toInt() }
                radius = 10 * dp; cardElevation = 1 * dp
                setCardBackgroundColor(Color.parseColor("#1E1E2E"))
            }
            val row = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding((12 * dp).toInt(), (10 * dp).toInt(), (12 * dp).toInt(), (10 * dp).toInt())
                gravity = android.view.Gravity.CENTER_VERTICAL
            }
            val dot = View(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams((10 * dp).toInt(), (10 * dp).toInt()).apply {
                    marginEnd = (10 * dp).toInt(); topMargin = (2 * dp).toInt() }
                setBackgroundColor(appColor(cat))
            }
            val tvName = TextView(requireContext()).apply {
                text = cat; textSize = 14f; setTextColor(Color.parseColor("#E0E0E0"))
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            val tvTime = TextView(requireContext()).apply {
                text = timeStr; textSize = 14f; setTextColor(appColor(cat))
                typeface = android.graphics.Typeface.DEFAULT_BOLD
            }
            row.addView(dot); row.addView(tvName); row.addView(tvTime)
            card.addView(row); binding.chartContainer.addView(card)
        }
    }

    // ── Overuse popup alert ───────────────────────────────────────────────────
    private fun checkOveruseAlerts(apps: List<AppUsageInfo>, totalHrs: Double) {
        val alerts = mutableListOf<Pair<String,String>>()

        val socialMin = apps.filter { it.category == "Social Media" }.sumOf { it.usageMinutes }
        val socialHrs = socialMin / 60.0
        if (socialHrs >= THRESHOLD_SOCIAL)
            alerts += "📱 Social Media" to String.format("You've spent %.1fh on social media today (limit: %.0fh). Consider a break.", socialHrs, THRESHOLD_SOCIAL)

        val instaMin = apps.filter { it.packageName.contains("instagram") }.sumOf { it.usageMinutes }
        val instaHrs = instaMin / 60.0
        if (instaHrs >= THRESHOLD_INSTA)
            alerts += "📸 Instagram" to String.format("Instagram: %.1fh today — that's over the %.0fh healthy limit.", instaHrs, THRESHOLD_INSTA)

        val ytMin = apps.filter { it.packageName.contains("youtube") }.sumOf { it.usageMinutes }
        val ytHrs = ytMin / 60.0
        if (ytHrs >= THRESHOLD_YOUTUBE)
            alerts += "▶️ YouTube" to String.format("YouTube: %.1fh today. Extended watching is linked to poor sleep quality.", ytHrs)

        val gamingMin = apps.filter { it.category == "Gaming" }.sumOf { it.usageMinutes }
        val gamingHrs = gamingMin / 60.0
        if (gamingHrs >= THRESHOLD_GAMING)
            alerts += "🎮 Gaming" to String.format("%.1fh of gaming detected. Take a 15-minute break and stretch.", gamingHrs)

        if (totalHrs >= THRESHOLD_SCREEN)
            alerts += "📵 Screen Time" to String.format("Total screen time: %.1fh. Try the 20-20-20 rule to reduce eye strain.", totalHrs)

        if (alerts.isNotEmpty()) showOveruseBottomSheet(alerts)
    }

    private fun showOveruseBottomSheet(alerts: List<Pair<String, String>>) {
        val dialog = BottomSheetDialog(requireContext())
        val dp = resources.displayMetrics.density
        val root = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((20 * dp).toInt(), (20 * dp).toInt(), (20 * dp).toInt(), (32 * dp).toInt())
        }

        val title = TextView(requireContext()).apply {
            text = "⚠️ Usage Alerts"
            textSize = 18f; setTextColor(Color.parseColor("#F59E0B"))
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = (12 * dp).toInt() }
        }
        root.addView(title)

        alerts.forEach { (header, body) ->
            val card = MaterialCardView(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    bottomMargin = (8 * dp).toInt() }
                radius = 10 * dp; cardElevation = 0 * dp
                setCardBackgroundColor(Color.parseColor("#2A1F0A"))
                strokeWidth = 1; strokeColor = Color.parseColor("#F59E0B")
            }
            val inner = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.VERTICAL
                setPadding((12 * dp).toInt(), (10 * dp).toInt(), (12 * dp).toInt(), (10 * dp).toInt())
            }
            inner.addView(TextView(requireContext()).apply {
                text = header; textSize = 13f
                setTextColor(Color.parseColor("#F59E0B"))
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    bottomMargin = (4 * dp).toInt() }
            })
            inner.addView(TextView(requireContext()).apply {
                text = body; textSize = 12f; setTextColor(Color.parseColor("#D0C0A0"))
            })
            card.addView(inner); root.addView(card)
        }

        root.addView(com.google.android.material.button.MaterialButton(requireContext()).apply {
            text = "Got it — I'll take a break"
            setTextColor(Color.WHITE); backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#7C3AED"))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, (48 * dp).toInt()).apply {
                topMargin = (8 * dp).toInt() }
            setOnClickListener { dialog.dismiss() }
        })

        val scrollView = android.widget.ScrollView(requireContext()).apply { addView(root) }
        dialog.setContentView(scrollView)
        dialog.show()
    }

    // ── App detail bottom sheet ───────────────────────────────────────────────
    private fun showAppDetail(app: AppUsageInfo) {
        val dialog = BottomSheetDialog(requireContext())
        val dp = resources.displayMetrics.density
        val root = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((20 * dp).toInt(), (20 * dp).toInt(), (20 * dp).toInt(), (32 * dp).toInt())
        }
        val hrs = app.usageMinutes / 60; val mins = app.usageMinutes % 60
        root.addView(TextView(requireContext()).apply {
            text = app.appName; textSize = 20f; typeface = android.graphics.Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#F1F1F8"))
        })
        root.addView(TextView(requireContext()).apply {
            text = "Category: ${app.category}"; textSize = 13f; setTextColor(Color.parseColor("#A0A0B8"))
        })
        root.addView(TextView(requireContext()).apply {
            text = if (hrs > 0) "Today: ${hrs}h ${mins}m" else "Today: ${mins}m"
            textSize = 28f; typeface = android.graphics.Typeface.DEFAULT_BOLD
            setTextColor(appColor(app.category))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = (12 * dp).toInt() }
        })
        val tip = getTip(app)
        if (tip != null) {
            root.addView(TextView(requireContext()).apply {
                text = tip; textSize = 13f; setTextColor(Color.parseColor("#A0A0B8"))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    topMargin = (12 * dp).toInt() }
            })
        }
        dialog.setContentView(root); dialog.show()
    }

    private fun getTip(app: AppUsageInfo): String? {
        val pkg = app.packageName.lowercase()
        return when {
            pkg.contains("instagram") -> "Studies show >1h/day on Instagram is linked to increased anxiety. Try grayscale mode."
            pkg.contains("tiktok")    -> "TikTok's short-form content is highly addictive. Consider setting a daily timer in settings."
            pkg.contains("youtube")   -> "Long YouTube sessions displace sleep. Avoid watching after 9 PM."
            pkg.contains("whatsapp")  -> "High WhatsApp use may indicate notification overload. Mute non-essential groups."
            pkg.contains("facebook")  -> "Facebook doomscrolling increases stress. Try a 1-week limit challenge."
            pkg.contains("snapchat")  -> "Snapchat streaks can create anxiety. Remember — you control the app, not the other way."
            app.category == "Gaming"  -> "Gaming > 1.5h/day elevates cortisol. Schedule gaming sessions instead of open-ended play."
            else                      -> null
        }
    }

    private fun highlightTab(period: String) {
        val active = Color.parseColor("#7C3AED"); val inactive = Color.parseColor("#3A3A5C")
        binding.tabToday.setBackgroundColor(if (period == "today") active else inactive)
        binding.tab7Day.setBackgroundColor(if (period == "7day")   active else inactive)
        binding.tab30Day.setBackgroundColor(if (period == "30day") active else inactive)
    }

    private fun appColor(category: String): Int = when (category) {
        "Social Media"  -> Color.parseColor("#6C63FF")
        "Gaming"        -> Color.parseColor("#FF6584")
        "Entertainment" -> Color.parseColor("#43E97B")
        "Messaging"     -> Color.parseColor("#4FACFE")
        "Browser"       -> Color.parseColor("#FFA15A")
        "Productivity"  -> Color.parseColor("#00F2FE")
        "Email"         -> Color.parseColor("#F093FB")
        else            -> Color.parseColor("#9E9E9E")
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}
