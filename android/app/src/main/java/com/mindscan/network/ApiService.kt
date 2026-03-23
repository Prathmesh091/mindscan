package com.mindscan.network

import retrofit2.Response
import retrofit2.http.*

interface ApiService {

    @POST("api/auth/register")
    suspend fun register(@Body request: RegisterRequest): Response<AuthResponse>

    @POST("api/auth/login")
    suspend fun login(@Body request: LoginRequest): Response<AuthResponse>

    @POST("api/stress/predict")
    suspend fun predict(@Body request: StressPredictRequest): Response<StressPredictResponse>

    @GET("api/stress/history")
    suspend fun getHistory(): Response<HistoryResponse>

    @GET("api/stress/stats")
    suspend fun getStats(): Response<StatsResponse>

    /** Per-app usage trend from backend — app=instagram|whatsapp|youtube etc., days=7|30 */
    @GET("api/behavior/trend")
    suspend fun getAppTrend(
        @Query("app") app: String,
        @Query("days") days: Int = 7
    ): Response<AppTrendResponse>
}
