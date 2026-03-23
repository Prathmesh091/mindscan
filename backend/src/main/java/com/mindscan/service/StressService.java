package com.mindscan.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mindscan.dto.Dtos.*;
import com.mindscan.model.StressHistory;
import com.mindscan.model.User;
import com.mindscan.repository.StressHistoryRepository;
import com.mindscan.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.net.URI;
import java.net.http.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service @RequiredArgsConstructor @Slf4j
public class StressService {

    private final StressHistoryRepository historyRepo;
    private final UserRepository          userRepo;
    private final RecommendationService   recommendationService;
    private final InsightService          insightService;
    private final BehaviorService         behaviorService;

    @Value("${ml.service.url}")
    private String mlServiceUrl;

    private final ObjectMapper mapper     = new ObjectMapper();
    private final HttpClient   httpClient = HttpClient.newBuilder()
        .connectTimeout(java.time.Duration.ofSeconds(30)).build();

    public StressPredictResponse predict(StressPredictRequest req, String username) {
        try {
            // ── Save behavior data (upsert today) ──────────────────────────
            behaviorService.saveBehaviorData(req, username);

            // ── Build ML payload ───────────────────────────────────────────
            Map<String,Object> mlBody = new HashMap<>();
            mlBody.put("age",                          req.getAge());
            mlBody.put("sleep_duration",               req.getSleepDuration());
            mlBody.put("quality_of_sleep",             req.getSleepQuality());
            mlBody.put("physical_activity_level",      req.getPhysicalActivity());
            mlBody.put("daily_steps",                  req.getDailySteps());
            mlBody.put("daily_screen_time_hours",      req.getScreenTime());
            mlBody.put("social_media_usage_hours",     req.getSocialMediaHours() != null ? req.getSocialMediaHours() : 1.0);
            mlBody.put("gaming_app_usage_hours",       req.getGamingHours()       != null ? req.getGamingHours()       : 0.5);
            mlBody.put("productivity_app_usage_hours", req.getProductivityHours() != null ? req.getProductivityHours() : 1.0);
            mlBody.put("mental_fatigue_score",         req.getMentalFatigue());
            mlBody.put("phone_before_sleep_min",       req.getPhoneBeforeSleep());

            log.info("ML call: user={} sleep={}h social={}h gaming={}h screen={}h",
                username, req.getSleepDuration(), req.getSocialMediaHours(),
                req.getGamingHours(), req.getScreenTime());

            HttpRequest httpReq = HttpRequest.newBuilder()
                .uri(URI.create(mlServiceUrl + "/predict"))
                .header("Content-Type","application/json")
                .timeout(java.time.Duration.ofSeconds(60))
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(mlBody)))
                .build();

            HttpResponse<String> resp = httpClient.send(httpReq, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) throw new RuntimeException("ML error: " + resp.body());

            MlPredictResponse ml = mapper.readValue(resp.body(), MlPredictResponse.class);

            // ── Merge recommendations ───────────────────────────────────────
            List<String> allRecs = new ArrayList<>();
            if (ml.getRecommendations() != null) allRecs.addAll(ml.getRecommendations());
            recommendationService.generateRecommendations(req, ml.getStress_level())
                .forEach(r -> { if (!allRecs.contains(r)) allRecs.add(r); });

            // ── Generate insight ────────────────────────────────────────────
            String insight = insightService.generateInsight(req, ml.getStress_level());

            // ── Persist ────────────────────────────────────────────────────
            User user = userRepo.findByUsername(username).orElseThrow();
            StressHistory h = StressHistory.builder()
                .user(user)
                .stressLevel(ml.getStress_level())
                .stressScore(ml.getStress_score())
                .confidence(ml.getConfidence())
                .sleepDuration(req.getSleepDuration())
                .sleepQuality(req.getSleepQuality())
                .screenTime(req.getScreenTime())
                .phoneBeforeSleep(req.getPhoneBeforeSleep())
                .mentalFatigue(req.getMentalFatigue())
                .physicalActivity(req.getPhysicalActivity())
                .dailySteps(req.getDailySteps())
                .caffeineIntake(req.getCaffeineIntake())
                .notifications(req.getNotifications())
                .socialMediaHours(req.getSocialMediaHours())
                .gamingHours(req.getGamingHours())
                .productivityHours(req.getProductivityHours())
                .age(req.getAge()).gender(req.getGender())
                .insight(insight)
                .recommendations(allRecs).build();
            historyRepo.save(h);

            return StressPredictResponse.builder()
                .id(h.getId()).stressLevel(ml.getStress_level())
                .stressScore(ml.getStress_score()).confidence(ml.getConfidence())
                .probabilities(ml.getProbabilities()).recommendations(allRecs)
                .insight(insight).createdAt(h.getCreatedAt())
                .success(true).message("Prediction successful").build();

        } catch (Exception e) {
            log.error("Prediction failed: {}", e.getMessage(), e);
            throw new RuntimeException("Prediction failed: " + e.getMessage());
        }
    }

    public HistoryResponse getHistory(String username) {
        User user = userRepo.findByUsername(username).orElseThrow();
        List<StressHistory> all = historyRepo.findTop30ByUserOrderByCreatedAtDesc(user);
        return HistoryResponse.builder()
            .history(all.stream().map(this::toItem).collect(Collectors.toList()))
            .totalCount(historyRepo.countByUser(user)).success(true).build();
    }

    public StatsResponse getStats(String username) {
        User user = userRepo.findByUsername(username).orElseThrow();
        List<StressHistory> all = historyRepo.findTop30ByUserOrderByCreatedAtDesc(user);
        Map<String,Long> counts = all.stream()
            .collect(Collectors.groupingBy(StressHistory::getStressLevel, Collectors.counting()));
        String dominant = counts.entrySet().stream()
            .max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse("N/A");
        double avg = all.stream().mapToDouble(StressHistory::getStressScore).average().orElse(0);
        LocalDateTime sevenDaysAgo = LocalDateTime.now().minusDays(7);
        List<StressHistory> last7 = historyRepo.findRecentByUser(user, sevenDaysAgo);
        return StatsResponse.builder()
            .totalPredictions(historyRepo.countByUser(user)).averageStressScore(avg)
            .stressLevelCounts(counts).dominantStressLevel(dominant)
            .last7Days(last7.stream().map(this::toItem).collect(Collectors.toList()))
            .success(true).build();
    }

    private StressHistoryItem toItem(StressHistory h) {
        return StressHistoryItem.builder()
            .id(h.getId()).stressLevel(h.getStressLevel()).stressScore(h.getStressScore())
            .confidence(h.getConfidence()).sleepDuration(h.getSleepDuration())
            .sleepQuality(h.getSleepQuality()).screenTime(h.getScreenTime())
            .phoneBeforeSleep(h.getPhoneBeforeSleep()).mentalFatigue(h.getMentalFatigue())
            .physicalActivity(h.getPhysicalActivity()).dailySteps(h.getDailySteps())
            .caffeineIntake(h.getCaffeineIntake()).notifications(h.getNotifications())
            .socialMediaHours(h.getSocialMediaHours()).gamingHours(h.getGamingHours())
            .productivityHours(h.getProductivityHours())
            .recommendations(h.getRecommendations()).insight(h.getInsight())
            .createdAt(h.getCreatedAt()).build();
    }
}
