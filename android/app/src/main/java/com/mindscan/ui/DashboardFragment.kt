package com.mindscan.ui

import android.content.Intent
import android.graphics.Color
import android.graphics.PorterDuff
import android.os.Bundle
import android.provider.Settings
import android.view.*
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.card.MaterialCardView
import com.mindscan.databinding.FragmentDashboardBinding
import com.mindscan.network.*
import com.mindscan.util.BehaviorCollector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DashboardFragment : Fragment() {

    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        setupSlider()
        loadUsageSummaryCards()
        checkPermissionUI()
        binding.btnAnalyze.setOnClickListener { submitPrediction() }
        binding.btnGrantUsage.setOnClickListener {
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        }
    }

    override fun onResume() {
        super.onResume()
        loadUsageSummaryCards()
        checkPermissionUI()
    }

    private fun setupSlider() {
        binding.sliderSleepQuality.addOnChangeListener { _, value, _ ->
            binding.tvSleepQualityValue.text = value.toString()
        }
    }

    private fun checkPermissionUI() {
        val has = BehaviorCollector.hasUsagePermission(requireContext())
        binding.btnGrantUsage.visibility  = if (has) View.GONE else View.VISIBLE
        binding.tvNoUsageData.visibility  = if (has) View.GONE else View.VISIBLE
        binding.layoutUsageCards.visibility = if (has) View.VISIBLE else View.GONE
    }

    private fun loadUsageSummaryCards() {
        if (!BehaviorCollector.hasUsagePermission(requireContext())) return
        lifecycleScope.launch {
            val categories = withContext(Dispatchers.IO) { BehaviorCollector.getCategoryUsageSummary(requireContext()) }
            val totalHours = withContext(Dispatchers.IO) { BehaviorCollector.getTodayScreenTimeHours(requireContext()) }
            if (!isAdded) return@launch
            binding.tvTodayScreenTime.text = String.format("Total screen time: %.1f hrs today", totalHours)
            binding.layoutUsageCards.removeAllViews()
            if (categories.isEmpty()) {
                binding.tvNoUsageData.text = "No app usage data yet today"
                binding.tvNoUsageData.visibility = View.VISIBLE
            } else {
                binding.tvNoUsageData.visibility = View.GONE
                val maxMinutes = categories.firstOrNull()?.totalMinutes ?: 1L
                categories.take(6).forEach { binding.layoutUsageCards.addView(buildUsageCard(it, maxMinutes)) }
            }
        }
    }

    private fun buildUsageCard(cat: CategoryUsage, maxMinutes: Long): View {
        val ctx = requireContext(); val dp = resources.displayMetrics.density
        val hrs = cat.totalMinutes / 60; val mins = cat.totalMinutes % 60
        val timeStr = if (hrs > 0) "${hrs}h ${mins}m" else "${mins}m"
        val pct = if (maxMinutes > 0) (cat.totalMinutes * 100 / maxMinutes).toInt() else 0

        val card = MaterialCardView(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = (8 * dp).toInt() }
            radius = 10 * dp; cardElevation = 1 * dp
            setCardBackgroundColor(Color.parseColor("#2A2A3E"))
        }
        val inner = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            val p = (12 * dp).toInt(); setPadding(p, p, p, p)
        }
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = (6 * dp).toInt() }
        }
        row.addView(TextView(ctx).apply {
            text = "${cat.emoji}  ${cat.category}"; textSize = 14f
            setTextColor(Color.parseColor("#F1F1F8")); typeface = android.graphics.Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        row.addView(TextView(ctx).apply {
            text = timeStr; textSize = 14f
            setTextColor(Color.parseColor("#7C3AED")); typeface = android.graphics.Typeface.DEFAULT_BOLD
        })
        val progress = ProgressBar(ctx, null, android.R.attr.progressBarStyleHorizontal).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (6 * dp).toInt())
            max = 100; this.progress = pct
            progressDrawable.setColorFilter(Color.parseColor("#7C3AED"), PorterDuff.Mode.SRC_IN)
        }
        inner.addView(row); inner.addView(progress); card.addView(inner)
        return card
    }

    private fun submitPrediction() {
        val sleepDuration    = binding.etSleepDuration.text.toString().toDoubleOrNull()
        val sleepQuality     = binding.sliderSleepQuality.value.toDouble()
        val physicalActivity = binding.etPhysicalActivity.text.toString().toDoubleOrNull()
        val dailySteps       = binding.etDailySteps.text.toString().toDoubleOrNull()
        val age              = binding.etAge.text.toString().toDoubleOrNull()
        val gender           = if (binding.rgGender.checkedRadioButtonId == binding.rbMale.id) 1 else 0

        if (sleepDuration == null || physicalActivity == null || dailySteps == null || age == null) {
            android.widget.Toast.makeText(requireContext(), "Please fill in all fields", android.widget.Toast.LENGTH_SHORT).show()
            return
        }

        // Save profile for 10 PM auto-prediction
        requireContext().getSharedPreferences("mindscan_daily_prefs", android.content.Context.MODE_PRIVATE).edit()
            .putFloat("sleep_duration",    sleepDuration.toFloat())
            .putFloat("sleep_quality",     sleepQuality.toFloat())
            .putFloat("physical_activity", physicalActivity.toFloat())
            .putFloat("daily_steps",       dailySteps.toFloat())
            .putFloat("age",               age.toFloat())
            .putInt("gender",              gender).apply()

        showLoading(true)

        lifecycleScope.launch {
            val ctx = requireContext()
            val screenTime       = withContext(Dispatchers.IO) { BehaviorCollector.getTodayScreenTimeHours(ctx) }
            val phoneBefore      = withContext(Dispatchers.IO) { BehaviorCollector.getPhoneUsageBeforeSleepMinutes(ctx) }
            val notifications    = withContext(Dispatchers.IO) { BehaviorCollector.getTodayNotificationsCount(ctx).toDouble() }
            val socialMedia      = withContext(Dispatchers.IO) { BehaviorCollector.getSocialMediaHours(ctx) }
            val gaming           = withContext(Dispatchers.IO) { BehaviorCollector.getGamingHours(ctx) }
            val productivity     = withContext(Dispatchers.IO) { BehaviorCollector.getProductivityHours(ctx) }
            val entertainment    = withContext(Dispatchers.IO) { BehaviorCollector.getEntertainmentHours(ctx) }
            val messaging        = withContext(Dispatchers.IO) { BehaviorCollector.getMessagingHours(ctx) }
            // Per-app breakdown
            val instagram        = withContext(Dispatchers.IO) { BehaviorCollector.getInstagramHours(ctx) }
            val whatsapp         = withContext(Dispatchers.IO) { BehaviorCollector.getWhatsappHours(ctx) }
            val youtube          = withContext(Dispatchers.IO) { BehaviorCollector.getYoutubeHours(ctx) }
            val snapchat         = withContext(Dispatchers.IO) { BehaviorCollector.getSnapchatHours(ctx) }
            val facebook         = withContext(Dispatchers.IO) { BehaviorCollector.getFacebookHours(ctx) }
            val tiktok           = withContext(Dispatchers.IO) { BehaviorCollector.getTiktokHours(ctx) }

            val request = StressPredictRequest(
                sleepDuration    = sleepDuration,
                sleepQuality     = sleepQuality,
                physicalActivity = physicalActivity,
                dailySteps       = dailySteps,
                age              = age,
                gender           = gender,
                screenTime       = screenTime,
                phoneBeforeSleep = phoneBefore,
                notifications    = notifications,
                socialMediaHours = socialMedia,
                gamingHours      = gaming,
                productivityHours = productivity,
                entertainmentHours = entertainment,
                messagingHours   = messaging,
                instagramHours   = instagram,
                whatsappHours    = whatsapp,
                youtubeHours     = youtube,
                snapchatHours    = snapchat,
                facebookHours    = facebook,
                tiktokHours      = tiktok
            )

            try {
                val response = ApiClient.getApiService().predict(request)
                if (response.isSuccessful && response.body()?.success == true) {
                    displayResult(response.body()!!)
                } else {
                    android.widget.Toast.makeText(ctx, response.body()?.message ?: "Prediction failed", android.widget.Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                android.widget.Toast.makeText(ctx, "Connection error: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
            } finally {
                showLoading(false)
            }
        }
    }

    private fun displayResult(result: StressPredictResponse) {
        binding.resultCard.visibility = View.VISIBLE
        val color = when (result.stressLevel) {
            "Low"    -> Color.parseColor("#22C55E")
            "Medium" -> Color.parseColor("#F59E0B")
            "High"   -> Color.parseColor("#EF4444")
            else     -> Color.GRAY
        }
        binding.tvStressLevel.text = result.stressLevel ?: "Unknown"
        binding.tvStressLevel.setTextColor(color)
        val score = result.stressScore?.toInt() ?: 0
        binding.progressStressScore.progress = score
        binding.progressStressScore.progressDrawable.setColorFilter(color, PorterDuff.Mode.SRC_IN)
        binding.tvStressScore.text = "$score / 100"
        binding.tvConfidence.text = "Confidence: ${((result.confidence ?: 0.0) * 100).toInt()}%"
        val probs = result.probabilities
        if (probs != null) {
            binding.tvProbLow.text    = "Low: ${String.format("%.0f", (probs["Low"]    ?: 0.0) * 100)}%"
            binding.tvProbMedium.text = "Med: ${String.format("%.0f", (probs["Medium"] ?: 0.0) * 100)}%"
            binding.tvProbHigh.text   = "High: ${String.format("%.0f", (probs["High"]  ?: 0.0) * 100)}%"
        }
        // Show AI insight
        if (!result.insight.isNullOrBlank()) {
            binding.tvInsight.visibility = View.VISIBLE
            binding.tvInsight.text = "🧠 ${result.insight}"
        }
        if (!result.recommendations.isNullOrEmpty()) {
            binding.tvRecommendations.text = result.recommendations.joinToString("\n\n")
        }
        binding.scrollView.post { binding.scrollView.smoothScrollTo(0, binding.resultCard.top) }
    }

    private fun showLoading(loading: Boolean) {
        binding.btnAnalyze.isEnabled    = !loading
        binding.progressBar.visibility  = if (loading) View.VISIBLE else View.GONE
        if (loading) binding.resultCard.visibility = View.GONE
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}
