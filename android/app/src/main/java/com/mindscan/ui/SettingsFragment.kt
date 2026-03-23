package com.mindscan.ui

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.work.*
import com.mindscan.MindScanApp
import com.mindscan.databinding.FragmentSettingsBinding
import com.mindscan.util.BehaviorCollector
import com.mindscan.util.ReminderWorker
import com.mindscan.util.TokenManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.concurrent.TimeUnit

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        loadUserInfo()
        setupClickListeners()
        updatePermissionStatus()
    }

    override fun onResume() {
        super.onResume()
        updatePermissionStatus()
    }

    private fun loadUserInfo() {
        lifecycleScope.launch {
            val tokenManager = TokenManager(requireContext())
            // Only show username — no email field in new UI
            binding.tvUsername.text = tokenManager.getUsername().first() ?: "—"
        }
    }

    private fun setupClickListeners() {
        binding.btnUsagePermission.setOnClickListener {
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        }

        binding.switchDailyReminder.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) scheduleDailyReminder() else cancelDailyReminder()
        }

        binding.btnLogout.setOnClickListener {
            (requireActivity() as MainActivity).logout()
        }

        binding.btnAbout.setOnClickListener {
            Toast.makeText(
                requireContext(),
                "MindScan v1.0\nAI Behavioral Stress Monitoring\n" +
                "Powered by ANN + GBM + RF + ET Ensemble\n" +
                "Accuracy: 88.7%\n" +
                "Auto-prediction runs daily at 10 PM",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun updatePermissionStatus() {
        val hasPermission = BehaviorCollector.hasUsagePermission(requireContext())
        binding.tvPermissionStatus.text =
            if (hasPermission) "✅ Granted — real app usage active"
            else "❌ Not granted — tap Grant to enable"
        binding.btnUsagePermission.isEnabled = !hasPermission
    }

    private fun scheduleDailyReminder() {
        val request = PeriodicWorkRequestBuilder<ReminderWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(1, TimeUnit.HOURS)
            .build()
        WorkManager.getInstance(requireContext()).enqueueUniquePeriodicWork(
            "daily_stress_reminder",
            ExistingPeriodicWorkPolicy.REPLACE,
            request
        )
        Toast.makeText(requireContext(),
            "Daily reminders enabled", Toast.LENGTH_SHORT).show()
    }

    private fun cancelDailyReminder() {
        WorkManager.getInstance(requireContext())
            .cancelUniqueWork("daily_stress_reminder")
        Toast.makeText(requireContext(),
            "Daily reminders disabled", Toast.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
