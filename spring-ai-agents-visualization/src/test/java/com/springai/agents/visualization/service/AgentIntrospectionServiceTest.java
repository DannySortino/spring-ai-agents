package com.springai.agents.visualization.service;

import com.springai.agents.agent.AgentRegistry;
import com.springai.agents.agent.AgentRuntime;
import com.springai.agents.node.*;
import com.springai.agents.visualization.dto.*;
import com.springai.agents.workflow.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("AgentIntrospectionService")
class AgentIntrospectionServiceTest {

    private AgentRegistry agentRegistry;
    private AgentIntrospectionService service;

    @BeforeEach
    void setUp() {
        agentRegistry = mock(AgentRegistry.class);
        service = new AgentIntrospectionService(agentRegistry);
    }

    // ── getAllAgents ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("getAllAgents")
    class GetAllAgents {

        @Test
        @DisplayName("returns summaries for all registered agents")
        void returnsSummaries() {
            AgentRuntime rt1 = createMockRuntime("agent-1", "First agent", 1, 5);
            AgentRuntime rt2 = createMockRuntime("agent-2", "Second agent", 2, 10);

            when(agentRegistry.getAgentNames()).thenReturn(Set.of("agent-1", "agent-2"));
            when(agentRegistry.getSyncAgent("agent-1")).thenReturn(rt1);
            when(agentRegistry.getSyncAgent("agent-2")).thenReturn(rt2);

            List<AgentSummaryDto> summaries = service.getAllAgents();

            assertEquals(2, summaries.size());
        }

        @Test
        @DisplayName("returns empty list when no agents registered")
        void emptyWhenNoAgents() {
            when(agentRegistry.getAgentNames()).thenReturn(Set.of());

            List<AgentSummaryDto> summaries = service.getAllAgents();
            assertTrue(summaries.isEmpty());
        }

        @Test
        @DisplayName("skips agents that return null from getSyncAgent")
        void skipsNullAgents() {
            AgentRuntime rt = createMockRuntime("agent-1", "desc", 1, 0);
            when(agentRegistry.getAgentNames()).thenReturn(Set.of("agent-1", "ghost"));
            when(agentRegistry.getSyncAgent("agent-1")).thenReturn(rt);
            when(agentRegistry.getSyncAgent("ghost")).thenReturn(null);

            List<AgentSummaryDto> summaries = service.getAllAgents();
            assertEquals(1, summaries.size());
            assertEquals("agent-1", summaries.get(0).getName());
        }
    }

    // ── getAgentSummary ─────────────────────────────────────────────────

    @Nested
    @DisplayName("getAgentSummary")
    class GetAgentSummary {

        @Test
        @DisplayName("returns correct summary fields")
        void returnsSummary() {
            AgentRuntime runtime = createMockRuntime("my-agent", "A test agent", 2, 42);
            when(agentRegistry.getSyncAgent("my-agent")).thenReturn(runtime);

            AgentSummaryDto summary = service.getAgentSummary("my-agent");

            assertNotNull(summary);
            assertEquals("my-agent", summary.getName());
            assertEquals("A test agent", summary.getDescription());
            assertEquals(2, summary.getWorkflowCount());
            assertTrue(summary.isMultiWorkflow());
            assertEquals(42, summary.getInvocationCount());
        }

        @Test
        @DisplayName("single workflow agent reports multiWorkflow=false")
        void singleWorkflowNotMulti() {
            AgentRuntime runtime = createMockRuntime("single", "desc", 1, 0);
            when(agentRegistry.getSyncAgent("single")).thenReturn(runtime);

            AgentSummaryDto summary = service.getAgentSummary("single");
            assertFalse(summary.isMultiWorkflow());
            assertEquals(1, summary.getWorkflowCount());
        }

        @Test
        @DisplayName("returns null for unknown agent")
        void nullForUnknown() {
            when(agentRegistry.getSyncAgent("nonexistent")).thenReturn(null);
            assertNull(service.getAgentSummary("nonexistent"));
        }
    }

    // ── getAgentDetail ──────────────────────────────────────────────────

    @Nested
    @DisplayName("getAgentDetail")
    class GetAgentDetail {

        @Test
        @DisplayName("returns full detail with workflow structures")
        void returnsDetailWithWorkflows() {
            Workflow workflow = buildSimpleWorkflow("main", "Main workflow");
            AgentRuntime runtime = createMockRuntime("my-agent", "desc", List.of(workflow), 5);
            when(agentRegistry.getSyncAgent("my-agent")).thenReturn(runtime);

            AgentDetailDto detail = service.getAgentDetail("my-agent");

            assertNotNull(detail);
            assertEquals("my-agent", detail.getName());
            assertEquals(1, detail.getWorkflowCount());
            assertFalse(detail.isMultiWorkflow());
            assertEquals(5, detail.getInvocationCount());
            assertNotNull(detail.getWorkflows());
            assertEquals(1, detail.getWorkflows().size());

            WorkflowDto wfDto = detail.getWorkflows().get(0);
            assertEquals("main", wfDto.getName());
            assertEquals("Main workflow", wfDto.getDescription());
            assertFalse(wfDto.getNodes().isEmpty());
            assertFalse(wfDto.getEdges().isEmpty());
        }

