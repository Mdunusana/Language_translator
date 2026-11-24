package com.linguavoice.controller;

import com.linguavoice.dto.dashboard.StatisticsResponse;
import com.linguavoice.dto.translation.TranslationResponse;
import com.linguavoice.security.CurrentUser;
import com.linguavoice.service.DashboardService;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Dashboard data for the signed-in user. */
@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    /** @param timezone IANA zone for day buckets, e.g. {@code Africa/Johannesburg}; defaults to UTC */
    @GetMapping("/statistics")
    public StatisticsResponse statistics(@AuthenticationPrincipal Jwt jwt,
                                         @RequestParam(required = false) String timezone,
                                         @RequestParam(defaultValue = "" + DashboardService.DEFAULT_DAYS) int days) {
        return dashboardService.statistics(CurrentUser.id(jwt), timezone, days);
    }

    @GetMapping("/recent-activity")
    public List<TranslationResponse> recentActivity(@AuthenticationPrincipal Jwt jwt,
                                                    @RequestParam(defaultValue = "5") int limit) {
        return dashboardService.recentActivity(CurrentUser.id(jwt), limit);
    }
}
