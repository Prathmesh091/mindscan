package com.mindscan.util

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.mindscan.MindScanApp
import com.mindscan.R
import com.mindscan.ui.MainActivity
import java.text.SimpleDateFormat
import java.util.*

class UsageAlertWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    companion object {
        // Thresholds
        const val THRESHOLD_SOCIAL_HRS    = 2.0
        const val THRESHOLD_GAMING_HRS    = 1.5
        const val THRESHOLD_SCREEN_HRS    = 5.0
        const val THRESHOLD_PHONE_BED     = 45.0
        const val THRESHOLD_INSTAGRAM_HRS = 1.5
        const val THRESHOLD_TIKTOK_HRS    = 1.0
        const val THRESHOLD_YOUTUBE_HRS   = 2.0
        // Notification IDs
        const val NOTIF_SOCIAL_MEDIA  = 2001
        const val NOTIF_GAMING        = 2002
        const val NOTIF_SCREEN_TIME   = 2003
        const val NOTIF_PHONE_BED     = 2004
        const val NOTIF_INSTAGRAM     = 2005
        const val NOTIF_TIKTOK        = 2006
        const val NOTIF_YOUTUBE       = 2007
        const val PREFS_ALERTS        = "mindscan_alert_prefs"
    }

    override suspend fun doWork(): Result {
        if (!BehaviorCollector.hasUsagePermission(context)) return Result.success()
        return try {
            val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            val prefs = context.getSharedPreferences(PREFS_ALERTS, Context.MODE_PRIVATE)

            val socialHrs  = BehaviorCollector.getSocialMediaHours(context)
            val gamingHrs  = BehaviorCollector.getGamingHours(context)
            val screenHrs  = BehaviorCollector.getTodayScreenTimeHours(context)
            val phoneBed   = BehaviorCollector.getPhoneUsageBeforeSleepMinutes(context)
            val instaHrs   = BehaviorCollector.getInstagramHours(context)
            val tiktokHrs  = BehaviorCollector.getTiktokHours(context)
            val ytHrs      = BehaviorCollector.getYoutubeHours(context)

            fun fmt(h: Double): String {
                val hi = h.toInt(); val mi = ((h - hi) * 60).toInt()
                return if (hi > 0) "${hi}h ${mi}m" else "${mi}m"
            }

            // Instagram
            maybeAlert(prefs, "instagram_$today", NOTIF_INSTAGRAM,
                instaHrs >= THRESHOLD_INSTAGRAM_HRS,
                title   = "📸 Instagram Overuse — ${fmt(instaHrs)}",
                message = "You've spent ${fmt(instaHrs)} on Instagram today. Studies link >1.5h to anxiety.",
                bigText = "Instagram: ${fmt(instaHrs)} today. Research shows excessive Instagram use increases social comparison anxiety. Try grayscale mode or set a timer.")

            // TikTok
            maybeAlert(prefs, "tiktok_$today", NOTIF_TIKTOK,
                tiktokHrs >= THRESHOLD_TIKTOK_HRS,
                title   = "🎵 TikTok Limit Reached — ${fmt(tiktokHrs)}",
                message = "${fmt(tiktokHrs)} on TikTok. Short-form content is highly addictive.",
                bigText = "TikTok: ${fmt(tiktokHrs)} today. The dopamine loop from short videos makes it hard to stop. Consider setting a 1h daily limit in TikTok settings.")

            // YouTube
            maybeAlert(prefs, "youtube_$today", NOTIF_YOUTUBE,
                ytHrs >= THRESHOLD_YOUTUBE_HRS,
                title   = "▶️ YouTube: ${fmt(ytHrs)} Today",
                message = "Extended YouTube use can displace sleep and increase stress.",
                bigText = "YouTube: ${fmt(ytHrs)} today. Avoid watching after 9 PM — it pushes back your sleep time. Try the queue instead of autoplay.")

            // Social media total
            maybeAlert(prefs, "social_$today", NOTIF_SOCIAL_MEDIA,
                socialHrs >= THRESHOLD_SOCIAL_HRS,
                title   = "📱 Social Media Overuse — ${fmt(socialHrs)}",
                message = "${fmt(socialHrs)} on social media today. Limit: 2h.",
                bigText = "Social media: ${fmt(socialHrs)} today — above the 2h healthy limit. Excessive social media use is directly linked to elevated stress and anxiety. Try a 30-minute break now.")

            // Gaming
            maybeAlert(prefs, "gaming_$today", NOTIF_GAMING,
                gamingHrs >= THRESHOLD_GAMING_HRS,
                title   = "🎮 Extended Gaming — ${fmt(gamingHrs)}",
                message = "${fmt(gamingHrs)} gaming today. Take a break!",
                bigText = "Gaming: ${fmt(gamingHrs)} today. Long sessions increase cortisol and cause eye strain. Take a 15-minute break, walk around, drink water.")

            // Total screen
            maybeAlert(prefs, "screen_$today", NOTIF_SCREEN_TIME,
                screenHrs >= THRESHOLD_SCREEN_HRS,
                title   = "📵 Screen Time: ${fmt(screenHrs)} Today",
                message = "Your total screen time is high. Try the 20-20-20 rule.",
                bigText = "Total screen time: ${fmt(screenHrs)} today. Every 20 minutes, look at something 20 feet away for 20 seconds to reduce eye strain and digital fatigue.")

            // Phone before bed
            val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
            maybeAlert(prefs, "bed_$today", NOTIF_PHONE_BED,
                hour >= 22 && phoneBed >= THRESHOLD_PHONE_BED,
                title   = "🌙 Phone Before Bed — ${phoneBed.toInt()}m",
                message = "You've used your phone for ${phoneBed.toInt()}m late at night.",
                bigText = "Phone usage: ${phoneBed.toInt()} minutes after 10 PM. Blue light suppresses melatonin and delays sleep onset by 30-60 minutes. Put the phone down now.")

            cleanOldKeys(prefs, today)
            Result.success()
        } catch (e: Exception) {
            Log.e("UsageAlertWorker", "Error: ${e.message}")
            Result.success()
        }
    }

    private fun maybeAlert(
        prefs: android.content.SharedPreferences,
        key: String, notifId: Int, condition: Boolean,
        title: String, message: String, bigText: String
    ) {
        if (!condition || prefs.getBoolean(key, false)) return
        sendNotification(notifId, title, message, bigText)
        prefs.edit().putBoolean(key, true).apply()
    }

    private fun sendNotification(notifId: Int, title: String, message: String, bigText: String) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pi = PendingIntent.getActivity(context, notifId, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notif = NotificationCompat.Builder(context, MindScanApp.CHANNEL_USAGE_ALERTS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title).setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(bigText))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pi).setAutoCancel(true).build()
        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(notifId, notif)
        Log.i("UsageAlertWorker", "Sent: $title")
    }

    private fun cleanOldKeys(prefs: android.content.SharedPreferences, today: String) {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val todayMs = sdf.parse(today)?.time ?: return
        val editor = prefs.edit()
        prefs.all.keys.forEach { key ->
            val dateStr = key.substringAfterLast("_")
            try {
                val keyMs = sdf.parse(dateStr)?.time ?: return@forEach
                if ((todayMs - keyMs) / 86_400_000L > 3) editor.remove(key)
            } catch (e: Exception) {}
        }
        editor.apply()
    }
}
