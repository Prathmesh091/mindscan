package com.mindscan.controller;

import com.mindscan.dto.Dtos;
import com.mindscan.service.StressService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/stress")
@RequiredArgsConstructor
@Slf4j
public class StressController {

    private final StressService stressService;

    @PostMapping("/predict")
    public ResponseEntity<Dtos.StressPredictResponse> predict(
            @Valid @RequestBody Dtos.StressPredictRequest req,
            @AuthenticationPrincipal UserDetails ud) {
        return ResponseEntity.ok(stressService.predict(req, ud.getUsername()));
    }

    /** Used by Android — returns history for the authenticated user */
    @GetMapping("/history")
    public ResponseEntity<Dtos.HistoryResponse> getHistory(
            @AuthenticationPrincipal UserDetails ud) {
        return ResponseEntity.ok(stressService.getHistory(ud.getUsername()));
    }

    /** Alternative path — kept for completeness */
    @GetMapping("/history/{userId}")
    public ResponseEntity<Dtos.HistoryResponse> getHistoryById(
            @PathVariable Long userId,
            @AuthenticationPrincipal UserDetails ud) {
        // Still scoped to the authenticated user for security
        return ResponseEntity.ok(stressService.getHistory(ud.getUsername()));
    }

    @GetMapping("/stats")
    public ResponseEntity<Dtos.StatsResponse> getStats(
            @AuthenticationPrincipal UserDetails ud) {
        return ResponseEntity.ok(stressService.getStats(ud.getUsername()));
    }
}
