package com.springai.agents.visualization.dto;

import com.springai.agents.visualization.model.NodeStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Status of a single node during execution.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NodeStatusDto {
    private String nodeId;
    private String nodeType;
    private NodeStatus status;
    private long startedAt;
    private long durationMs;
    private String resultPreview;
}

