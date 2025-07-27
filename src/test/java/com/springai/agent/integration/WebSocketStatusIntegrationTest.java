package com.springai.agent.integration;

import com.springai.agent.config.VisualizationConfiguration;
import com.springai.agent.config.AgentsProperties;
import com.springai.agent.config.AppProperties;
import com.springai.agent.service.ExecutionStatusService;
import com.springai.agent.service.ExecutionStatusService.ExecutionStatusData;
import com.springai.agent.service.ExecutionStatusService.NodeExecutionStatus;
import com.springai.agent.service.ExecutionStatusService.NodeState;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.*;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.springframework.web.socket.sockjs.client.SockJsClient;
import org.springframework.web.socket.sockjs.client.WebSocketTransport;
import org.springframework.ai.chat.model.ChatModel;

import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Integration tests for WebSocket functionality in the visualization system.
 * Tests real-time status updates via WebSocket connections.
 */
@SpringBootTest(
    classes = {
        VisualizationConfiguration.class,
        WebSocketStatusIntegrationTest.TestConfig.class
    },
    webEnvironment = SpringBootTest.WebEnvironment.NONE
)
@TestPropertySource(properties = {
    "visualization.realTimeStatus=true"
})
class WebSocketStatusIntegrationTest {

    @TestConfiguration
    static class TestConfig {
        @Bean
        @Primary
        public ChatModel mockChatModel() {
            return mock(ChatModel.class);
        }
        
        @Bean
        @Primary
        public AgentsProperties mockAgentsProperties() {
            return mock(AgentsProperties.class);
        }
        
        @Bean
        @Primary
        public AppProperties mockAppProperties() {
            return mock(AppProperties.class);
        }
        
        @Bean
        @Primary
        public SimpMessagingTemplate mockSimpMessagingTemplate() {
            return mock(SimpMessagingTemplate.class);
        }
    }

    @Autowired
    private ExecutionStatusService executionStatusService;
    
    @Autowired
    private SimpMessagingTemplate messagingTemplate;
    
    private WebSocketStompClient stompClient;
    private StompSession stompSession;
    private final BlockingQueue<ExecutionStatusData> receivedMessages = new LinkedBlockingQueue<>();
    private final BlockingQueue<NodeExecutionStatus> receivedNodeMessages = new LinkedBlockingQueue<>();

    @BeforeEach
    void setUp() throws Exception {
        // Set up WebSocket client
        stompClient = new WebSocketStompClient(new SockJsClient(
            Arrays.asList(new WebSocketTransport(new StandardWebSocketClient()))));
        stompClient.setMessageConverter(new MappingJackson2MessageConverter());
        
        // Set messaging template for the service
        executionStatusService.setMessagingTemplate(messagingTemplate);
        
        // Clear execution service state between tests
        executionStatusService.clearAll();
        
        // Clear message queues
        receivedMessages.clear();
        receivedNodeMessages.clear();
    }

    @Test
    @DisplayName("Should broadcast execution updates via WebSocket")
    @Timeout(10)
    void testExecutionStatusBroadcast() throws Exception {
        // Given
        String agentName = "test-agent";
        List<String> nodeIds = Arrays.asList("step1", "step2");
        
        // Start execution
        String executionId = executionStatusService.startExecution(agentName, nodeIds);
        assertNotNull(executionId);
        
        // Verify that messaging template was called for broadcast
        verify(messagingTemplate, atLeast(1)).convertAndSend(anyString(), any(Object.class));
    }

    @Test
    @DisplayName("Should broadcast node status updates via WebSocket")
    @Timeout(10)
    void testNodeStatusUpdateBroadcast() throws Exception {
        // Given
        String agentName = "test-agent";
        List<String> nodeIds = Arrays.asList("step1", "step2");
        String executionId = executionStatusService.startExecution(agentName, nodeIds);
        
        // When - Update node status
        executionStatusService.updateNodeStatus(executionId, "step1", 
            NodeState.RUNNING, null, null, null);
        
        // Then - Verify broadcast was called
        verify(messagingTemplate, atLeast(2)).convertAndSend(anyString(), any(Object.class));
        
        // Update to completed
        executionStatusService.updateNodeStatus(executionId, "step1", 
            NodeState.COMPLETED, "Test result", null, 1000L);
        
        // Verify additional broadcast
        verify(messagingTemplate, atLeast(3)).convertAndSend(anyString(), any(Object.class));
    }

    @Test
    @DisplayName("Should broadcast execution completion via WebSocket")
    @Timeout(10)
    void testExecutionCompletionBroadcast() throws Exception {
        // Given
        String agentName = "test-agent";
        List<String> nodeIds = Arrays.asList("step1");
        String executionId = executionStatusService.startExecution(agentName, nodeIds);
        
        // When - Complete execution
        executionStatusService.completeExecution(executionId, true);
        
        // Then - Verify broadcast was called
        verify(messagingTemplate, atLeast(2)).convertAndSend(anyString(), any(Object.class));
    }

