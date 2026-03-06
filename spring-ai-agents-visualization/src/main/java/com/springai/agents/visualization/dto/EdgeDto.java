package com.springai.agents.visualization.dto;

import lombok.Builder;
import lombok.Value;

/**
 * DTO representing a directed edge between two nodes.
 */
@Value
@Builder
public class EdgeDto {
    String from;
    String to;
}

