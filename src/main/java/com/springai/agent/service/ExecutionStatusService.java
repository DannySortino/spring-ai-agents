package com.springai.agent.service;

import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for tracking and broadcasting real-time execution status of workflow nodes.
 * Maintains execution state and provides WebSocket updates for visualization.
 * 
 * @author Danny Sortino
 * @since 1.0.0
 */
@Service
public class ExecutionStatusService {
    
    private static final Logger logger = LoggerFactory.getLogger(ExecutionStatusService.class);
    
    // Map of execution ID to execution status
    private final Map<String, ExecutionStatus> executions = new ConcurrentHashMap<>();
    
    // Map of execution ID to node statuses
    private final Map<String, Map<String, NodeExecutionStatus>> nodeStatuses = new ConcurrentHashMap<>();
    
    @Setter
    private SimpMessagingTemplate messagingTemplate;
    
    public ExecutionStatusService() {
        // Constructor for conditional bean creation
    }
    
    /**
     * Clear all execution data (for testing purposes).
     */
    public void clearAll() {
        executions.clear();
        nodeStatuses.clear();
    }

    /**
     * Start tracking a new execution.
     */
    public String startExecution(String agentName, List<String> nodeIds) {
        String executionId = UUID.randomUUID().toString();
        
        ExecutionStatus execution = new ExecutionStatus(
            executionId, 
            agentName, 
            ExecutionState.RUNNING, 
            LocalDateTime.now(),
            null
        );
        
        executions.put(executionId, execution);
        
        // Initialize all nodes as PENDING
        Map<String, NodeExecutionStatus> nodes = new ConcurrentHashMap<>();
        for (String nodeId : nodeIds) {
            nodes.put(nodeId, new NodeExecutionStatus(
                nodeId, 
                NodeState.PENDING, 
                null, 
                null, 
                null,
                null
            ));
        }
        nodeStatuses.put(executionId, nodes);
        
        logger.info("Started execution tracking for agent: {} with ID: {}", agentName, executionId);
        
        // Broadcast initial status
        broadcastExecutionUpdate(executionId);
        
        return executionId;
    }
    
    /**
     * Update node status during execution.
     */
    public void updateNodeStatus(String executionId, String nodeId, NodeState state, 
                                String result, String error, Long durationMs) {
        Map<String, NodeExecutionStatus> nodes = nodeStatuses.get(executionId);
        if (nodes == null) {
            logger.warn("No execution found for ID: {}", executionId);
            return;
        }
        
        NodeExecutionStatus currentStatus = nodes.get(nodeId);
        if (currentStatus == null) {
            logger.warn("No node found for ID: {} in execution: {}", nodeId, executionId);
            return;
        }
        
        LocalDateTime timestamp = LocalDateTime.now();
        NodeExecutionStatus updatedStatus = new NodeExecutionStatus(
            nodeId,
            state,
            timestamp,
            result,
            error,
            durationMs
        );
        
        nodes.put(nodeId, updatedStatus);
        
        logger.debug("Updated node {} in execution {} to state: {}", nodeId, executionId, state);
        
        // Broadcast status update
        broadcastNodeUpdate(executionId, updatedStatus);
    }
    
    /**
     * Mark execution as completed.
     */
    public void completeExecution(String executionId, boolean success) {
        ExecutionStatus execution = executions.get(executionId);
        if (execution == null) {
            logger.warn("No execution found for ID: {}", executionId);
            return;
        }
        
        ExecutionState finalState = success ? ExecutionState.COMPLETED : ExecutionState.FAILED;
        ExecutionStatus updatedExecution = new ExecutionStatus(
            execution.executionId(),
            execution.agentName(),
            finalState,
            execution.startTime(),
            LocalDateTime.now()
        );
        
        executions.put(executionId, updatedExecution);
        
        logger.info("Completed execution {} for agent {} with state: {}", 
                   executionId, execution.agentName(), finalState);
        
        // Broadcast completion
        broadcastExecutionUpdate(executionId);
    }
    
    /**
     * Get current execution status.
     */
    public Optional<ExecutionStatusData> getExecutionStatus(String executionId) {
        ExecutionStatus execution = executions.get(executionId);
        Map<String, NodeExecutionStatus> nodes = nodeStatuses.get(executionId);
        
        if (execution == null || nodes == null) {
            return Optional.empty();
        }
        
        return Optional.of(new ExecutionStatusData(execution, new ArrayList<>(nodes.values())));
    }
    
    /**
     * Get all active executions.
     */
    public List<ExecutionStatusData> getAllActiveExecutions() {
        return executions.values().stream()
                .filter(exec -> exec.state() == ExecutionState.RUNNING)
                .map(exec -> {
                    Map<String, NodeExecutionStatus> nodes = nodeStatuses.get(exec.executionId());
                    return new ExecutionStatusData(exec, nodes != null ? new ArrayList<>(nodes.values()) : Collections.emptyList());
                })
                .toList();
    }
    
    /**
     * Get execution history.
     */
    public List<ExecutionStatusData> getExecutionHistory(int limit) {
        return executions.values().stream()
                .sorted((a, b) -> b.startTime().compareTo(a.startTime()))
                .limit(limit)
                .map(exec -> {
                    Map<String, NodeExecutionStatus> nodes = nodeStatuses.get(exec.executionId());
                    return new ExecutionStatusData(exec, nodes != null ? new ArrayList<>(nodes.values()) : Collections.emptyList());
                })
                .toList();
    }
    
    /**
     * Broadcast execution update via WebSocket.
     */
    private void broadcastExecutionUpdate(String executionId) {
        if (messagingTemplate != null) {
            try {
                getExecutionStatus(executionId).ifPresent(status -> {
                    messagingTemplate.convertAndSend("/topic/execution/" + executionId, status);
                    messagingTemplate.convertAndSend("/topic/executions", status);
                });
            } catch (Exception e) {
                logger.warn("Failed to broadcast execution update for {}: {}", executionId, e.getMessage());
            }
        }
    }
    
    /**
     * Broadcast node update via WebSocket.
     */
    private void broadcastNodeUpdate(String executionId, NodeExecutionStatus nodeStatus) {
        if (messagingTemplate != null) {
            try {
                messagingTemplate.convertAndSend("/topic/execution/" + executionId + "/node/" + nodeStatus.nodeId(), nodeStatus);
            } catch (Exception e) {
                logger.warn("Failed to broadcast node update for {} in execution {}: {}", 
                           nodeStatus.nodeId(), executionId, e.getMessage());
            }
        }
    }
    
    /**
     * Execution state enum.
     */
    public enum ExecutionState {
        RUNNING, COMPLETED, FAILED
    }
    
    /**
     * Node execution state enum.
     */
    public enum NodeState {
        PENDING, RUNNING, COMPLETED, FAILED, SKIPPED
    }

    /**
         * Overall execution status.
         */
        public record ExecutionStatus(String executionId, String agentName, ExecutionState state, LocalDateTime startTime,
                                      LocalDateTime endTime) {
    }

    /**
         * Individual node execution status.
         */
        public record NodeExecutionStatus(String nodeId, NodeState state, LocalDateTime timestamp, String result,
                                          String error, Long durationMs) {
    }

    /**
         * Combined execution and node status data.
         */
        public record ExecutionStatusData(ExecutionStatus execution, List<NodeExecutionStatus> nodes) {
    }
}