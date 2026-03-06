package com.springai.agents.visualization.service;

import com.springai.agents.visualization.dto.*;
import com.springai.agents.visualization.model.ExecutionStatus;
import com.springai.agents.visualization.model.NodeStatus;
import com.springai.agents.workflow.event.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("ExecutionTrackingService")
class ExecutionTrackingServiceTest {

    private ExecutionHistoryService historyService;
    private SimpMessagingTemplate messagingTemplate;
    private ExecutionTrackingService trackingService;

    @BeforeEach
    void setUp() {
        historyService = new ExecutionHistoryService(100);
        messagingTemplate = mock(SimpMessagingTemplate.class);
        trackingService = new ExecutionTrackingService(historyService, messagingTemplate);
    }

    // ── WorkflowStarted ─────────────────────────────────────────────────

    @Nested
    @DisplayName("onWorkflowStarted")
    class OnWorkflowStarted {

        @Test
        @DisplayName("updates record with workflow name and sends WebSocket event")
        void updatesRecordAndSendsEvent() {
            ExecutionRecordDto record = saveRunningRecord("exec-1", "my-agent");

            Map<String, Object> source = Map.of("executionId", "exec-1", "agentName", "my-agent");
            WorkflowStartedEvent event = new WorkflowStartedEvent(source, "main-workflow", "hello", 3);

            trackingService.onWorkflowStarted(event);

            assertEquals("main-workflow", record.getWorkflowName());

            // Should send to both /topic/executions/all and /topic/executions/my-agent
            verify(messagingTemplate).convertAndSend(eq("/topic/executions/all"), any(ExecutionEventDto.class));
            verify(messagingTemplate).convertAndSend(eq("/topic/executions/my-agent"), any(ExecutionEventDto.class));
        }

        @Test
        @DisplayName("ignores events without executionId in source")
        void ignoresWithoutExecutionId() {
            Map<String, Object> source = Map.of("agentName", "my-agent");
            WorkflowStartedEvent event = new WorkflowStartedEvent(source, "wf", "input", 2);

            trackingService.onWorkflowStarted(event);

            verifyNoInteractions(messagingTemplate);
        }

        @Test
        @DisplayName("handles non-map source gracefully")
        void handlesNonMapSource() {
            WorkflowStartedEvent event = new WorkflowStartedEvent("not-a-map", "wf", "input", 2);

            assertDoesNotThrow(() -> trackingService.onWorkflowStarted(event));
            verifyNoInteractions(messagingTemplate);
        }
    }

    // ── NodeStarted ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("onNodeStarted")
    class OnNodeStarted {

        @Test
        @DisplayName("adds node status to execution record")
        void addsNodeStatus() {
            ExecutionRecordDto record = saveRunningRecord("exec-1", "my-agent");

            Map<String, Object> source = Map.of("executionId", "exec-1", "agentName", "my-agent");
            NodeStartedEvent event = new NodeStartedEvent(source, "main", "hello", "process", "LlmNode");

            trackingService.onNodeStarted(event);

            NodeStatusDto nodeStatus = record.getNodeStatuses().get("process");
            assertNotNull(nodeStatus);
            assertEquals("process", nodeStatus.getNodeId());
            assertEquals("LlmNode", nodeStatus.getNodeType());
            assertEquals(NodeStatus.RUNNING, nodeStatus.getStatus());

            verify(messagingTemplate, times(2)).convertAndSend(anyString(), any(ExecutionEventDto.class));
        }

        @Test
        @DisplayName("ignores when execution record not found")
        void ignoresUnknownRecord() {
            Map<String, Object> source = Map.of("executionId", "unknown", "agentName", "agent");
            NodeStartedEvent event = new NodeStartedEvent(source, "main", "hello", "node1", "InputNode");

            assertDoesNotThrow(() -> trackingService.onNodeStarted(event));
            // Still sends WebSocket event even if record not found
            verify(messagingTemplate, times(2)).convertAndSend(anyString(), any(ExecutionEventDto.class));
        }
    }

    // ── NodeCompleted ───────────────────────────────────────────────────

    @Nested
    @DisplayName("onNodeCompleted")
    class OnNodeCompleted {

        @Test
        @DisplayName("updates node status to COMPLETED with duration")
        void updatesNodeStatus() {
            ExecutionRecordDto record = saveRunningRecord("exec-1", "my-agent");
            // Simulate node started first
            NodeStatusDto nodeStatus = NodeStatusDto.builder()
                    .nodeId("process")
                    .nodeType("LlmNode")
                    .status(NodeStatus.RUNNING)
                    .startedAt(System.currentTimeMillis())
                    .build();
            record.getNodeStatuses().put("process", nodeStatus);

            Map<String, Object> source = Map.of("executionId", "exec-1", "agentName", "my-agent");
            NodeCompletedEvent event = new NodeCompletedEvent(source, "main", "hello", "process", "LlmNode", 250);

            trackingService.onNodeCompleted(event);

            assertEquals(NodeStatus.COMPLETED, nodeStatus.getStatus());
            assertEquals(250, nodeStatus.getDurationMs());
        }

