package com.springai.agents.visualization.controller;

import com.springai.agents.visualization.dto.*;
import com.springai.agents.visualization.service.AgentIntrospectionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("AgentApiController")
class AgentApiControllerTest {

    private AgentIntrospectionService introspectionService;
    private AgentApiController controller;

    @BeforeEach
    void setUp() {
        introspectionService = mock(AgentIntrospectionService.class);
        controller = new AgentApiController(introspectionService);
    }

    // ── listAgents ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("GET /agents-ui/api/agents")
    class ListAgents {

        @Test
        @DisplayName("returns all agent summaries")
        void returnsAll() {
            List<AgentSummaryDto> summaries = List.of(
                    AgentSummaryDto.builder().name("agent-1").description("A").workflowCount(1).build(),
                    AgentSummaryDto.builder().name("agent-2").description("B").workflowCount(2).multiWorkflow(true).build()
            );
            when(introspectionService.getAllAgents()).thenReturn(summaries);

            List<AgentSummaryDto> result = controller.listAgents();

            assertEquals(2, result.size());
            assertEquals("agent-1", result.get(0).getName());
            assertEquals("agent-2", result.get(1).getName());
        }

        @Test
        @DisplayName("returns empty list when no agents")
        void returnsEmpty() {
            when(introspectionService.getAllAgents()).thenReturn(List.of());

            List<AgentSummaryDto> result = controller.listAgents();
            assertTrue(result.isEmpty());
        }
    }

    // ── getAgent ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("GET /agents-ui/api/agents/{name}")
    class GetAgent {

        @Test
        @DisplayName("returns 200 with agent detail when found")
        void returns200WhenFound() {
            AgentDetailDto detail = AgentDetailDto.builder()
                    .name("my-agent")
                    .description("Test agent")
                    .workflowCount(1)
                    .workflows(List.of())
                    .build();
            when(introspectionService.getAgentDetail("my-agent")).thenReturn(detail);

            ResponseEntity<AgentDetailDto> response = controller.getAgent("my-agent");

            assertEquals(200, response.getStatusCode().value());
            assertNotNull(response.getBody());
            assertEquals("my-agent", response.getBody().getName());
        }

        @Test
        @DisplayName("returns 404 when agent not found")
        void returns404WhenNotFound() {
            when(introspectionService.getAgentDetail("nonexistent")).thenReturn(null);

            ResponseEntity<AgentDetailDto> response = controller.getAgent("nonexistent");

            assertEquals(404, response.getStatusCode().value());
            assertNull(response.getBody());
        }
    }

    // ── getWorkflow ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("GET /agents-ui/api/agents/{name}/workflows/{workflow}")
    class GetWorkflow {

        @Test
        @DisplayName("returns 200 with workflow when found")
        void returns200WhenFound() {
            WorkflowDto wf = WorkflowDto.builder()
                    .name("main")
                    .description("Main workflow")
                    .nodes(List.of())
                    .edges(List.of())
                    .build();
            when(introspectionService.getWorkflow("my-agent", "main")).thenReturn(wf);

            ResponseEntity<WorkflowDto> response = controller.getWorkflow("my-agent", "main");

            assertEquals(200, response.getStatusCode().value());
            assertEquals("main", response.getBody().getName());
        }

        @Test
        @DisplayName("returns 404 when workflow not found")
        void returns404WhenNotFound() {
            when(introspectionService.getWorkflow("my-agent", "nonexistent")).thenReturn(null);

            ResponseEntity<WorkflowDto> response = controller.getWorkflow("my-agent", "nonexistent");

            assertEquals(404, response.getStatusCode().value());
        }
    }
}

