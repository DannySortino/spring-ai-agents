package com.springai.agents.visualization.controller;

import com.springai.agents.visualization.dto.PerformanceStatsDto;
import com.springai.agents.visualization.service.PerformanceAnalyticsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("AnalyticsApiController")
class AnalyticsApiControllerTest {

    private PerformanceAnalyticsService analyticsService;
    private AnalyticsApiController controller;

    @BeforeEach
    void setUp() {
        analyticsService = mock(PerformanceAnalyticsService.class);
        controller = new AnalyticsApiController(analyticsService);
    }

    @Test
    @DisplayName("GET /analytics returns all stats")
    void allStats() {
        PerformanceStatsDto s1 = PerformanceStatsDto.builder()
                .agentName("agent-a").totalRuns(10).avgDurationMs(200).nodeStats(List.of()).build();
        PerformanceStatsDto s2 = PerformanceStatsDto.builder()
                .agentName("agent-b").totalRuns(5).avgDurationMs(100).nodeStats(List.of()).build();
        when(analyticsService.getAllStats()).thenReturn(List.of(s1, s2));

        List<PerformanceStatsDto> result = controller.allStats();

        assertEquals(2, result.size());
        assertEquals("agent-a", result.get(0).getAgentName());
    }

    @Test
    @DisplayName("GET /analytics/{agent} returns agent stats")
    void agentStats() {
        PerformanceStatsDto stats = PerformanceStatsDto.builder()
                .agentName("my-agent").totalRuns(3).avgDurationMs(150).nodeStats(List.of()).build();
        when(analyticsService.getAgentStats("my-agent")).thenReturn(stats);

        ResponseEntity<PerformanceStatsDto> response = controller.agentStats("my-agent");

        assertEquals(200, response.getStatusCode().value());
        assertEquals("my-agent", response.getBody().getAgentName());
        assertEquals(3, response.getBody().getTotalRuns());
    }

    @Test
    @DisplayName("GET /analytics/{agent}/{workflow} returns workflow stats")
    void workflowStats() {
        PerformanceStatsDto stats = PerformanceStatsDto.builder()
                .agentName("my-agent").workflowName("analyze").totalRuns(2)
                .avgDurationMs(100).nodeStats(List.of()).build();
        when(analyticsService.getWorkflowStats("my-agent", "analyze")).thenReturn(stats);

        ResponseEntity<PerformanceStatsDto> response = controller.workflowStats("my-agent", "analyze");

        assertEquals(200, response.getStatusCode().value());
        assertEquals("analyze", response.getBody().getWorkflowName());
    }
}

