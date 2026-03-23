package com.mindscan.dto;

import jakarta.validation.constraints.*;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public class Dtos {

    // ── Auth ──────────────────────────────────────────────────────────────────
    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class RegisterRequest {
        @NotBlank @Size(min=3, max=20)
        @Pattern(regexp="^[a-zA-Z0-9_]+$") private String username;
        private String email;
        @NotBlank @Size(min=6)             private String password;
    }

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class LoginRequest {
        @NotBlank private String username;
        @NotBlank private String password;
    }

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class AuthResponse {
        private String token; private String username;
        private String email; private String message; private boolean success;
    }

    // ── Stress Prediction ─────────────────────────────────────────────────────
    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class StressPredictRequest {
        @NotNull @DecimalMin("0") @DecimalMax("24")  private Double sleepDuration;
        @NotNull @DecimalMin("1") @DecimalMax("10")  private Double sleepQuality;
        @NotNull @DecimalMin("0") @DecimalMax("300") private Double physicalActivity;
        @NotNull @DecimalMin("0")                    private Double dailySteps;
        @NotNull @DecimalMin("0") @DecimalMax("24")  private Double screenTime;
        @NotNull @DecimalMin("0") @DecimalMax("240") private Double phoneBeforeSleep;
        @NotNull @DecimalMin("1") @DecimalMax("10")  private Double mentalFatigue;
        @NotNull @DecimalMin("0") @DecimalMax("20")  private Double caffeineIntake;
        @NotNull @DecimalMin("0")                    private Double notifications;
        @NotNull @DecimalMin("10") @DecimalMax("100") private Double age;
        @NotNull @Min(0) @Max(1)                     private Integer gender;
        // App-wise usage from device
        private Double socialMediaHours;
        private Double gamingHours;
        private Double productivityHours;
        // Individual app hours (new)
        private Double instagramHours;
        private Double whatsappHours;
        private Double youtubeHours;
        private Double snapchatHours;
        private Double facebookHours;
        private Double tiktokHours;
        private Double entertainmentHours;
        private Double messagingHours;
    }

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class StressPredictResponse {
        private Long id; private String stressLevel; private Double stressScore;
        private Double confidence; private Map<String,Double> probabilities;
        private List<String> recommendations; private String insight;
        private LocalDateTime createdAt; private boolean success; private String message;
    }

    // ── History & Stats ───────────────────────────────────────────────────────
    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class StressHistoryItem {
        private Long id; private String stressLevel; private Double stressScore;
        private Double confidence; private Double sleepDuration; private Double sleepQuality;
        private Double screenTime; private Double phoneBeforeSleep; private Double mentalFatigue;
        private Double physicalActivity; private Double dailySteps; private Double caffeineIntake;
        private Double notifications; private Double socialMediaHours; private Double gamingHours;
        private Double productivityHours;
        private Map<String,Double> probabilities; private List<String> recommendations;
        private String insight; private LocalDateTime createdAt;
    }

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class HistoryResponse {
        private List<StressHistoryItem> history; private long totalCount;
        private boolean success;
    }

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class StatsResponse {
        private long totalPredictions; private Double averageStressScore;
        private Map<String,Long> stressLevelCounts; private String dominantStressLevel;
        private List<StressHistoryItem> last7Days; private boolean success;
    }

    // ── Behavior Data DTOs ────────────────────────────────────────────────────
    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class BehaviorDataItem {
        private Long id; private LocalDate recordDate;
        private Double sleepDuration; private Double sleepQuality;
        private Double totalScreenTime; private Double socialMediaHours;
        private Double gamingHours; private Double productivityHours;
        private Double instagramHours; private Double whatsappHours;
        private Double youtubeHours; private Double snapchatHours;
        private Double facebookHours; private Double tiktokHours;
        private Double entertainmentHours; private Double messagingHours;
        private Double notificationsCount; private LocalDateTime createdAt;
    }

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class BehaviorHistoryResponse {
        private List<BehaviorDataItem> data; private boolean success;
    }

    // ── App Trend ─────────────────────────────────────────────────────────────
    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class AppTrendPoint {
        private String date;       // "Mar 21"
        private Double hours;      // usage hours that day
    }

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class AppTrendResponse {
        private String appName;
        private List<AppTrendPoint> trend;
        private Double avgHours;
        private Double totalHours;
        private boolean success;
    }

    // ── ML Internal ───────────────────────────────────────────────────────────
    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class MlPredictRequest {
        private double age; private double sleep_duration; private double quality_of_sleep;
        private double physical_activity_level; private double daily_steps;
        private double daily_screen_time_hours; private double social_media_usage_hours;
        private double gaming_app_usage_hours; private double productivity_app_usage_hours;
        private double mental_fatigue_score; private double phone_before_sleep_min;
    }

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class MlPredictResponse {
        private String stress_level; private Double stress_score;
        private Double confidence; private Map<String,Double> probabilities;
        private List<String> recommendations;
    }

    // ── Generic ───────────────────────────────────────────────────────────────
    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class ApiResponse<T> {
        private boolean success; private String message; private T data;
    }
}
