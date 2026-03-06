package com.springai.agents.visualization.dto;

import lombok.Builder;
import lombok.Value;

import java.util.List;

/**
 * Aggregated performance statistics for an agent or workflow.
 */
@Value
@Builder
public class PerformanceStatsDto {
    String agentName;
    String workflowName;
    int totalRuns;
    double avgDurationMs;
    double p95DurationMs;
    long maxDurationMs;
    List<NodePerfDto> nodeStats;
}

