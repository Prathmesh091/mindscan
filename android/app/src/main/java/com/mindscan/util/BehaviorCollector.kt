package com.mindscan.util

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStats
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Process
import com.mindscan.network.AppUsageInfo
import com.mindscan.network.CategoryUsage
import java.util.Calendar

object BehaviorCollector {

    fun hasUsagePermission(context: Context): Boolean = try {
        val ops = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(), context.packageName) == AppOpsManager.MODE_ALLOWED
    } catch (e: Exception) { false }

    fun getTodayScreenTimeHours(context: Context): Double {
        if (!hasUsagePermission(context)) return 0.0
        return try {
            val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val now = System.currentTimeMillis()
            val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, now - 86_400_000L, now)
            val ms = stats?.filter { it.totalTimeInForeground > 0 && !isSystemApp(context, it.packageName) }
                ?.sumOf { it.totalTimeInForeground } ?: 0L
            (ms / 3_600_000.0).coerceAtMost(24.0)
        } catch (e: Exception) { 0.0 }
    }

    fun getAppUsageList(context: Context): List<AppUsageInfo> {
        if (!hasUsagePermission(context)) return emptyList()
        return try {
            val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val pm  = context.packageManager
            val now = System.currentTimeMillis()
            usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, now - 86_400_000L, now)
                ?.filter { it.totalTimeInForeground > 60_000L && !isSystemApp(context, it.packageName) }
                ?.sortedByDescending { it.totalTimeInForeground }
                ?.take(20)
                ?.mapNotNull { stat ->
                    try {
                        val info = pm.getApplicationInfo(stat.packageName, 0)
                        AppUsageInfo(
                            appName      = pm.getApplicationLabel(info).toString(),
                            packageName  = stat.packageName,
                            usageMinutes = stat.totalTimeInForeground / 60_000L,
                            category     = categorizeApp(stat.packageName, pm.getApplicationLabel(info).toString())
                        )
                    } catch (e: PackageManager.NameNotFoundException) { null }
                } ?: emptyList()
        } catch (e: Exception) { emptyList() }
    }

    fun getCategoryUsageSummary(context: Context): List<CategoryUsage> {
        val emojiMap = mapOf(
            "Social Media" to "📱", "Entertainment" to "🎬", "Gaming" to "🎮",
            "Browser" to "🌐", "Messaging" to "💬", "Email" to "📧",
            "Navigation" to "🗺️", "Photos" to "📷", "Productivity" to "💼", "Other" to "📂"
        )
        return getAppUsageList(context).groupBy { it.category }
            .map { (cat, list) -> CategoryUsage(cat, list.sumOf { it.usageMinutes }, emojiMap[cat] ?: "📂") }
            .sortedByDescending { it.totalMinutes }
    }

    /** Get hours for a specific app package — returns 0.0 if not found */
    fun getSpecificAppHours(context: Context, vararg packagePrefixes: String): Double {
        if (!hasUsagePermission(context)) return 0.0
        return try {
            val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val now = System.currentTimeMillis()
            val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, now - 86_400_000L, now)
            val ms = stats?.filter { stat ->
                packagePrefixes.any { prefix -> stat.packageName.contains(prefix, ignoreCase = true) }
            }?.sumOf { it.totalTimeInForeground } ?: 0L
            (ms / 3_600_000.0).coerceAtMost(24.0)
        } catch (e: Exception) { 0.0 }
    }

    fun getSocialMediaHours(context: Context): Double =
        getAppUsageList(context).filter { it.category == "Social Media" }.sumOf { it.usageMinutes } / 60.0

    fun getGamingHours(context: Context): Double =
        getAppUsageList(context).filter { it.category == "Gaming" }.sumOf { it.usageMinutes } / 60.0

    fun getProductivityHours(context: Context): Double =
        getAppUsageList(context).filter { it.category in listOf("Productivity","Email") }.sumOf { it.usageMinutes } / 60.0

    fun getEntertainmentHours(context: Context): Double =
        getAppUsageList(context).filter { it.category == "Entertainment" }.sumOf { it.usageMinutes } / 60.0

    fun getMessagingHours(context: Context): Double =
        getAppUsageList(context).filter { it.category == "Messaging" }.sumOf { it.usageMinutes } / 60.0

    // Specific popular apps
    fun getInstagramHours(context: Context)  = getSpecificAppHours(context, "instagram")
    fun getWhatsappHours(context: Context)   = getSpecificAppHours(context, "whatsapp")
    fun getYoutubeHours(context: Context)    = getSpecificAppHours(context, "youtube")
    fun getSnapchatHours(context: Context)   = getSpecificAppHours(context, "snapchat")
    fun getFacebookHours(context: Context)   = getSpecificAppHours(context, "facebook")
    fun getTiktokHours(context: Context)     = getSpecificAppHours(context, "tiktok", "musical")

    fun getTodayNotificationsCount(context: Context): Int {
        if (!hasUsagePermission(context)) return 50
        return try {
            val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val now = System.currentTimeMillis()
            val events = usm.queryEvents(now - 86_400_000L, now)
            var count = 0
            val e = UsageEvents.Event()
            while (events.hasNextEvent()) { events.getNextEvent(e); if (e.eventType == 12) count++ }
            count
        } catch (e: Exception) { 50 }
    }

    fun getPhoneUsageBeforeSleepMinutes(context: Context): Double {
        if (!hasUsagePermission(context)) return 30.0
        return try {
            val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val cal = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 22); set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            }
            val start = cal.timeInMillis; val now = System.currentTimeMillis()
            if (start > now) return 0.0
            val end = minOf(now, start + 7_200_000L)
            val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, start, end)
            val ms = stats?.filter { it.totalTimeInForeground > 0 }?.sumOf { it.totalTimeInForeground } ?: 0L
            (ms / 60_000.0).coerceAtMost(120.0)
        } catch (e: Exception) { 30.0 }
    }

    private fun isSystemApp(context: Context, packageName: String): Boolean = try {
        val info = context.packageManager.getApplicationInfo(packageName, 0)
        (info.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0 && packageName != context.packageName
    } catch (e: Exception) { false }

    fun categorizeApp(packageName: String, appName: String): String {
        val pkg = packageName.lowercase(); val name = appName.lowercase()
        return when {
            pkg.contains("instagram") || pkg.contains("facebook") || pkg.contains("twitter") ||
            pkg.contains("tiktok") || pkg.contains("snapchat") || pkg.contains("linkedin") ||
            pkg.contains("reddit") || pkg.contains("pinterest") || pkg.contains("kwai") -> "Social Media"
            pkg.contains("youtube") || pkg.contains("netflix") || pkg.contains("spotify") ||
            pkg.contains("prime") || pkg.contains("disney") || pkg.contains("hotstar") ||
            pkg.contains("jiocinema") || pkg.contains("twitch") || pkg.contains("vlc") -> "Entertainment"
            pkg.contains("game") || pkg.contains("pubg") || pkg.contains("minecraft") ||
            pkg.contains("roblox") || pkg.contains("fortnite") || pkg.contains("clash") ||
            pkg.contains("ludo") || name.contains("game") -> "Gaming"
            pkg.contains("chrome") || pkg.contains("firefox") || pkg.contains("browser") ||
            pkg.contains("opera") || pkg.contains("brave") || pkg.contains("edge") -> "Browser"
            pkg.contains("gmail") || pkg.contains("outlook") || pkg.contains("mail") -> "Email"
            pkg.contains("whatsapp") || pkg.contains("telegram") || pkg.contains("signal") ||
            pkg.contains("messenger") || pkg.contains("viber") || pkg.contains("discord") ||
            pkg.contains("slack") || pkg.contains("teams") -> "Messaging"
            pkg.contains("maps") || pkg.contains("uber") || pkg.contains("ola") -> "Navigation"
            pkg.contains("camera") || pkg.contains("gallery") || pkg.contains("photos") -> "Photos"
            pkg.contains("notion") || pkg.contains("sheets") || pkg.contains("docs") ||
            pkg.contains("office") || pkg.contains("word") || pkg.contains("excel") -> "Productivity"
            else -> "Other"
        }
    }
}
