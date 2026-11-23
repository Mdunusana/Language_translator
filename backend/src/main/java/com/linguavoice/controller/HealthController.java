package com.linguavoice.controller;

import com.linguavoice.dto.HealthResponse;
import com.linguavoice.service.HealthService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** {@code GET /api/health} — public liveness/readiness probe used by the frontend and Docker. */
@RestController
@RequestMapping("/api/health")
public class HealthController {

    private final HealthService healthService;

    public HealthController(HealthService healthService) {
        this.healthService = healthService;
    }

    @GetMapping
    public ResponseEntity<HealthResponse> health() {
        HealthResponse health = healthService.check();
        HttpStatus status = HealthService.UP.equals(health.status()) ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE;
        return ResponseEntity.status(status).body(health);
    }
}