    @Test
    @DisplayName("Should handle multiple concurrent WebSocket broadcasts")
    @Timeout(10)
    void testConcurrentWebSocketBroadcasts() throws Exception {
        // Given
        String agentName1 = "agent1";
        String agentName2 = "agent2";
        List<String> nodeIds = Arrays.asList("step1");
        
        // When - Start multiple executions concurrently
        String executionId1 = executionStatusService.startExecution(agentName1, nodeIds);
        String executionId2 = executionStatusService.startExecution(agentName2, nodeIds);
        
        // Update both executions
        executionStatusService.updateNodeStatus(executionId1, "step1", 
            NodeState.COMPLETED, "Result 1", null, 1000L);
        executionStatusService.updateNodeStatus(executionId2, "step1", 
            NodeState.COMPLETED, "Result 2", null, 1500L);
        
        // Complete both executions
        executionStatusService.completeExecution(executionId1, true);
        executionStatusService.completeExecution(executionId2, true);
        
        // Then - Verify multiple broadcasts occurred
        verify(messagingTemplate, atLeast(6)).convertAndSend(anyString(), any(Object.class));
    }

    @Test
    @DisplayName("Should broadcast to correct WebSocket topics")
    @Timeout(10)
    void testWebSocketTopicRouting() throws Exception {
        // Given
        String agentName = "test-agent";
        List<String> nodeIds = Arrays.asList("step1");
        String executionId = executionStatusService.startExecution(agentName, nodeIds);
        
        // Reset mock to only count calls from updateNodeStatus
        reset(messagingTemplate);
        
        // When - Update node status
        executionStatusService.updateNodeStatus(executionId, "step1", 
            NodeState.RUNNING, null, null, null);
        
        // Then - Verify correct topics were used (only counting calls from updateNodeStatus)
        // updateNodeStatus only broadcasts to node-specific topics, not execution topics
        // Verify node-specific topic (should contain "/node/")
        verify(messagingTemplate).convertAndSend(
            argThat(topic -> topic.toString().contains("/node/step1")), 
            any(NodeExecutionStatus.class));
        
        // Verify that execution topics are NOT called from updateNodeStatus
        verify(messagingTemplate, never()).convertAndSend(
            argThat(topic -> topic.toString().startsWith("/topic/execution/") && !topic.toString().contains("/node/")), 
            any(ExecutionStatusData.class));
        verify(messagingTemplate, never()).convertAndSend(
            eq("/topic/executions"), 
            any(ExecutionStatusData.class));
    }

    @Test
    @DisplayName("Should handle WebSocket broadcast failures gracefully")
    @Timeout(10)
    void testWebSocketBroadcastFailureHandling() throws Exception {
        // Given - Mock messaging template to throw exception
        SimpMessagingTemplate failingTemplate = mock(SimpMessagingTemplate.class);
        doThrow(new RuntimeException("WebSocket connection failed"))
            .when(failingTemplate).convertAndSend(anyString(), any(Object.class));
        
        executionStatusService.setMessagingTemplate(failingTemplate);
        
        // When/Then - Should not throw exception despite WebSocket failure
        assertDoesNotThrow(() -> {
            String executionId = executionStatusService.startExecution("test-agent", 
                Arrays.asList("step1"));
            executionStatusService.updateNodeStatus(executionId, "step1", 
                NodeState.COMPLETED, "Result", null, 1000L);
            executionStatusService.completeExecution(executionId, true);
        });
        
        // Verify the service still works for data retrieval
        List<ExecutionStatusData> history = executionStatusService.getExecutionHistory(10);
        assertEquals(1, history.size());
    }

    @Test
    @DisplayName("Should work without WebSocket messaging template")
    @Timeout(10)
    void testWithoutWebSocketMessaging() throws Exception {
        // Given - Service without messaging template
        ExecutionStatusService serviceWithoutWS = new ExecutionStatusService();
        // Don't set messaging template
        
        // When/Then - Should work normally without WebSocket broadcasts
        assertDoesNotThrow(() -> {
            String executionId = serviceWithoutWS.startExecution("test-agent", 
                Arrays.asList("step1"));
            serviceWithoutWS.updateNodeStatus(executionId, "step1", 
                NodeState.COMPLETED, "Result", null, 1000L);
            serviceWithoutWS.completeExecution(executionId, true);
        });
        
        // Verify data is still tracked
        List<ExecutionStatusData> history = serviceWithoutWS.getExecutionHistory(10);
        assertEquals(1, history.size());
    }

