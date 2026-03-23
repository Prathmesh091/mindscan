package com.mindscan.network

import android.content.Context
import com.mindscan.util.TokenManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object ApiClient {

    // ── CHANGE THIS to your Render URL when deployed ──────────────────────────
    // For local testing with emulator:  http://10.0.2.2:8080/
    // For local testing with real phone: http://YOUR_PC_IP:8080/
    // For Render deployment:             https://mindscan-backend.onrender.com/
    const val BASE_URL = "https://mindscan-backend.onrender.com/"

    private var retrofit: Retrofit? = null
    private var apiService: ApiService? = null

    fun init(context: Context) {
        val tokenManager = TokenManager(context)

        val authInterceptor = Interceptor { chain ->
            val token = runBlocking {
                try { tokenManager.getToken().first() } catch (e: Exception) { null }
            }
            val request = if (!token.isNullOrBlank()) {
                chain.request().newBuilder()
                    .addHeader("Authorization", "Bearer $token")
                    .build()
            } else {
                chain.request()
            }
            chain.proceed(request)
        }

        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .addInterceptor(loggingInterceptor)
            .connectTimeout(60, TimeUnit.SECONDS)   // longer for Render cold start
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()

        retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        apiService = retrofit!!.create(ApiService::class.java)
    }

    fun getApiService(): ApiService {
        return apiService
            ?: throw IllegalStateException("ApiClient not initialized. Call init() first.")
    }
}
