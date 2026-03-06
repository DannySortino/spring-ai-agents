package com.springai.agents.visualization.dto;

import lombok.Builder;
import lombok.Value;

import java.util.List;

/**
 * Detailed agent info including all workflows.
 */
@Value
@Builder
public class AgentDetailDto {
    String name;
    String description;
    int workflowCount;
    boolean multiWorkflow;
    int invocationCount;
    List<WorkflowDto> workflows;
}

