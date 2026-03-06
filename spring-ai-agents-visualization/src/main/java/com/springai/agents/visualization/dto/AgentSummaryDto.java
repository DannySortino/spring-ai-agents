package com.springai.agents.visualization.dto;

import lombok.Builder;
import lombok.Value;

/**
 * Summary of an agent for the dashboard view.
 */
@Value
@Builder
public class AgentSummaryDto {
    String name;
    String description;
    int workflowCount;
    boolean multiWorkflow;
    int invocationCount;
}

