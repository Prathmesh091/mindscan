package com.mindscan.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "user_behavior_data",
    uniqueConstraints = @UniqueConstraint(
        name = "uc_behavior_user_date", columnNames = {"user_id","record_date"}))
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class UserBehaviorData {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @ToString.Exclude @EqualsAndHashCode.Exclude
    private User user;

    // Manual inputs
    @Column(name = "sleep_duration",  nullable = false) private Double sleepDuration;
    @Column(name = "sleep_quality",   nullable = false) private Double sleepQuality;
    @Column(name = "age")             private Double age;
    @Column(name = "gender")          private Integer gender;
    @Column(name = "mental_fatigue")  private Double mentalFatigue;
    @Column(name = "physical_activity") private Double physicalActivity;
    @Column(name = "daily_steps")     private Double dailySteps;
    @Column(name = "caffeine_intake") private Double caffeineIntake;

    // Auto-collected from device
    @Column(name = "total_screen_time")   private Double totalScreenTime;
    @Column(name = "phone_before_sleep")  private Double phoneBeforeSleep;
    @Column(name = "notifications_count") private Double notificationsCount;

    // Per-app usage (hours)
    @Column(name = "instagram_hours")    private Double instagramHours;
    @Column(name = "whatsapp_hours")     private Double whatsappHours;
    @Column(name = "youtube_hours")      private Double youtubeHours;
    @Column(name = "snapchat_hours")     private Double snapchatHours;
    @Column(name = "facebook_hours")     private Double facebookHours;
    @Column(name = "tiktok_hours")       private Double tiktokHours;
    @Column(name = "social_media_hours") private Double socialMediaHours;
    @Column(name = "gaming_hours")       private Double gamingHours;
    @Column(name = "productivity_hours") private Double productivityHours;
    @Column(name = "entertainment_hours") private Double entertainmentHours;
    @Column(name = "messaging_hours")    private Double messagingHours;

    @Column(name = "record_date", nullable = false) private LocalDate recordDate;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false) private LocalDateTime createdAt;
}
