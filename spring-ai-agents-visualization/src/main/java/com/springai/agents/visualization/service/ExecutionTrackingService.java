package com.springai.agents.visualization.service;

import com.springai.agents.visualization.dto.*;
import com.springai.agents.visualization.model.ExecutionStatus;
import com.springai.agents.visualization.model.NodeStatus;
import com.springai.agents.workflow.event.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.Map;

/**
 * Listens to workflow lifecycle events and:
 * 1. Updates execution records in the history service
 * 2. Pushes live updates via WebSocket STOMP
 */
@Slf4j
@RequiredArgsConstructor
public class ExecutionTrackingService {

    private final ExecutionHistoryService historyService;
    private final SimpMessagingTemplate messagingTemplate;

    @EventListener
    public void onWorkflowStarted(WorkflowStartedEvent event) {
        String executionId = extractExecutionId(event);
        if (executionId == null) return;

        String agentName = extractAgentName(event);
        ExecutionRecordDto record = historyService.getById(executionId);
        if (record != null) {
            record.setWorkflowName(event.getWorkflowName());
        }

        ExecutionEventDto eventDto = ExecutionEventDto.builder()
                .eventType("WORKFLOW_STARTED")
                .executionId(executionId)
                .agentName(agentName)
                .workflowName(event.getWorkflowName())
                .timestamp(System.currentTimeMillis())
                .nodeCount(event.getNodeCount())
                .status("RUNNING")
                .build();

        sendEvent(eventDto, agentName);
    }

    @EventListener
    public void onNodeStarted(NodeStartedEvent event) {
        String executionId = extractExecutionId(event);
        if (executionId == null) return;

        String agentName = extractAgentName(event);
        ExecutionRecordDto record = historyService.getById(executionId);
        if (record != null) {
            NodeStatusDto nodeStatus = NodeStatusDto.builder()
                    .nodeId(event.getNodeId())
                    .nodeType(event.getNodeType())
                    .status(NodeStatus.RUNNING)
                    .startedAt(System.currentTimeMillis())
                    .build();
            record.getNodeStatuses().put(event.getNodeId(), nodeStatus);
        }

        ExecutionEventDto eventDto = ExecutionEventDto.builder()
                .eventType("NODE_STARTED")
                .executionId(executionId)
                .agentName(agentName)
                .workflowName(event.getWorkflowName())
                .timestamp(System.currentTimeMillis())
                .nodeId(event.getNodeId())
                .nodeType(event.getNodeType())
                .status("RUNNING")
                .build();

        sendEvent(eventDto, agentName);
    }

    @EventListener
    public void onNodeCompleted(NodeCompletedEvent event) {
        String executionId = extractExecutionId(event);
        if (executionId == null) return;

        String agentName = extractAgentName(event);
        ExecutionRecordDto record = historyService.getById(executionId);
        if (record != null) {
            NodeStatusDto nodeStatus = record.getNodeStatuses().get(event.getNodeId());
            if (nodeStatus != null) {
                nodeStatus.setStatus(NodeStatus.COMPLETED);
                nodeStatus.setDurationMs(event.getDurationMs());
            }
        }

        ExecutionEventDto eventDto = ExecutionEventDto.builder()
                .eventType("NODE_COMPLETED")
                .executionId(executionId)
                .agentName(agentName)
                .workflowName(event.getWorkflowName())
                .timestamp(System.currentTimeMillis())
                .nodeId(event.getNodeId())
                .nodeType(event.getNodeType())
                .durationMs(event.getDurationMs())
                .status("COMPLETED")
                .build();

        sendEvent(eventDto, agentName);
    }

    @EventListener
    public void onWorkflowCompleted(WorkflowCompletedEvent event) {
        String executionId = extractExecutionId(event);
        if (executionId == null) return;

        String agentName = extractAgentName(event);
        ExecutionRecordDto record = historyService.getById(executionId);
        if (record != null) {
            record.setOutput(event.getOutput());
            record.setStatus(ExecutionStatus.COMPLETED);
            record.setCompletedAt(System.currentTimeMillis());
            record.setDurationMs(event.getDurationMs());
            historyService.update(record);
        }

        ExecutionEventDto eventDto = ExecutionEventDto.builder()
                .eventType("WORKFLOW_COMPLETED")
                .executionId(executionId)
                .agentName(agentName)
                .workflowName(event.getWorkflowName())
                .timestamp(System.currentTimeMillis())
                .durationMs(event.getDurationMs())
                .output(truncate(event.getOutput(), 500))
                .status("COMPLETED")
                .build();

        sendEvent(eventDto, agentName);
    }

    private void sendEvent(ExecutionEventDto eventDto, String agentName) {
        try {
            messagingTemplate.convertAndSend("/topic/executions/all", eventDto);
            if (agentName != null) {
                messagingTemplate.convertAndSend("/topic/executions/" + agentName, eventDto);
            }
        } catch (Exception e) {
            log.warn("Failed to send WebSocket event: {}", e.getMessage());
        }
    }

    private String extractExecutionId(WorkflowEvent event) {
        // The execution controller injects executionId into the execution context
        // which is propagated through the WorkflowExecutor source object
        if (event.getSource() instanceof Map<?, ?> ctx) {
            Object id = ctx.get("executionId");
            return id != null ? id.toString() : null;
        }
        return null;
    }

    private String extractAgentName(WorkflowEvent event) {
        if (event.getSource() instanceof Map<?, ?> ctx) {
            Object name = ctx.get("agentName");
            return name != null ? name.toString() : null;
        }
        return null;
    }

    private String truncate(String text, int maxLen) {
        if (text == null) return null;
        return text.length() <= maxLen ? text : text.substring(0, maxLen) + "...";
    }
}

