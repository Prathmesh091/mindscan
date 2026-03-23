package com.mindscan.service;

import com.mindscan.dto.Dtos;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class RecommendationService {

    /**
     * Augments ML-generated recommendations with rule-based logic.
     * Rules:
     *  - sleep < 6h      → sleep advice
     *  - screen > 7h     → digital detox
     *  - phoneBeforeSleep > 60 → social media / night screen
     *  - gaming proxy: screen > 5h AND notifications < 30 → gaming advice
     *  - steps < 5000 OR physicalActivity < 30 → movement
     *  - mentalFatigue >= 7 → mindfulness
     *  - caffeineIntake >= 4 → caffeine
     *  - stressLevel == "High" → high stress action plan
     */
    public List<String> generateRecommendations(Dtos.StressPredictRequest req, String stressLevel) {
        List<String> recs = new ArrayList<>();

        // Sleep
        if (req.getSleepDuration() < 6.0) {
            recs.add("🌙 You're sleeping less than 6 hours. Aim for 7–9 hours — it's the single biggest lever for stress reduction.");
        } else if (req.getSleepDuration() < 7.0) {
            recs.add("😴 Getting close to 7 hours but not quite there. Try going to bed 30 minutes earlier tonight.");
        }

        // Digital detox
        if (req.getScreenTime() > 7.0) {
            recs.add("📵 Your screen time is very high. Set app timers and aim for under 5 hours of recreational screen use per day.");
        } else if (req.getScreenTime() > 5.0) {
            recs.add("📱 Moderate screen time detected. Take a 5-minute screen break every hour to reduce digital fatigue.");
        }

        // Social media / phone before sleep
        if (req.getPhoneBeforeSleep() > 60.0) {
            recs.add("📴 Avoid phone use at least 1 hour before bed. Blue light suppresses melatonin and delays sleep onset.");
        } else if (req.getPhoneBeforeSleep() > 30.0) {
            recs.add("🌛 Try reducing pre-sleep phone use to under 30 minutes for better sleep quality.");
        }

        // Notifications overload
        if (req.getNotifications() > 150.0) {
            recs.add("🔕 You're receiving 150+ notifications daily. Turn off non-essential alerts to reduce constant interruptions.");
        }

        // Movement (steps < 5000 OR physical activity < 30 min)
        if (req.getDailySteps() < 5000 || req.getPhysicalActivity() < 30.0) {
            recs.add("🚶 Low activity detected. Try for 8,000 steps or 30+ minutes of movement daily — even a brisk walk helps lower cortisol.");
        }

        // Gaming proxy: high screen, low notifications (likely gaming/streaming)
        if (req.getScreenTime() > 5.0 && req.getNotifications() < 30.0) {
            recs.add("🎮 High screen time with low notifications suggests extended gaming or streaming. Set a hard stop 1 hour before bed.");
        }

        // Mental fatigue
        if (req.getMentalFatigue() >= 8.0) {
            recs.add("🧘 High mental fatigue score. Try 10 minutes of box breathing (4 in, hold 4, out 4, hold 4) daily.");
        } else if (req.getMentalFatigue() >= 6.0) {
            recs.add("🧠 Elevated mental fatigue. Short mindfulness breaks during the day can help reset your focus.");
        }

        // Caffeine
        if (req.getCaffeineIntake() >= 4.0) {
            recs.add("☕ High caffeine intake elevates cortisol and disrupts sleep. Try capping at 2–3 cups and avoid caffeine after 2 PM.");
        }

        // High stress
        if ("High".equals(stressLevel)) {
            recs.add("⚠️ High stress level detected. Consider speaking with a mental health professional and prioritising rest this week.");
            recs.add("🆘 Grounding technique: Name 5 things you can see, 4 you can touch, 3 you can hear, 2 you can smell, 1 you can taste.");
        }

        // Positive fallback
        if (recs.isEmpty()) {
            recs.add("✅ Your behavioral metrics look healthy. Keep up the great habits!");
            recs.add("💪 Continue your current sleep and activity routine — you're managing stress well.");
        }

        return recs;
    }
}
