package com.springai.agent.service;

import com.springai.agent.service.ExecutionStatusService.ExecutionState;
import com.springai.agent.service.ExecutionStatusService.NodeState;
import com.springai.agent.service.ExecutionStatusService.ExecutionStatus;
import com.springai.agent.service.ExecutionStatusService.NodeExecutionStatus;
import com.springai.agent.service.ExecutionStatusService.ExecutionStatusData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ExecutionStatusService.
 * Tests execution tracking, status updates, and WebSocket broadcasting.
 */
class ExecutionStatusServiceTest {

    private ExecutionStatusService service;
    
    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new ExecutionStatusService();
        service.setMessagingTemplate(messagingTemplate);
    }

    @Test
    @DisplayName("Should start execution tracking and return execution ID")
    void testStartExecution() {
        // Given
        String agentName = "test-agent";
        List<String> nodeIds = Arrays.asList("node1", "node2", "node3");

        // When
        String executionId = service.startExecution(agentName, nodeIds);

        // Then
        assertNotNull(executionId);
        assertFalse(executionId.isEmpty());
        
        // Verify execution status
        Optional<ExecutionStatusData> statusData = service.getExecutionStatus(executionId);
        assertTrue(statusData.isPresent());
        
        ExecutionStatus execution = statusData.get().execution();
        assertEquals(executionId, execution.executionId());
        assertEquals(agentName, execution.agentName());
        assertEquals(ExecutionState.RUNNING, execution.state());
        assertNotNull(execution.startTime());
        assertNull(execution.endTime());
        
        // Verify all nodes are initialized as PENDING
        List<NodeExecutionStatus> nodes = statusData.get().nodes();
        assertEquals(3, nodes.size());
        
        for (NodeExecutionStatus node : nodes) {
            assertEquals(NodeState.PENDING, node.state());
            assertTrue(nodeIds.contains(node.nodeId()));
            assertNull(node.result());
            assertNull(node.error());
            assertNull(node.durationMs());
        }
        
        // Verify WebSocket broadcast - should broadcast to both specific execution topic and general executions topic
        verify(messagingTemplate, times(1)).convertAndSend(eq("/topic/execution/" + executionId), any(ExecutionStatusData.class));
        verify(messagingTemplate, times(1)).convertAndSend(eq("/topic/executions"), any(ExecutionStatusData.class));
    }

    @Test
    @DisplayName("Should update node status correctly")
    void testUpdateNodeStatus() {
        // Given
        String agentName = "test-agent";
        List<String> nodeIds = Arrays.asList("node1", "node2");
        String executionId = service.startExecution(agentName, nodeIds);
        
        // When
        service.updateNodeStatus(executionId, "node1", NodeState.RUNNING, null, null, null);
        
        // Then
        Optional<ExecutionStatusData> statusData = service.getExecutionStatus(executionId);
        assertTrue(statusData.isPresent());
        
        NodeExecutionStatus node1 = findNodeById(statusData.get().nodes(), "node1");
        assertNotNull(node1);
        assertEquals(NodeState.RUNNING, node1.state());
        assertNotNull(node1.timestamp());
        
        NodeExecutionStatus node2 = findNodeById(statusData.get().nodes(), "node2");
        assertNotNull(node2);
        assertEquals(NodeState.PENDING, node2.state()); // Should remain unchanged
        
        // Verify WebSocket broadcast
        verify(messagingTemplate, atLeast(3)).convertAndSend(anyString(), any(Object.class));
    }

    @Test
    @DisplayName("Should update node status with result and duration")
    void testUpdateNodeStatusWithResult() {
        // Given
        String agentName = "test-agent";
        List<String> nodeIds = Arrays.asList("node1");
        String executionId = service.startExecution(agentName, nodeIds);
        
        String result = "Node execution result";
        Long duration = 1500L;
        
        // When
        service.updateNodeStatus(executionId, "node1", NodeState.COMPLETED, result, null, duration);
        
        // Then
        Optional<ExecutionStatusData> statusData = service.getExecutionStatus(executionId);
        assertTrue(statusData.isPresent());
        
        NodeExecutionStatus node = findNodeById(statusData.get().nodes(), "node1");
        assertNotNull(node);
        assertEquals(NodeState.COMPLETED, node.state());
        assertEquals(result, node.result());
        assertNull(node.error());
        assertEquals(duration, node.durationMs());
        assertNotNull(node.timestamp());
    }

    @Test
    @DisplayName("Should update node status with error")
    void testUpdateNodeStatusWithError() {
        // Given
        String agentName = "test-agent";
        List<String> nodeIds = Arrays.asList("node1");
        String executionId = service.startExecution(agentName, nodeIds);
        
        String error = "Node execution failed";
        Long duration = 500L;
        
        // When
        service.updateNodeStatus(executionId, "node1", NodeState.FAILED, null, error, duration);
        
        // Then
        Optional<ExecutionStatusData> statusData = service.getExecutionStatus(executionId);
        assertTrue(statusData.isPresent());
        
        NodeExecutionStatus node = findNodeById(statusData.get().nodes(), "node1");
        assertNotNull(node);
        assertEquals(NodeState.FAILED, node.state());
        assertNull(node.result());
        assertEquals(error, node.error());
        assertEquals(duration, node.durationMs());
        assertNotNull(node.timestamp());
    }

    @Test
    @DisplayName("Should handle update for non-existent execution")
    void testUpdateNodeStatusNonExistentExecution() {
        // Given
        String nonExistentExecutionId = "non-existent-id";
        
        // When/Then - Should not throw exception
        assertDoesNotThrow(() -> {
            service.updateNodeStatus(nonExistentExecutionId, "node1", NodeState.RUNNING, null, null, null);
        });
        
        // Verify no WebSocket broadcast for non-existent execution
        verify(messagingTemplate, never()).convertAndSend(anyString(), any(Object.class));
    }

    @Test
    @DisplayName("Should handle update for non-existent node")
    void testUpdateNodeStatusNonExistentNode() {
        // Given
        String agentName = "test-agent";
        List<String> nodeIds = Arrays.asList("node1");
        String executionId = service.startExecution(agentName, nodeIds);
        
        // When/Then - Should not throw exception
        assertDoesNotThrow(() -> {
            service.updateNodeStatus(executionId, "non-existent-node", NodeState.RUNNING, null, null, null);
        });
    }

    @Test
    @DisplayName("Should complete execution successfully")
    void testCompleteExecutionSuccess() {
        // Given
        String agentName = "test-agent";
        List<String> nodeIds = Arrays.asList("node1");
        String executionId = service.startExecution(agentName, nodeIds);
        
        // When
        service.completeExecution(executionId, true);
        
        // Then
        Optional<ExecutionStatusData> statusData = service.getExecutionStatus(executionId);
        assertTrue(statusData.isPresent());
        
        ExecutionStatus execution = statusData.get().execution();
        assertEquals(ExecutionState.COMPLETED, execution.state());
        assertNotNull(execution.endTime());
        assertTrue(execution.endTime().isAfter(execution.startTime()) || execution.endTime().isEqual(execution.startTime()));
        
        // Verify WebSocket broadcast
        verify(messagingTemplate, atLeast(3)).convertAndSend(anyString(), any(Object.class));
    }

    @Test
    @DisplayName("Should complete execution with failure")
    void testCompleteExecutionFailure() {
        // Given
        String agentName = "test-agent";
        List<String> nodeIds = Arrays.asList("node1");
        String executionId = service.startExecution(agentName, nodeIds);
        
        // When
        service.completeExecution(executionId, false);
        
        // Then
        Optional<ExecutionStatusData> statusData = service.getExecutionStatus(executionId);
        assertTrue(statusData.isPresent());
        
        ExecutionStatus execution = statusData.get().execution();
        assertEquals(ExecutionState.FAILED, execution.state());
        assertNotNull(execution.endTime());
    }

    @Test
    @DisplayName("Should handle complete for non-existent execution")
    void testCompleteExecutionNonExistent() {
        // Given
        String nonExistentExecutionId = "non-existent-id";
        
        // When/Then - Should not throw exception
        assertDoesNotThrow(() -> {
            service.completeExecution(nonExistentExecutionId, true);
        });
    }

    @Test
    @DisplayName("Should return empty for non-existent execution status")
    void testGetExecutionStatusNonExistent() {
        // Given
        String nonExistentExecutionId = "non-existent-id";
        
        // When
        Optional<ExecutionStatusData> result = service.getExecutionStatus(nonExistentExecutionId);
        
        // Then
        assertFalse(result.isPresent());
    }

    @Test
    @DisplayName("Should return all active executions")
    void testGetAllActiveExecutions() {
        // Given
        String executionId1 = service.startExecution("agent1", Arrays.asList("node1"));
        String executionId2 = service.startExecution("agent2", Arrays.asList("node2"));
        String executionId3 = service.startExecution("agent3", Arrays.asList("node3"));
        
        // Complete one execution
        service.completeExecution(executionId2, true);
        
        // When
        List<ExecutionStatusData> activeExecutions = service.getAllActiveExecutions();
        
        // Then
        assertEquals(2, activeExecutions.size());
        
        List<String> activeIds = activeExecutions.stream()
                .map(data -> data.execution().executionId())
                .toList();
        
        assertTrue(activeIds.contains(executionId1));
        assertFalse(activeIds.contains(executionId2)); // Should be excluded (completed)
        assertTrue(activeIds.contains(executionId3));
    }

    @Test
    @DisplayName("Should return execution history with limit")
    void testGetExecutionHistory() {
        // Given
        String executionId1 = service.startExecution("agent1", Arrays.asList("node1"));
        String executionId2 = service.startExecution("agent2", Arrays.asList("node2"));
        String executionId3 = service.startExecution("agent3", Arrays.asList("node3"));
        
        // When
        List<ExecutionStatusData> history = service.getExecutionHistory(2);
        
        // Then
        assertEquals(2, history.size());
        
        // Should be sorted by start time (most recent first)
        LocalDateTime firstStartTime = history.get(0).execution().startTime();
        LocalDateTime secondStartTime = history.get(1).execution().startTime();
        assertTrue(firstStartTime.isAfter(secondStartTime) || firstStartTime.isEqual(secondStartTime));
    }

    @Test
    @DisplayName("Should return empty history when no executions")
    void testGetExecutionHistoryEmpty() {
        // When
        List<ExecutionStatusData> history = service.getExecutionHistory(10);
        
        // Then
        assertTrue(history.isEmpty());
    }

    @Test
    @DisplayName("Should handle multiple node state transitions")
    void testMultipleNodeStateTransitions() {
        // Given
        String agentName = "test-agent";
        List<String> nodeIds = Arrays.asList("node1", "node2", "node3");
        String executionId = service.startExecution(agentName, nodeIds);
        
        // When - Simulate execution flow
        service.updateNodeStatus(executionId, "node1", NodeState.RUNNING, null, null, null);
        service.updateNodeStatus(executionId, "node1", NodeState.COMPLETED, "Result 1", null, 1000L);
        
        service.updateNodeStatus(executionId, "node2", NodeState.RUNNING, null, null, null);
        service.updateNodeStatus(executionId, "node3", NodeState.RUNNING, null, null, null);
        
        service.updateNodeStatus(executionId, "node2", NodeState.COMPLETED, "Result 2", null, 1500L);
        service.updateNodeStatus(executionId, "node3", NodeState.FAILED, null, "Error in node3", 800L);
        
        service.completeExecution(executionId, false); // Failed due to node3
        
        // Then
        Optional<ExecutionStatusData> statusData = service.getExecutionStatus(executionId);
        assertTrue(statusData.isPresent());
        
        ExecutionStatus execution = statusData.get().execution();
        assertEquals(ExecutionState.FAILED, execution.state());
        
        List<NodeExecutionStatus> nodes = statusData.get().nodes();
        
        NodeExecutionStatus node1 = findNodeById(nodes, "node1");
        assertEquals(NodeState.COMPLETED, node1.state());
        assertEquals("Result 1", node1.result());
        assertEquals(1000L, node1.durationMs());
        
        NodeExecutionStatus node2 = findNodeById(nodes, "node2");
        assertEquals(NodeState.COMPLETED, node2.state());
        assertEquals("Result 2", node2.result());
        assertEquals(1500L, node2.durationMs());
        
        NodeExecutionStatus node3 = findNodeById(nodes, "node3");
        assertEquals(NodeState.FAILED, node3.state());
        assertEquals("Error in node3", node3.error());
        assertEquals(800L, node3.durationMs());
    }

    @Test
    @DisplayName("Should work without messaging template")
    void testWithoutMessagingTemplate() {
        // Given
        ExecutionStatusService serviceWithoutMessaging = new ExecutionStatusService();
        // Don't set messaging template
        
        // When/Then - Should not throw exceptions
        assertDoesNotThrow(() -> {
            String executionId = serviceWithoutMessaging.startExecution("agent", Arrays.asList("node1"));
            serviceWithoutMessaging.updateNodeStatus(executionId, "node1", NodeState.RUNNING, null, null, null);
            serviceWithoutMessaging.completeExecution(executionId, true);
        });
    }

    @Test
    @DisplayName("Should handle concurrent access safely")
    void testConcurrentAccess() {
        // Given
        String agentName = "test-agent";
        List<String> nodeIds = Arrays.asList("node1", "node2");
        String executionId = service.startExecution(agentName, nodeIds);
        
        // When - Simulate concurrent updates
        Thread thread1 = new Thread(() -> {
            service.updateNodeStatus(executionId, "node1", NodeState.RUNNING, null, null, null);
            service.updateNodeStatus(executionId, "node1", NodeState.COMPLETED, "Result 1", null, 1000L);
        });
        
        Thread thread2 = new Thread(() -> {
            service.updateNodeStatus(executionId, "node2", NodeState.RUNNING, null, null, null);
            service.updateNodeStatus(executionId, "node2", NodeState.COMPLETED, "Result 2", null, 1500L);
        });
        
        // Then - Should not throw exceptions
        assertDoesNotThrow(() -> {
            thread1.start();
            thread2.start();
            thread1.join();
            thread2.join();
        });
        
        // Verify final state
        Optional<ExecutionStatusData> statusData = service.getExecutionStatus(executionId);
        assertTrue(statusData.isPresent());
        assertEquals(2, statusData.get().nodes().size());
    }

    // Helper methods
    private NodeExecutionStatus findNodeById(List<NodeExecutionStatus> nodes, String nodeId) {
        return nodes.stream()
                .filter(node -> nodeId.equals(node.nodeId()))
                .findFirst()
                .orElse(null);
    }
}