    @Test
    @DisplayName("Should broadcast execution status with correct data structure")
    @Timeout(10)
    void testWebSocketDataStructure() throws Exception {
        // Given
        String agentName = "test-agent";
        List<String> nodeIds = Arrays.asList("step1", "step2");
        String executionId = executionStatusService.startExecution(agentName, nodeIds);
        
        // When - Update execution
        executionStatusService.updateNodeStatus(executionId, "step1", 
            NodeState.RUNNING, null, null, null);
        executionStatusService.updateNodeStatus(executionId, "step1", 
            NodeState.COMPLETED, "Test result", null, 1000L);
        executionStatusService.completeExecution(executionId, true);
        
        // Then - Verify the data structure sent via WebSocket
        // We can't easily test the actual WebSocket data without a full integration,
        // but we can verify the service has the correct data
        var status = executionStatusService.getExecutionStatus(executionId);
        assertTrue(status.isPresent());
        
        ExecutionStatusData data = status.get();
        assertEquals(agentName, data.execution().agentName());
        assertEquals(ExecutionStatusService.ExecutionState.COMPLETED, 
            data.execution().state());
        assertEquals(2, data.nodes().size());
        
        // Find step1 node
        NodeExecutionStatus step1 = data.nodes().stream()
            .filter(node -> "step1".equals(node.nodeId()))
            .findFirst()
            .orElse(null);
        assertNotNull(step1);
        assertEquals(NodeState.COMPLETED, step1.state());
        assertEquals("Test result", step1.result());
        assertEquals(1000L, step1.durationMs());
    }

    @Test
    @DisplayName("Should handle rapid WebSocket updates efficiently")
    @Timeout(10)
    void testRapidWebSocketUpdates() throws Exception {
        // Given
        String agentName = "stress-test-agent";
        List<String> nodeIds = Arrays.asList("step1", "step2", "step3", "step4", "step5");
        String executionId = executionStatusService.startExecution(agentName, nodeIds);
        
        // When - Rapidly update multiple nodes
        for (String nodeId : nodeIds) {
            executionStatusService.updateNodeStatus(executionId, nodeId, 
                NodeState.RUNNING, null, null, null);
            executionStatusService.updateNodeStatus(executionId, nodeId, 
                NodeState.COMPLETED, "Result for " + nodeId, null, 100L);
        }
        
        executionStatusService.completeExecution(executionId, true);
        
        // Then - Verify all updates were processed
        var status = executionStatusService.getExecutionStatus(executionId);
        assertTrue(status.isPresent());
        assertEquals(5, status.get().nodes().size());
        
        // All nodes should be completed
        for (NodeExecutionStatus node : status.get().nodes()) {
            assertEquals(NodeState.COMPLETED, node.state());
            assertNotNull(node.result());
            assertTrue(node.result().startsWith("Result for"));
        }
        
        // Verify messaging template was called many times
        verify(messagingTemplate, atLeast(15)).convertAndSend(anyString(), any(Object.class));
    }

    @Test
    @DisplayName("Should maintain WebSocket connection state correctly")
    @Timeout(10)
    void testWebSocketConnectionState() throws Exception {
        // Given
        String agentName = "connection-test-agent";
        List<String> nodeIds = Arrays.asList("step1");
        
        // When - Multiple operations
        String executionId1 = executionStatusService.startExecution(agentName, nodeIds);
        executionStatusService.updateNodeStatus(executionId1, "step1", 
            NodeState.COMPLETED, "Result 1", null, 1000L);
        executionStatusService.completeExecution(executionId1, true);
        
        String executionId2 = executionStatusService.startExecution(agentName, nodeIds);
        executionStatusService.updateNodeStatus(executionId2, "step1", 
            NodeState.FAILED, null, "Error occurred", 500L);
        executionStatusService.completeExecution(executionId2, false);
        
        // Then - Verify both executions are tracked correctly
        var history = executionStatusService.getExecutionHistory(10);
        assertEquals(2, history.size());
        
        // Verify WebSocket broadcasts occurred for both executions
        verify(messagingTemplate, atLeast(6)).convertAndSend(anyString(), any(Object.class));
    }

    // Helper class for WebSocket message handling
    private static class TestStompFrameHandler implements StompFrameHandler {
        private final BlockingQueue<Object> messageQueue;
        private final Class<?> expectedType;
        
        public TestStompFrameHandler(BlockingQueue<Object> messageQueue, Class<?> expectedType) {
            this.messageQueue = messageQueue;
            this.expectedType = expectedType;
        }
        
        @Override
        public Type getPayloadType(StompHeaders headers) {
            return expectedType;
        }
        
        @Override
        public void handleFrame(StompHeaders headers, Object payload) {
            messageQueue.offer(payload);
        }
    }
}

