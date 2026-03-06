package com.springai.agents.visualization.service;

import com.springai.agents.agent.AgentRegistry;
import com.springai.agents.agent.AgentRuntime;
import com.springai.agents.visualization.dto.*;
import com.springai.agents.workflow.Workflow;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

/**
 * Reads the AgentRegistry and converts domain objects into visualization DTOs.
 */
@Slf4j
@RequiredArgsConstructor
public class AgentIntrospectionService {

    private final AgentRegistry agentRegistry;

    /**
     * Get summaries for all registered agents.
     */
    public List<AgentSummaryDto> getAllAgents() {
        return agentRegistry.getAgentNames().stream()
                .map(this::getAgentSummary)
                .filter(Objects::nonNull)
                .toList();
    }

    /**
     * Get a summary for a single agent.
     */
    public AgentSummaryDto getAgentSummary(String agentName) {
        AgentRuntime runtime = agentRegistry.getSyncAgent(agentName);
        if (runtime == null) return null;

        List<Workflow> workflows = runtime.getWorkflows();
        return AgentSummaryDto.builder()
                .name(runtime.getName())
                .description(runtime.getDescription())
                .workflowCount(workflows.size())
                .multiWorkflow(workflows.size() > 1)
                .invocationCount(runtime.getInvocationCount())
                .build();
    }

    /**
     * Get full detail for an agent including all workflow structures.
     */
    public AgentDetailDto getAgentDetail(String agentName) {
        AgentRuntime runtime = agentRegistry.getSyncAgent(agentName);
        if (runtime == null) return null;

        List<Workflow> workflows = runtime.getWorkflows();
        List<WorkflowDto> workflowDtos = workflows.stream()
                .map(this::convertWorkflow)
                .toList();

        return AgentDetailDto.builder()
                .name(runtime.getName())
                .description(runtime.getDescription())
                .workflowCount(workflows.size())
                .multiWorkflow(workflows.size() > 1)
                .invocationCount(runtime.getInvocationCount())
                .workflows(workflowDtos)
                .build();
    }

    /**
     * Get a single workflow's structure by agent name and workflow name.
     */
    public WorkflowDto getWorkflow(String agentName, String workflowName) {
        AgentRuntime runtime = agentRegistry.getSyncAgent(agentName);
        if (runtime == null) return null;

        return runtime.getWorkflows().stream()
                .filter(w -> w.getName().equals(workflowName))
                .findFirst()
                .map(this::convertWorkflow)
                .orElse(null);
    }

    private WorkflowDto convertWorkflow(Workflow workflow) {
        Map<Integer, List<String>> levelGroups = workflow.getLevelGroups();

        // Build nodeId → level map
        Map<String, Integer> nodeLevels = new HashMap<>();
        levelGroups.forEach((level, nodeIds) ->
                nodeIds.forEach(id -> nodeLevels.put(id, level)));

        // Convert nodes
        List<NodeDto> nodeDtos = workflow.getNodes().values().stream()
                .map(node -> NodeDto.from(node, nodeLevels.getOrDefault(node.getId(), 0)))
                .toList();

        // Convert edges
        List<EdgeDto> edgeDtos = workflow.getEdges().stream()
                .map(edge -> EdgeDto.builder().from(edge.from()).to(edge.to()).build())
                .toList();

        return WorkflowDto.builder()
                .name(workflow.getName())
                .description(workflow.getDescription())
                .nodes(nodeDtos)
                .edges(edgeDtos)
                .levelGroups(levelGroups)
                .build();
    }
}

