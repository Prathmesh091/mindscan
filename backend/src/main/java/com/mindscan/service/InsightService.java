package com.mindscan.service;

import com.mindscan.dto.Dtos.StressPredictRequest;
import org.springframework.stereotype.Service;

/**
 * Generates a single dynamic "why" sentence for the stress prediction.
 * Example: "High stress detected due to low sleep (5h) and 3.5h of social media usage."
 */
@Service
public class InsightService {

    public String generateInsight(StressPredictRequest req, String stressLevel) {
        StringBuilder sb = new StringBuilder();

        // Opening
        sb.append(switch (stressLevel) {
            case "High"   -> "High stress detected — ";
            case "Medium" -> "Moderate stress detected — ";
            default       -> "Low stress detected — ";
        });

        int factors = 0;

        // Sleep
        if (req.getSleepDuration() != null && req.getSleepDuration() < 6.0) {
            sb.append(String.format("critically low sleep (%.1fh)", req.getSleepDuration()));
            factors++;
        } else if (req.getSleepDuration() != null && req.getSleepDuration() < 7.0) {
            sb.append(String.format("insufficient sleep (%.1fh)", req.getSleepDuration()));
            factors++;
        }

        // Social media
        Double sm = req.getSocialMediaHours();
        if (sm != null && sm > 2.5) {
            if (factors > 0) sb.append(factors == 1 ? " and " : ", ");
            sb.append(String.format("high social media use (%.1fh)", sm));
            factors++;
        }

        // Gaming
        Double gaming = req.getGamingHours();
        if (gaming != null && gaming > 1.5) {
            if (factors > 0) sb.append(factors == 1 ? " and " : ", ");
            sb.append(String.format("extended gaming (%.1fh)", gaming));
            factors++;
        }

        // Total screen time
        Double screen = req.getScreenTime();
        if (screen != null && screen > 7.0 && (sm == null || sm <= 2.5)) {
            if (factors > 0) sb.append(factors == 1 ? " and " : ", ");
            sb.append(String.format("high screen time (%.1fh)", screen));
            factors++;
        }

        // Mental fatigue
        Double fatigue = req.getMentalFatigue();
        if (fatigue != null && fatigue >= 7.0) {
            if (factors > 0) sb.append(factors == 1 ? " and " : ", ");
            sb.append(String.format("high mental fatigue (%.0f/10)", fatigue));
            factors++;
        }

        // Phone before sleep
        Double pbs = req.getPhoneBeforeSleep();
        if (pbs != null && pbs > 60.0) {
            if (factors > 0) sb.append(factors == 1 ? " and " : ", ");
            sb.append(String.format("phone use before bed (%.0fm)", pbs));
            factors++;
        }

        // Low activity
        Double steps = req.getDailySteps();
        if (steps != null && steps < 4000 && factors < 3) {
            if (factors > 0) sb.append(factors == 1 ? " and " : ", ");
            sb.append(String.format("very low activity (%.0f steps)", steps));
            factors++;
        }

        // Fallback for Low stress
        if (factors == 0) {
            sb.append(String.format("healthy sleep (%.1fh) and balanced screen usage",
                req.getSleepDuration() != null ? req.getSleepDuration() : 7.0));
        }

        sb.append(".");
        return sb.toString();
    }
}
