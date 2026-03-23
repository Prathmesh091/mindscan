package com.mindscan

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.work.*
import com.mindscan.network.ApiClient
import com.mindscan.util.DailyPredictionWorker
import com.mindscan.util.UsageAlertWorker
import java.util.Calendar
import java.util.concurrent.TimeUnit

class MindScanApp : Application() {

    override fun onCreate() {
        super.onCreate()
        ApiClient.init(this)
        createNotificationChannels()
        scheduleDailyPrediction()
        scheduleUsageAlerts()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)

            // Channel 1: Daily 10 PM stress report
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_DAILY_PREDICTION,
                    "Daily Stress Report",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "Your nightly automated stress analysis result"
                }
            )

            // Channel 2: Usage overuse alerts (social media, gaming, screen time)
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_USAGE_ALERTS,
                    "Usage Alerts",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "Alerts when app usage exceeds healthy limits"
                }
            )

            // Channel 3: Daily check-in reminders
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_STRESS_REMINDER,
                    "Stress Check Reminders",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Reminders to log your daily check-in"
                }
            )
        }
    }

    /**
     * Daily prediction at 10 PM — collects all data + sends stress result notification
     */
    private fun scheduleDailyPrediction() {
        val now    = Calendar.getInstance()
        val target = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 22)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (now.after(target)) target.add(Calendar.DAY_OF_YEAR, 1)

        val initialDelay = target.timeInMillis - now.timeInMillis

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val dailyWork = PeriodicWorkRequestBuilder<DailyPredictionWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.LINEAR, 15, TimeUnit.MINUTES)
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            WORK_DAILY_PREDICTION,
            ExistingPeriodicWorkPolicy.KEEP,
            dailyWork
        )
    }

    /**
     * Usage alert checks every 2 hours throughout the day.
     * Each alert fires maximum ONCE per day per category.
     * No network required — reads local device usage only.
     */
    private fun scheduleUsageAlerts() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.NOT_REQUIRED) // works offline
            .build()

        val alertWork = PeriodicWorkRequestBuilder<UsageAlertWorker>(
            2, TimeUnit.HOURS   // check every 2 hours
        )
            .setInitialDelay(30, TimeUnit.MINUTES) // first check 30 mins after app open
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            WORK_USAGE_ALERTS,
            ExistingPeriodicWorkPolicy.KEEP,
            alertWork
        )
    }

    companion object {
        // Notification channels
        const val CHANNEL_DAILY_PREDICTION      = "daily_prediction"
        const val CHANNEL_USAGE_ALERTS          = "usage_alerts"
        const val CHANNEL_STRESS_REMINDER       = "stress_reminder"

        // Notification IDs
        const val NOTIFICATION_DAILY_PREDICTION  = 1001
        const val NOTIFICATION_PERMISSION_REMINDER = 1002

        // WorkManager work names
        const val WORK_DAILY_PREDICTION         = "work_daily_prediction"
        const val WORK_USAGE_ALERTS             = "work_usage_alerts"
    }
}
