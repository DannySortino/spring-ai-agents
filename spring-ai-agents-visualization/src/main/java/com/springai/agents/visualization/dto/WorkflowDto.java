package com.springai.agents.visualization.dto;

import lombok.Builder;
import lombok.Value;

import java.util.List;
import java.util.Map;

/**
 * DTO representing a single workflow's graph structure.
 */
@Value
@Builder
public class WorkflowDto {
    String name;
    String description;
    List<NodeDto> nodes;
    List<EdgeDto> edges;
    Map<Integer, List<String>> levelGroups;
}

