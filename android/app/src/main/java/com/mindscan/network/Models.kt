package com.mindscan.network

import com.google.gson.annotations.SerializedName

// ── Auth ──────────────────────────────────────────────────────────────────────
data class RegisterRequest(val username: String, val email: String = "", val password: String)
data class LoginRequest(val username: String, val password: String)
data class AuthResponse(
    val success: Boolean, val message: String,
    val token: String?, val username: String?, val email: String?
)

// ── Stress Prediction ─────────────────────────────────────────────────────────
data class StressPredictRequest(
    @SerializedName("sleepDuration")      val sleepDuration: Double,
    @SerializedName("sleepQuality")       val sleepQuality: Double,
    @SerializedName("physicalActivity")   val physicalActivity: Double,
    @SerializedName("dailySteps")         val dailySteps: Double,
    @SerializedName("age")                val age: Double,
    @SerializedName("gender")             val gender: Int,
    // Auto-collected
    @SerializedName("screenTime")         val screenTime: Double,
    @SerializedName("phoneBeforeSleep")   val phoneBeforeSleep: Double,
    @SerializedName("mentalFatigue")      val mentalFatigue: Double = 5.0,
    @SerializedName("caffeineIntake")     val caffeineIntake: Double = 2.0,
    @SerializedName("notifications")      val notifications: Double,
    // Category totals
    @SerializedName("socialMediaHours")   val socialMediaHours: Double,
    @SerializedName("gamingHours")        val gamingHours: Double,
    @SerializedName("productivityHours")  val productivityHours: Double = 1.0,
    // Per-app breakdown (new)
    @SerializedName("instagramHours")     val instagramHours: Double? = null,
    @SerializedName("whatsappHours")      val whatsappHours: Double? = null,
    @SerializedName("youtubeHours")       val youtubeHours: Double? = null,
    @SerializedName("snapchatHours")      val snapchatHours: Double? = null,
    @SerializedName("facebookHours")      val facebookHours: Double? = null,
    @SerializedName("tiktokHours")        val tiktokHours: Double? = null,
    @SerializedName("entertainmentHours") val entertainmentHours: Double? = null,
    @SerializedName("messagingHours")     val messagingHours: Double? = null
)

data class StressPredictResponse(
    val id: Long?, val success: Boolean, val message: String?,
    val stressLevel: String?, val stressScore: Double?, val confidence: Double?,
    val probabilities: Map<String, Double>?,
    val recommendations: List<String>?,
    val insight: String?,          // NEW: human-readable explanation
    val createdAt: String?
)

// ── History ───────────────────────────────────────────────────────────────────
data class StressHistoryItem(
    val id: Long, val stressLevel: String, val stressScore: Double,
    val confidence: Double, val sleepDuration: Double?, val sleepQuality: Double?,
    val screenTime: Double?, val phoneBeforeSleep: Double?, val mentalFatigue: Double?,
    val physicalActivity: Double?, val dailySteps: Double?, val caffeineIntake: Double?,
    val notifications: Double?, val socialMediaHours: Double?, val gamingHours: Double?,
    val productivityHours: Double?,
    val probabilities: Map<String, Double>?,
    val recommendations: List<String>?,
    val insight: String?,          // NEW
    val createdAt: String
)

data class HistoryResponse(
    val success: Boolean, val history: List<StressHistoryItem>?, val totalCount: Long?
)

data class StatsResponse(
    val success: Boolean, val totalPredictions: Long?,
    val averageStressScore: Double?, val stressLevelCounts: Map<String, Long>?,
    val dominantStressLevel: String?, val last7Days: List<StressHistoryItem>?
)

// ── App Usage (local device data) ─────────────────────────────────────────────
data class AppUsageInfo(
    val appName: String, val packageName: String,
    val usageMinutes: Long, val category: String
)

data class CategoryUsage(val category: String, val totalMinutes: Long, val emoji: String)

// ── App Trend (from backend) ──────────────────────────────────────────────────
data class AppTrendPoint(val date: String, val hours: Double)
data class AppTrendResponse(
    val success: Boolean, val appName: String,
    val trend: List<AppTrendPoint>?, val avgHours: Double?, val totalHours: Double?
)