        @Test
        @DisplayName("sends event with correct type and duration")
        void sendsCorrectEvent() {
            saveRunningRecord("exec-1", "my-agent");

            Map<String, Object> source = Map.of("executionId", "exec-1", "agentName", "my-agent");
            NodeCompletedEvent event = new NodeCompletedEvent(source, "main", "hello", "process", "LlmNode", 250);

            trackingService.onNodeCompleted(event);

            ArgumentCaptor<ExecutionEventDto> captor = ArgumentCaptor.forClass(ExecutionEventDto.class);
            verify(messagingTemplate).convertAndSend(eq("/topic/executions/all"), captor.capture());

            ExecutionEventDto sentEvent = captor.getValue();
            assertEquals("NODE_COMPLETED", sentEvent.getEventType());
            assertEquals("process", sentEvent.getNodeId());
            assertEquals(250, sentEvent.getDurationMs());
            assertEquals("COMPLETED", sentEvent.getStatus());
        }
    }

    // ── WorkflowCompleted ───────────────────────────────────────────────

    @Nested
    @DisplayName("onWorkflowCompleted")
    class OnWorkflowCompleted {

        @Test
        @DisplayName("updates record to COMPLETED with output and duration")
        void updatesRecordToCompleted() {
            ExecutionRecordDto record = saveRunningRecord("exec-1", "my-agent");

            Map<String, Object> source = Map.of("executionId", "exec-1", "agentName", "my-agent");
            WorkflowCompletedEvent event = new WorkflowCompletedEvent(source, "main", "hello", 500, "Final output");

            trackingService.onWorkflowCompleted(event);

            assertEquals(ExecutionStatus.COMPLETED, record.getStatus());
            assertEquals("Final output", record.getOutput());
            assertEquals(500, record.getDurationMs());
            assertTrue(record.getCompletedAt() > 0);
        }

        @Test
        @DisplayName("sends event with truncated output")
        void truncatesLongOutput() {
            saveRunningRecord("exec-1", "my-agent");

            String longOutput = "x".repeat(1000);
            Map<String, Object> source = Map.of("executionId", "exec-1", "agentName", "my-agent");
            WorkflowCompletedEvent event = new WorkflowCompletedEvent(source, "main", "hello", 500, longOutput);

            trackingService.onWorkflowCompleted(event);

            ArgumentCaptor<ExecutionEventDto> captor = ArgumentCaptor.forClass(ExecutionEventDto.class);
            verify(messagingTemplate).convertAndSend(eq("/topic/executions/all"), captor.capture());

            ExecutionEventDto sentEvent = captor.getValue();
            assertTrue(sentEvent.getOutput().length() <= 503); // 500 + "..."
            assertTrue(sentEvent.getOutput().endsWith("..."));
        }

        @Test
        @DisplayName("persists updated record via historyService")
        void persistsUpdate() {
            ExecutionRecordDto record = saveRunningRecord("exec-1", "my-agent");

            Map<String, Object> source = Map.of("executionId", "exec-1", "agentName", "my-agent");
            WorkflowCompletedEvent event = new WorkflowCompletedEvent(source, "main", "hello", 200, "done");

            trackingService.onWorkflowCompleted(event);

            ExecutionRecordDto persisted = historyService.getById("exec-1");
            assertNotNull(persisted);
            assertEquals(ExecutionStatus.COMPLETED, persisted.getStatus());
            assertEquals("done", persisted.getOutput());
        }
    }

    // ── WebSocket Error Handling ────────────────────────────────────────

    @Nested
    @DisplayName("WebSocket error handling")
    class WebSocketErrors {

        @Test
        @DisplayName("does not throw when WebSocket send fails")
        void doesNotThrowOnSendFailure() {
            saveRunningRecord("exec-1", "my-agent");

            doThrow(new RuntimeException("WebSocket down"))
                    .when(messagingTemplate).convertAndSend(anyString(), any(ExecutionEventDto.class));

            Map<String, Object> source = Map.of("executionId", "exec-1", "agentName", "my-agent");
            WorkflowStartedEvent event = new WorkflowStartedEvent(source, "main", "hello", 2);

            assertDoesNotThrow(() -> trackingService.onWorkflowStarted(event));
        }
    }

    // ── Helpers ─────────────────────────────────────────────────────────

    private ExecutionRecordDto saveRunningRecord(String executionId, String agentName) {
        ExecutionRecordDto record = ExecutionRecordDto.builder()
                .id(executionId)
                .agentName(agentName)
                .input("test input")
                .status(ExecutionStatus.RUNNING)
                .startedAt(System.currentTimeMillis())
                .build();
        historyService.save(record);
        return record;
    }
}

