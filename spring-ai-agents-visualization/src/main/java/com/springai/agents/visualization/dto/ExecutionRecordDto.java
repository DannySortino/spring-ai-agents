package com.springai.agents.visualization.dto;

import com.springai.agents.visualization.model.ExecutionStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Record of a single workflow execution run.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExecutionRecordDto {
    private String id;
    private String agentName;
    private String workflowName;
    private String input;
    private String output;
    private ExecutionStatus status;
    private long startedAt;
    private long completedAt;
    private long durationMs;
    @Builder.Default
    private Map<String, NodeStatusDto> nodeStatuses = new ConcurrentHashMap<>();
}

