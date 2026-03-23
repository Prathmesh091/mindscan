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
import com.mindscan.network.ApiClient
import com.mindscan.network.StressPredictRequest
import com.mindscan.ui.MainActivity
import kotlinx.coroutines.flow.first

class DailyPredictionWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            if (!BehaviorCollector.hasUsagePermission(context)) {
                showReminderNotification(); return Result.success()
            }
            val tokenManager = TokenManager(context)
            val token = tokenManager.getToken().first()
            if (token.isNullOrBlank()) return Result.success()

            // Auto-collected from device
            val screenTime    = BehaviorCollector.getTodayScreenTimeHours(context)
            val notifications = BehaviorCollector.getTodayNotificationsCount(context)
            val phoneBefore   = BehaviorCollector.getPhoneUsageBeforeSleepMinutes(context)
            val socialMedia   = BehaviorCollector.getSocialMediaHours(context)
            val gaming        = BehaviorCollector.getGamingHours(context)
            val productivity  = BehaviorCollector.getProductivityHours(context)
            val entertainment = BehaviorCollector.getEntertainmentHours(context)
            val messaging     = BehaviorCollector.getMessagingHours(context)
            val instagram     = BehaviorCollector.getInstagramHours(context)
            val whatsapp      = BehaviorCollector.getWhatsappHours(context)
            val youtube       = BehaviorCollector.getYoutubeHours(context)
            val snapchat      = BehaviorCollector.getSnapchatHours(context)
            val facebook      = BehaviorCollector.getFacebookHours(context)
            val tiktok        = BehaviorCollector.getTiktokHours(context)

            // Manual profile (from last Dashboard submission)
            val prefs = context.getSharedPreferences("mindscan_daily_prefs", Context.MODE_PRIVATE)
            val sleepDuration   = prefs.getFloat("sleep_duration",    7f).toDouble()
            val sleepQuality    = prefs.getFloat("sleep_quality",     6f).toDouble()
            val physicalAct     = prefs.getFloat("physical_activity", 30f).toDouble()
            val dailySteps      = prefs.getFloat("daily_steps",       6000f).toDouble()
            val mentalFatigue   = prefs.getFloat("mental_fatigue",    5f).toDouble()
            val caffeine        = prefs.getFloat("caffeine",          2f).toDouble()
            val age             = prefs.getFloat("age",               25f).toDouble()
            val gender          = prefs.getInt("gender",              0)

            val request = StressPredictRequest(
                sleepDuration    = sleepDuration,
                sleepQuality     = sleepQuality,
                physicalActivity = physicalAct,
                dailySteps       = dailySteps,
                age              = age,
                gender           = gender,
                screenTime       = screenTime,
                phoneBeforeSleep = phoneBefore,
                mentalFatigue    = mentalFatigue,
                caffeineIntake   = caffeine,
                notifications    = notifications.toDouble(),
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

            val response = ApiClient.getApiService().predict(request)
            if (response.isSuccessful && response.body()?.success == true) {
                val result = response.body()!!
                showPredictionNotification(
                    stressLevel    = result.stressLevel    ?: "Unknown",
                    stressScore    = result.stressScore?.toInt() ?: 0,
                    insight        = result.insight,
                    recommendation = result.recommendations?.firstOrNull() ?: "Check the app for details."
                )
                Result.success()
            } else Result.retry()

        } catch (e: Exception) {
            Log.e("DailyPredictionWorker", "Failed: ${e.message}")
            Result.retry()
        }
    }

    private fun showPredictionNotification(
        stressLevel: String, stressScore: Int,
        insight: String?, recommendation: String
    ) {
        val emoji = when (stressLevel) { "Low" -> "🟢"; "Medium" -> "🟡"; "High" -> "🔴"; else -> "📊" }
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pi = PendingIntent.getActivity(context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val bigText = buildString {
            append("Stress score: $stressScore/100 ($stressLevel).\n\n")
            if (!insight.isNullOrBlank()) append("$insight\n\n")
            append(recommendation)
        }

        val notif = NotificationCompat.Builder(context, MindScanApp.CHANNEL_DAILY_PREDICTION)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("$emoji Today's Stress Report — $stressLevel")
            .setContentText("Score: $stressScore/100 · $recommendation")
            .setStyle(NotificationCompat.BigTextStyle().bigText(bigText))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pi).setAutoCancel(true).build()

        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .notify(MindScanApp.NOTIFICATION_DAILY_PREDICTION, notif)
    }

    private fun showReminderNotification() {
        val intent = Intent(context, MainActivity::class.java)
        val pi = PendingIntent.getActivity(context, 1, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notif = NotificationCompat.Builder(context, MindScanApp.CHANNEL_DAILY_PREDICTION)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("MindScan needs permission")
            .setContentText("Grant usage access to enable automatic stress predictions.")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pi).setAutoCancel(true).build()
        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .notify(MindScanApp.NOTIFICATION_PERMISSION_REMINDER, notif)
    }
}
