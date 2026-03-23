package com.mindscan.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDateTime;
import java.util.List;

@Entity @Table(name = "stress_history")
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class StressHistory {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @ToString.Exclude @EqualsAndHashCode.Exclude
    private User user;

    @Column(name = "stress_level",  nullable = false, length = 20) private String stressLevel;
    @Column(name = "stress_score",  nullable = false) private Double stressScore;
    @Column(nullable = false)       private Double confidence;

    // Inputs snapshot
    @Column(name = "sleep_duration")     private Double sleepDuration;
    @Column(name = "sleep_quality")      private Double sleepQuality;
    @Column(name = "screen_time")        private Double screenTime;
    @Column(name = "phone_before_sleep") private Double phoneBeforeSleep;
    @Column(name = "mental_fatigue")     private Double mentalFatigue;
    @Column(name = "physical_activity")  private Double physicalActivity;
    @Column(name = "daily_steps")        private Double dailySteps;
    @Column(name = "caffeine_intake")    private Double caffeineIntake;
    @Column(name = "notifications")      private Double notifications;
    @Column(name = "age")                private Double age;
    @Column(name = "gender")             private Integer gender;

    // App usage snapshot
    @Column(name = "social_media_hours")  private Double socialMediaHours;
    @Column(name = "gaming_hours")        private Double gamingHours;
    @Column(name = "productivity_hours")  private Double productivityHours;

    // AI probabilities
    @Column(name = "prob_low")    private Double probLow;
    @Column(name = "prob_medium") private Double probMedium;
    @Column(name = "prob_high")   private Double probHigh;

    // AI generated insight sentence
    @Column(name = "insight", length = 500) private String insight;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "stress_recommendations",
        joinColumns = @JoinColumn(name = "stress_history_id"))
    @Column(name = "recommendation", length = 500)
    private List<String> recommendations;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false) private LocalDateTime createdAt;
}
