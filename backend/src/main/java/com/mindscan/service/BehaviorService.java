package com.mindscan.service;

import com.mindscan.dto.Dtos.*;
import com.mindscan.model.User;
import com.mindscan.model.UserBehaviorData;
import com.mindscan.repository.UserBehaviorDataRepository;
import com.mindscan.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service @RequiredArgsConstructor @Slf4j
public class BehaviorService {

    private final UserBehaviorDataRepository behaviorRepo;
    private final UserRepository userRepo;

    /** Upsert today's behavior record — called automatically from StressService */
    public void saveBehaviorData(StressPredictRequest req, String username) {
        User user = userRepo.findByUsername(username).orElseThrow();
        LocalDate today = LocalDate.now();

        UserBehaviorData data = behaviorRepo.findByUserAndRecordDate(user, today)
            .orElse(UserBehaviorData.builder().user(user).recordDate(today).build());

        data.setSleepDuration(req.getSleepDuration());
        data.setSleepQuality(req.getSleepQuality());
        data.setAge(req.getAge());
        data.setGender(req.getGender());
        data.setMentalFatigue(req.getMentalFatigue());
        data.setPhysicalActivity(req.getPhysicalActivity());
        data.setDailySteps(req.getDailySteps());
        data.setCaffeineIntake(req.getCaffeineIntake());
        data.setTotalScreenTime(req.getScreenTime());
        data.setPhoneBeforeSleep(req.getPhoneBeforeSleep());
        data.setNotificationsCount(req.getNotifications());
        data.setSocialMediaHours(req.getSocialMediaHours());
        data.setGamingHours(req.getGamingHours());
        data.setProductivityHours(req.getProductivityHours());
        data.setInstagramHours(req.getInstagramHours());
        data.setWhatsappHours(req.getWhatsappHours());
        data.setYoutubeHours(req.getYoutubeHours());
        data.setSnapchatHours(req.getSnapchatHours());
        data.setFacebookHours(req.getFacebookHours());
        data.setTiktokHours(req.getTiktokHours());
        data.setEntertainmentHours(req.getEntertainmentHours());
        data.setMessagingHours(req.getMessagingHours());

        behaviorRepo.save(data);
        log.debug("Behavior data saved for user={} date={}", username, today);
    }

    /** Last 30 days behavior data */
    public BehaviorHistoryResponse getBehaviorHistory(String username) {
        User user = userRepo.findByUsername(username).orElseThrow();
        List<UserBehaviorData> records = behaviorRepo.findLast30ByUser(user, PageRequest.of(0, 30));

        List<BehaviorDataItem> items = records.stream().map(b -> BehaviorDataItem.builder()
            .id(b.getId()).recordDate(b.getRecordDate())
            .sleepDuration(b.getSleepDuration()).sleepQuality(b.getSleepQuality())
            .totalScreenTime(b.getTotalScreenTime()).socialMediaHours(b.getSocialMediaHours())
            .gamingHours(b.getGamingHours()).productivityHours(b.getProductivityHours())
            .instagramHours(b.getInstagramHours()).whatsappHours(b.getWhatsappHours())
            .youtubeHours(b.getYoutubeHours()).snapchatHours(b.getSnapchatHours())
            .facebookHours(b.getFacebookHours()).tiktokHours(b.getTiktokHours())
            .entertainmentHours(b.getEntertainmentHours()).messagingHours(b.getMessagingHours())
            .notificationsCount(b.getNotificationsCount()).createdAt(b.getCreatedAt())
            .build()).collect(Collectors.toList());

        return BehaviorHistoryResponse.builder().data(items).success(true).build();
    }

    /** Daily/weekly trend for a specific app */
    public AppTrendResponse getAppTrend(String appName, String username, int days) {
        User user = userRepo.findByUsername(username).orElseThrow();
        LocalDate since = LocalDate.now().minusDays(days - 1);
        List<UserBehaviorData> records = behaviorRepo.findRecentByUser(user, since);

        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("MMM dd");

        List<AppTrendPoint> trend = records.stream().map(b -> AppTrendPoint.builder()
            .date(b.getRecordDate().format(fmt))
            .hours(getAppHours(b, appName))
            .build()).sorted(Comparator.comparing(AppTrendPoint::getDate))
            .collect(Collectors.toList());

        double avg = trend.stream().mapToDouble(p -> p.getHours() != null ? p.getHours() : 0).average().orElse(0);
        double total = trend.stream().mapToDouble(p -> p.getHours() != null ? p.getHours() : 0).sum();

        return AppTrendResponse.builder()
            .appName(appName).trend(trend)
            .avgHours(Math.round(avg * 100.0) / 100.0)
            .totalHours(Math.round(total * 100.0) / 100.0)
            .success(true).build();
    }

    private Double getAppHours(UserBehaviorData b, String appName) {
        return switch (appName.toLowerCase()) {
            case "instagram"    -> b.getInstagramHours();
            case "whatsapp"     -> b.getWhatsappHours();
            case "youtube"      -> b.getYoutubeHours();
            case "snapchat"     -> b.getSnapchatHours();
            case "facebook"     -> b.getFacebookHours();
            case "tiktok"       -> b.getTiktokHours();
            case "social_media" -> b.getSocialMediaHours();
            case "gaming"       -> b.getGamingHours();
            case "productivity" -> b.getProductivityHours();
            case "entertainment"-> b.getEntertainmentHours();
            case "messaging"    -> b.getMessagingHours();
            default             -> b.getTotalScreenTime();
        };
    }
}