        @Test
        @DisplayName("returns null for unknown agent")
        void nullForUnknown() {
            when(agentRegistry.getSyncAgent("nonexistent")).thenReturn(null);
            assertNull(service.getAgentDetail("nonexistent"));
        }

        @Test
        @DisplayName("converts all node types correctly")
        void convertsNodeTypes() {
            var input = InputNode.builder().id("input").build();
            var llm = LlmNode.builder().id("llm").promptTemplate("Process: {input}").systemPrompt("You are a bot.").build();
            var context = ContextNode.builder().id("ctx").contextText("background info").build();
            var output = OutputNode.builder().id("output").build();

            Workflow workflow = WorkflowBuilder.create()
                    .name("multi-type")
                    .description("Mixed node types")
                    .nodes(input, llm, context, output)
                    .edge(input, llm)
                    .edge(input, context)
                    .edge(llm, output)
                    .edge(context, output)
                    .build();

            AgentRuntime runtime = createMockRuntime("mixed", "Mixed agent", List.of(workflow), 0);
            when(agentRegistry.getSyncAgent("mixed")).thenReturn(runtime);

            AgentDetailDto detail = service.getAgentDetail("mixed");
            WorkflowDto wfDto = detail.getWorkflows().get(0);

            assertEquals(4, wfDto.getNodes().size());
            assertEquals(4, wfDto.getEdges().size());

            Map<String, NodeDto> nodeMap = new HashMap<>();
            wfDto.getNodes().forEach(n -> nodeMap.put(n.getId(), n));

            assertEquals("INPUT", nodeMap.get("input").getType());
            assertEquals("LLM", nodeMap.get("llm").getType());
            assertEquals("Process: {input}", nodeMap.get("llm").getPromptTemplate());
            assertEquals("You are a bot.", nodeMap.get("llm").getSystemPrompt());
            assertEquals("CONTEXT", nodeMap.get("ctx").getType());
            assertEquals("background info", nodeMap.get("ctx").getContextText());
            assertEquals("OUTPUT", nodeMap.get("output").getType());
        }
    }

    // ── getWorkflow ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("getWorkflow")
    class GetWorkflow {

        @Test
        @DisplayName("returns workflow by agent and workflow name")
        void returnsByName() {
            Workflow wf1 = buildSimpleWorkflow("analyze", "Analyze data");
            Workflow wf2 = buildSimpleWorkflow("summarize", "Summarize text");
            AgentRuntime runtime = createMockRuntime("smart", "Smart agent", List.of(wf1, wf2), 0);
            when(agentRegistry.getSyncAgent("smart")).thenReturn(runtime);

            WorkflowDto dto = service.getWorkflow("smart", "analyze");
            assertNotNull(dto);
            assertEquals("analyze", dto.getName());
            assertEquals("Analyze data", dto.getDescription());
        }

        @Test
        @DisplayName("returns null for unknown workflow name")
        void nullForUnknownWorkflow() {
            Workflow wf = buildSimpleWorkflow("main", "Main");
            AgentRuntime runtime = createMockRuntime("agent", "desc", List.of(wf), 0);
            when(agentRegistry.getSyncAgent("agent")).thenReturn(runtime);

            assertNull(service.getWorkflow("agent", "nonexistent"));
        }

        @Test
        @DisplayName("returns null for unknown agent")
        void nullForUnknownAgent() {
            when(agentRegistry.getSyncAgent("nonexistent")).thenReturn(null);
            assertNull(service.getWorkflow("nonexistent", "main"));
        }
    }

    // ── Helpers ─────────────────────────────────────────────────────────

    private AgentRuntime createMockRuntime(String name, String description,
                                            int workflowCount, int invocationCount) {
        List<Workflow> workflows = new ArrayList<>();
        for (int i = 0; i < workflowCount; i++) {
            workflows.add(buildSimpleWorkflow("wf-" + i, "Workflow " + i));
        }
        return createMockRuntime(name, description, workflows, invocationCount);
    }

    private AgentRuntime createMockRuntime(String name, String description,
                                            List<Workflow> workflows, int invocationCount) {
        AgentRuntime runtime = mock(AgentRuntime.class);
        when(runtime.getName()).thenReturn(name);
        when(runtime.getDescription()).thenReturn(description);
        when(runtime.getWorkflows()).thenReturn(workflows);
        when(runtime.getInvocationCount()).thenReturn(invocationCount);
        return runtime;
    }

    private Workflow buildSimpleWorkflow(String name, String description) {
        var input = InputNode.builder().id("input").build();
        var output = OutputNode.builder().id("output").build();

        return WorkflowBuilder.create()
                .name(name)
                .description(description)
                .nodes(input, output)
                .edge(input, output)
                .build();
    }
}

