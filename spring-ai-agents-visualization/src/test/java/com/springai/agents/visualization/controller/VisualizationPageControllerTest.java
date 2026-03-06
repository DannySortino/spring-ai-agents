package com.springai.agents.visualization.controller;

import com.springai.agents.visualization.autoconfigure.VisualizationProperties;
import com.springai.agents.visualization.dto.AgentDetailDto;
import com.springai.agents.visualization.dto.AgentSummaryDto;
import com.springai.agents.visualization.service.AgentIntrospectionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.ui.ConcurrentModel;
import org.springframework.ui.Model;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("VisualizationPageController")
class VisualizationPageControllerTest {

    private AgentIntrospectionService introspectionService;
    private VisualizationProperties props;
    private VisualizationPageController controller;

    @BeforeEach
    void setUp() {
        introspectionService = mock(AgentIntrospectionService.class);
        props = new VisualizationProperties();
        controller = new VisualizationPageController(introspectionService, props);
    }

    // ── Dashboard ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("dashboard")
    class Dashboard {

        @Test
        @DisplayName("returns 'dashboard' view name")
        void returnsDashboardView() {
            when(introspectionService.getAllAgents()).thenReturn(List.of());
            Model model = new ConcurrentModel();

            String view = controller.dashboard(model);

            assertEquals("dashboard", view);
        }

        @Test
        @DisplayName("adds agents and props to model")
        void addsModelAttributes() {
            List<AgentSummaryDto> agents = List.of(
                    AgentSummaryDto.builder().name("a").description("A").workflowCount(1).build()
            );
            when(introspectionService.getAllAgents()).thenReturn(agents);
            Model model = new ConcurrentModel();

            controller.dashboard(model);

            assertTrue(model.containsAttribute("agents"));
            assertTrue(model.containsAttribute("props"));
            assertEquals(agents, model.getAttribute("agents"));
        }
    }

    // ── Agent Detail ────────────────────────────────────────────────────

    @Nested
    @DisplayName("agentDetail")
    class AgentDetail {

        @Test
        @DisplayName("returns 'agent-detail' view when agent exists")
        void returnsAgentDetailView() {
            AgentDetailDto detail = AgentDetailDto.builder()
                    .name("my-agent").description("desc").workflowCount(1).workflows(List.of()).build();
            when(introspectionService.getAgentDetail("my-agent")).thenReturn(detail);
            Model model = new ConcurrentModel();

            String view = controller.agentDetail("my-agent", model);

            assertEquals("agent-detail", view);
            assertEquals(detail, model.getAttribute("agent"));
            assertNotNull(model.getAttribute("props"));
        }

        @Test
        @DisplayName("redirects to dashboard when agent not found")
        void redirectsWhenNotFound() {
            when(introspectionService.getAgentDetail("nonexistent")).thenReturn(null);
            Model model = new ConcurrentModel();

            String view = controller.agentDetail("nonexistent", model);

            assertEquals("redirect:/agents-ui/", view);
        }
    }

    // ── History ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("history returns 'history' view with props")
    void historyView() {
        Model model = new ConcurrentModel();

        String view = controller.history(model);

        assertEquals("history", view);
        assertTrue(model.containsAttribute("props"));
    }

    // ── Analytics ───────────────────────────────────────────────────────

    @Test
    @DisplayName("analytics returns 'analytics' view with props")
    void analyticsView() {
        Model model = new ConcurrentModel();

        String view = controller.analytics(model);

        assertEquals("analytics", view);
        assertTrue(model.containsAttribute("props"));
    }

    // ── Compare ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("compare")
    class Compare {

        @Test
        @DisplayName("returns 'compare' view for multi-workflow agent")
        void returnsCompareView() {
            AgentDetailDto detail = AgentDetailDto.builder()
                    .name("smart").description("desc").workflowCount(2)
                    .multiWorkflow(true).workflows(List.of()).build();
            when(introspectionService.getAgentDetail("smart")).thenReturn(detail);
            Model model = new ConcurrentModel();

            String view = controller.compare("smart", model);

            assertEquals("compare", view);
            assertEquals(detail, model.getAttribute("agent"));
        }

        @Test
        @DisplayName("redirects when agent not found")
        void redirectsWhenNotFound() {
            when(introspectionService.getAgentDetail("nonexistent")).thenReturn(null);
            Model model = new ConcurrentModel();

            String view = controller.compare("nonexistent", model);

            assertEquals("redirect:/agents-ui/", view);
        }

        @Test
        @DisplayName("redirects when agent is not multi-workflow")
        void redirectsWhenSingleWorkflow() {
            AgentDetailDto detail = AgentDetailDto.builder()
                    .name("simple").description("desc").workflowCount(1)
                    .multiWorkflow(false).workflows(List.of()).build();
            when(introspectionService.getAgentDetail("simple")).thenReturn(detail);
            Model model = new ConcurrentModel();

            String view = controller.compare("simple", model);

            assertEquals("redirect:/agents-ui/", view);
        }
    }
}

