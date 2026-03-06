package com.springai.agents.visualization.controller;

import com.springai.agents.agent.AgentRegistry;
import com.springai.agents.agent.AgentRuntime;
import com.springai.agents.visualization.dto.ExecutionRecordDto;
import com.springai.agents.visualization.dto.ExecutionRequestDto;
import com.springai.agents.visualization.model.ExecutionStatus;
import com.springai.agents.visualization.service.ExecutionHistoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("ExecutionApiController")
class ExecutionApiControllerTest {

    private AgentRegistry agentRegistry;
    private ExecutionHistoryService historyService;
    private ExecutionApiController controller;

    @BeforeEach
    void setUp() {
        agentRegistry = mock(AgentRegistry.class);
        historyService = new ExecutionHistoryService(100);
        controller = new ExecutionApiController(agentRegistry, historyService);
    }

    // ── Execute ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("POST /agents-ui/api/agents/{name}/execute")
    class Execute {

        @Test
        @DisplayName("returns 404 when agent not found")
        void returns404WhenNotFound() {
            when(agentRegistry.getSyncAgent("nonexistent")).thenReturn(null);

            ExecutionRequestDto request = ExecutionRequestDto.builder().input("hello").build();
            ResponseEntity<ExecutionRecordDto> response = controller.execute("nonexistent", request);

            assertEquals(404, response.getStatusCode().value());
        }

        @Test
        @DisplayName("returns 200 with RUNNING record when agent found")
        void returns200WithRunningRecord() {
            AgentRuntime runtime = mock(AgentRuntime.class);
            when(agentRegistry.getSyncAgent("my-agent")).thenReturn(runtime);
            // Don't actually invoke (mock returns null) — the async execution will try but we just test the response
            when(runtime.invoke(anyString(), anyMap())).thenReturn("result");

            ExecutionRequestDto request = ExecutionRequestDto.builder().input("hello").build();
            ResponseEntity<ExecutionRecordDto> response = controller.execute("my-agent", request);

            assertEquals(200, response.getStatusCode().value());
            ExecutionRecordDto record = response.getBody();
            assertNotNull(record);
            assertNotNull(record.getId());
            assertEquals("my-agent", record.getAgentName());
            assertEquals("hello", record.getInput());
            assertEquals(ExecutionStatus.RUNNING, record.getStatus());
            assertTrue(record.getStartedAt() > 0);
        }

        @Test
        @DisplayName("saves execution record to history service")
        void savesRecord() {
            AgentRuntime runtime = mock(AgentRuntime.class);
            when(agentRegistry.getSyncAgent("my-agent")).thenReturn(runtime);
            when(runtime.invoke(anyString(), anyMap())).thenReturn("result");

            ExecutionRequestDto request = ExecutionRequestDto.builder().input("hello").build();
            ResponseEntity<ExecutionRecordDto> response = controller.execute("my-agent", request);

            String executionId = response.getBody().getId();
            ExecutionRecordDto saved = historyService.getById(executionId);
            assertNotNull(saved);
            assertEquals("my-agent", saved.getAgentName());
        }
    }

    // ── List Executions ─────────────────────────────────────────────────

    @Nested
    @DisplayName("GET /agents-ui/api/executions")
    class ListExecutions {

        @BeforeEach
        void populate() {
            ExecutionRecordDto r1 = ExecutionRecordDto.builder()
                    .id("exec-1").agentName("agent-a").input("hello")
                    .status(ExecutionStatus.COMPLETED).startedAt(1000).build();
            ExecutionRecordDto r2 = ExecutionRecordDto.builder()
                    .id("exec-2").agentName("agent-b").input("world")
                    .status(ExecutionStatus.RUNNING).startedAt(2000).build();
            historyService.save(r1);
            historyService.save(r2);
        }

        @Test
        @DisplayName("returns all executions without filters")
        void returnsAll() {
            List<ExecutionRecordDto> results = controller.listExecutions(null, null, null, 0, 50);
            assertEquals(2, results.size());
        }

        @Test
        @DisplayName("filters by agent name")
        void filtersByAgent() {
            List<ExecutionRecordDto> results = controller.listExecutions("agent-a", null, null, 0, 50);
            assertEquals(1, results.size());
            assertEquals("agent-a", results.get(0).getAgentName());
        }

        @Test
        @DisplayName("filters by status string")
        void filtersByStatus() {
            List<ExecutionRecordDto> results = controller.listExecutions(null, "COMPLETED", null, 0, 50);
            assertEquals(1, results.size());
            assertEquals(ExecutionStatus.COMPLETED, results.get(0).getStatus());
        }

        @Test
        @DisplayName("ignores invalid status string gracefully")
        void ignoresInvalidStatus() {
            List<ExecutionRecordDto> results = controller.listExecutions(null, "INVALID_STATUS", null, 0, 50);
            assertEquals(2, results.size()); // treats as no filter
        }

        @Test
        @DisplayName("supports pagination")
        void pagination() {
            List<ExecutionRecordDto> page0 = controller.listExecutions(null, null, null, 0, 1);
            assertEquals(1, page0.size());

            List<ExecutionRecordDto> page1 = controller.listExecutions(null, null, null, 1, 1);
            assertEquals(1, page1.size());
        }
    }

    // ── Get Single Execution ────────────────────────────────────────────

    @Nested
    @DisplayName("GET /agents-ui/api/executions/{id}")
    class GetExecution {

        @Test
        @DisplayName("returns 200 when execution found")
        void returns200WhenFound() {
            ExecutionRecordDto record = ExecutionRecordDto.builder()
                    .id("exec-1").agentName("a").input("x")
                    .status(ExecutionStatus.COMPLETED).startedAt(1000).build();
            historyService.save(record);

            ResponseEntity<ExecutionRecordDto> response = controller.getExecution("exec-1");

            assertEquals(200, response.getStatusCode().value());
            assertEquals("exec-1", response.getBody().getId());
        }

        @Test
        @DisplayName("returns 404 when execution not found")
        void returns404WhenNotFound() {
            ResponseEntity<ExecutionRecordDto> response = controller.getExecution("nonexistent");
            assertEquals(404, response.getStatusCode().value());
        }
    }

    // ── Clear History ───────────────────────────────────────────────────

    @Nested
    @DisplayName("DELETE /agents-ui/api/executions")
    class ClearHistory {

        @Test
        @DisplayName("clears all history and returns 204")
        void clearsAndReturns204() {
            historyService.save(ExecutionRecordDto.builder()
                    .id("exec-1").agentName("a").input("x")
                    .status(ExecutionStatus.RUNNING).startedAt(1000).build());
            assertEquals(1, historyService.size());

            ResponseEntity<Void> response = controller.clearHistory();

            assertEquals(204, response.getStatusCode().value());
            assertEquals(0, historyService.size());
        }
    }
}

