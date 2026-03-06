package com.springai.agents.visualization.controller;

import com.springai.agents.visualization.dto.PerformanceStatsDto;
import com.springai.agents.visualization.service.PerformanceAnalyticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST API for performance analytics.
 */
@RestController
@RequestMapping("/agents-ui/api/analytics")
@RequiredArgsConstructor
public class AnalyticsApiController {

    private final PerformanceAnalyticsService analyticsService;

    @GetMapping
    public List<PerformanceStatsDto> allStats() {
        return analyticsService.getAllStats();
    }

    @GetMapping("/{agent}")
    public ResponseEntity<PerformanceStatsDto> agentStats(@PathVariable String agent) {
        return ResponseEntity.ok(analyticsService.getAgentStats(agent));
    }

    @GetMapping("/{agent}/{workflow}")
    public ResponseEntity<PerformanceStatsDto> workflowStats(@PathVariable String agent,
                                                              @PathVariable String workflow) {
        return ResponseEntity.ok(analyticsService.getWorkflowStats(agent, workflow));
    }
}

