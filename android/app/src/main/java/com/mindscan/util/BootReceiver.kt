package com.mindscan.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.*
import com.mindscan.MindScanApp
import java.util.Calendar
import java.util.concurrent.TimeUnit

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            scheduleDailyPrediction(context)
            scheduleUsageAlerts(context)
        }
    }

    private fun scheduleDailyPrediction(context: Context) {
        val now    = Calendar.getInstance()
        val target = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 22)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (now.after(target)) target.add(Calendar.DAY_OF_YEAR, 1)

        val delay = target.timeInMillis - now.timeInMillis

        val work = PeriodicWorkRequestBuilder<DailyPredictionWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .setBackoffCriteria(BackoffPolicy.LINEAR, 15, TimeUnit.MINUTES)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            MindScanApp.WORK_DAILY_PREDICTION,
            ExistingPeriodicWorkPolicy.KEEP,
            work
        )
    }

    private fun scheduleUsageAlerts(context: Context) {
        val work = PeriodicWorkRequestBuilder<UsageAlertWorker>(2, TimeUnit.HOURS)
            .setInitialDelay(30, TimeUnit.MINUTES)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                    .build()
            )
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            MindScanApp.WORK_USAGE_ALERTS,
            ExistingPeriodicWorkPolicy.KEEP,
            work
        )
    }
}
