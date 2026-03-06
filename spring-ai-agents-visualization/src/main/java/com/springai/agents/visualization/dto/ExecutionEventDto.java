package com.springai.agents.visualization.dto;

import lombok.Builder;
import lombok.Value;

/**
 * WebSocket event envelope for real-time execution updates.
 */
@Value
@Builder
public class ExecutionEventDto {
    String eventType;
    String executionId;
    String agentName;
    String workflowName;
    long timestamp;
    String nodeId;
    String nodeType;
    long durationMs;
    String output;
    int nodeCount;
    String status;
}

