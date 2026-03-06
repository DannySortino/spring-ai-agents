package com.springai.agents.visualization.dto;

import lombok.Builder;
import lombok.Value;

/**
 * Request body for executing an agent.
 */
@Value
@Builder
public class ExecutionRequestDto {
    String input;
}

