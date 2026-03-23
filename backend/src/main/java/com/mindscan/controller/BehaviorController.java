package com.mindscan.controller;

import com.mindscan.dto.Dtos.*;
import com.mindscan.service.BehaviorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/behavior")
@RequiredArgsConstructor
@Slf4j
public class BehaviorController {

    private final BehaviorService behaviorService;

    /** Last 30 days behavior history */
    @GetMapping("/history")
    public ResponseEntity<BehaviorHistoryResponse> getHistory(
            @AuthenticationPrincipal UserDetails ud) {
        return ResponseEntity.ok(behaviorService.getBehaviorHistory(ud.getUsername()));
    }

    /**
     * Per-app trend over N days.
     * GET /api/behavior/trend?app=instagram&days=7
     * GET /api/behavior/trend?app=whatsapp&days=30
     * Supported: instagram, whatsapp, youtube, snapchat, facebook, tiktok,
     *            social_media, gaming, productivity, entertainment, messaging
     */
    @GetMapping("/trend")
    public ResponseEntity<AppTrendResponse> getAppTrend(
            @RequestParam String app,
            @RequestParam(defaultValue = "7") int days,
            @AuthenticationPrincipal UserDetails ud) {
        if (days < 1 || days > 90)
            throw new IllegalArgumentException("days must be between 1 and 90");
        return ResponseEntity.ok(behaviorService.getAppTrend(app, ud.getUsername(), days));
    }
}
