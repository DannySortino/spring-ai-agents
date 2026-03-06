package com.springai.agents.visualization.dto;

import lombok.Builder;
import lombok.Value;

/**
 * Performance stats for a single node.
 */
@Value
@Builder
public class NodePerfDto {
    String nodeId;
    String nodeType;
    double avgMs;
    double p95Ms;
    long maxMs;
    long minMs;
    int executionCount;
}

