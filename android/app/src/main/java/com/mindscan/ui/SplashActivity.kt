package com.mindscan.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.mindscan.util.TokenManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

@SuppressLint("CustomSplashScreen")
class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        lifecycleScope.launch {
            try {
                delay(1500L)
                val token = TokenManager(applicationContext).getToken().firstOrNull()
                if (!token.isNullOrBlank()) {
                    startActivity(Intent(this@SplashActivity, MainActivity::class.java))
                } else {
                    startActivity(Intent(this@SplashActivity, AuthActivity::class.java))
                }
            } catch (e: Exception) {
                Log.e("SplashActivity", "Init error", e)
                startActivity(Intent(this@SplashActivity, AuthActivity::class.java))
            } finally {
                finish()
            }
        }
    }
}
