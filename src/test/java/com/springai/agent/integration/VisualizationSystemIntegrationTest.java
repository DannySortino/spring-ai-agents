package com.springai.agent.integration;

import com.springai.agent.config.AgentsProperties;
import com.springai.agent.config.AppProperties;
import com.springai.agent.config.AppProperties.AgentDef;
import com.springai.agent.config.AppProperties.WorkflowDef;
import com.springai.agent.config.AppProperties.WorkflowStepDef;
import com.springai.agent.config.AppProperties.VisualizationDef;
import com.springai.agent.config.WorkflowType;
import com.springai.agent.config.VisualizationConfiguration;
import com.springai.agent.service.GraphVisualizationService;
import com.springai.agent.service.ExecutionStatusService;
import com.springai.agent.controller.VisualizationController;
import com.springai.agent.controller.GraphCreatorController;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureWebMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import org.springframework.boot.test.mock.mockito.MockBean;

/**
 * Integration tests for the complete visualization system.
 * Tests end-to-end functionality including REST endpoints, services, and configuration.
 */
@SpringBootTest(classes = {
    VisualizationConfiguration.class,
    VisualizationSystemIntegrationTest.TestConfig.class
})
@EnableAutoConfiguration(exclude = {
    org.springframework.boot.autoconfigure.web.servlet.error.ErrorMvcAutoConfiguration.class
})
@AutoConfigureWebMvc
@AutoConfigureMockMvc
@TestPropertySource(properties = {
    "spring.ai.agents.visualization.graph-structure=true",
    "spring.ai.agents.visualization.real-time-status=true",
    "spring.ai.agents.visualization.interactive-creator=true",
    "agents.list="  // Override agents configuration to empty
})
class VisualizationSystemIntegrationTest {

    @TestConfiguration
    static class TestConfig {
        @Bean
        @Primary
        public ChatModel mockChatModel() {
            return mock(ChatModel.class);
        }
        
        // Create test agents data
        private static List<AgentsProperties.AgentDef> createTestAgents() {
            return Arrays.asList(
                createTestAgentsPropertiesAgent("test-agent-1", "Test Agent 1", Arrays.asList(
                    createAgentsPropertiesStep("step1", "Process: {input}", null),
                    createAgentsPropertiesStep("step2", "Analyze: {step1}", Arrays.asList("step1"))
                )),
                createTestAgentsPropertiesAgent("test-agent-2", "Test Agent 2", Arrays.asList(
                    createAgentsPropertiesStep("root", "Root: {input}", null),
                    createAgentsPropertiesStep("branch_a", "Branch A: {root}", Arrays.asList("root")),
                    createAgentsPropertiesStep("branch_b", "Branch B: {root}", Arrays.asList("root")),
                    createAgentsPropertiesStep("merge", "Merge: {branch_a} {branch_b}", Arrays.asList("branch_a", "branch_b"))
                ))
            );
        }
        
        @Bean
        @Primary
        public AppProperties testAppProperties() {
            AppProperties appProperties = new AppProperties();
            
            // Create test agents with graph workflows
            List<AgentDef> agents = Arrays.asList(
                createTestAgent("test-agent-1", "Test Agent 1", Arrays.asList(
                    createStep("step1", "Process: {input}", null),
                    createStep("step2", "Analyze: {step1}", Arrays.asList("step1"))
                )),
                createTestAgent("test-agent-2", "Test Agent 2", Arrays.asList(
                    createStep("root", "Root: {input}", null),
                    createStep("branch_a", "Branch A: {root}", Arrays.asList("root")),
                    createStep("branch_b", "Branch B: {root}", Arrays.asList("root")),
                    createStep("merge", "Merge: {branch_a} {branch_b}", Arrays.asList("branch_a", "branch_b"))
                ))
            );
            
            appProperties.setAgents(agents);
            
            // Set visualization configuration
            VisualizationDef visualization = new VisualizationDef();
            visualization.setGraphStructure(true);
            visualization.setRealTimeStatus(true);
            visualization.setInteractiveCreator(true);
            appProperties.setVisualization(visualization);
            
            return appProperties;
        }
        
        private static AgentDef createTestAgent(String name, String systemPrompt, List<WorkflowStepDef> steps) {
            WorkflowDef workflow = new WorkflowDef();
            workflow.setType(WorkflowType.GRAPH);
            workflow.setChain(steps);
            
            AgentDef agent = new AgentDef();
            agent.setName(name);
            agent.setSystemPrompt(systemPrompt);
            agent.setWorkflow(workflow);
            return agent;
        }
        
        private static WorkflowStepDef createStep(String nodeId, String prompt, List<String> dependsOn) {
            WorkflowStepDef step = new WorkflowStepDef();
            step.setNodeId(nodeId);
            step.setPrompt(prompt);
            step.setDependsOn(dependsOn);
            return step;
        }
        
        private static AgentsProperties.AgentDef createTestAgentsPropertiesAgent(String name, String systemPrompt, List<AgentsProperties.WorkflowStepDef> steps) {
            AgentsProperties.WorkflowDef workflow = new AgentsProperties.WorkflowDef();
            workflow.setType(WorkflowType.GRAPH);
            workflow.setChain(steps);
            
            AgentsProperties.AgentDef agent = new AgentsProperties.AgentDef();
            agent.setName(name);
            agent.setSystemPrompt(systemPrompt);
            agent.setWorkflow(workflow);
            return agent;
        }
        
        private static AgentsProperties.WorkflowStepDef createAgentsPropertiesStep(String nodeId, String prompt, List<String> dependsOn) {
            AgentsProperties.WorkflowStepDef step = new AgentsProperties.WorkflowStepDef();
            step.setNodeId(nodeId);
            step.setPrompt(prompt);
            step.setDependsOn(dependsOn);
            return step;
        }
    }

    @MockBean
    private AgentsProperties agentsProperties;
    
    @Autowired
    private MockMvc mockMvc;
    
    @Autowired
    private GraphVisualizationService graphVisualizationService;
    
    @Autowired
    private ExecutionStatusService executionStatusService;
    
    @Autowired
    private VisualizationController visualizationController;
    
    @Autowired
    private GraphCreatorController graphCreatorController;
    
    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        // Configure the mock AgentsProperties to return test data
        when(agentsProperties.getList()).thenReturn(TestConfig.createTestAgents());
    }

    @Test
    @DisplayName("Should have all visualization components properly wired")
    void testComponentWiring() {
        assertNotNull(graphVisualizationService);
        assertNotNull(executionStatusService);
        assertNotNull(visualizationController);
        assertNotNull(graphCreatorController);
    }

    @Test
    @DisplayName("Should return visualization configuration via REST API")
    void testGetVisualizationConfig() throws Exception {
        mockMvc.perform(get("/visualization/config"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.graphStructureEnabled").value(true))
                .andExpect(jsonPath("$.realTimeStatusEnabled").value(true))
                .andExpect(jsonPath("$.interactiveCreatorEnabled").value(true))
                .andExpect(jsonPath("$.basePath").value("/visualization"))
                .andExpect(jsonPath("$.websocketEndpoint").value("/ws/status"));
    }

    @Test
    @DisplayName("Should return all agent graphs via REST API")
    void testGetAllAgentGraphs() throws Exception {
        mockMvc.perform(get("/visualization/graphs"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].agentName").value("test-agent-1"))
                .andExpect(jsonPath("$[0].nodes").isArray())
                .andExpect(jsonPath("$[0].nodes.length()").value(2))
                .andExpect(jsonPath("$[0].edges").isArray())
                .andExpect(jsonPath("$[0].edges.length()").value(1))
                .andExpect(jsonPath("$[1].agentName").value("test-agent-2"))
                .andExpect(jsonPath("$[1].nodes.length()").value(4))
                .andExpect(jsonPath("$[1].edges.length()").value(4));
    }

    @Test
    @DisplayName("Should return specific agent graph via REST API")
    void testGetSpecificAgentGraph() throws Exception {
        mockMvc.perform(get("/visualization/graphs/test-agent-1"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.agentName").value("test-agent-1"))
                .andExpect(jsonPath("$.systemPrompt").value("Test Agent 1"))
                .andExpect(jsonPath("$.nodes").isArray())
                .andExpect(jsonPath("$.nodes.length()").value(2))
                .andExpect(jsonPath("$.nodes[0].id").value("step1"))
                .andExpect(jsonPath("$.nodes[0].type").value("prompt"))
                .andExpect(jsonPath("$.nodes[1].id").value("step2"))
                .andExpect(jsonPath("$.nodes[1].type").value("prompt"))
                .andExpect(jsonPath("$.edges").isArray())
                .andExpect(jsonPath("$.edges.length()").value(1))
                .andExpect(jsonPath("$.edges[0].source").value("step1"))
                .andExpect(jsonPath("$.edges[0].target").value("step2"));
    }

    @Test
    @DisplayName("Should return 404 for non-existent agent graph")
    void testGetNonExistentAgentGraph() throws Exception {
        mockMvc.perform(get("/visualization/graphs/non-existent-agent"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Should return agents list via REST API")
    void testGetAgentsList() throws Exception {
        mockMvc.perform(get("/visualization/agents"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].name").value("test-agent-1"))
                .andExpect(jsonPath("$[0].systemPrompt").value("Test Agent 1"))
                .andExpect(jsonPath("$[0].workflowType").value("GRAPH"))
                .andExpect(jsonPath("$[0].nodeCount").value(2))
                .andExpect(jsonPath("$[1].name").value("test-agent-2"))
                .andExpect(jsonPath("$[1].systemPrompt").value("Test Agent 2"))
                .andExpect(jsonPath("$[1].workflowType").value("GRAPH"))
                .andExpect(jsonPath("$[1].nodeCount").value(4));
    }

    @Test
    @DisplayName("Should return health status via REST API")
    void testGetHealthStatus() throws Exception {
        mockMvc.perform(get("/visualization/health"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.graphStructureEnabled").value(true))
                .andExpect(jsonPath("$.realTimeStatusEnabled").value(true))
                .andExpect(jsonPath("$.interactiveCreatorEnabled").value(true))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    @DisplayName("Should return active executions via REST API")
    void testGetActiveExecutions() throws Exception {
        mockMvc.perform(get("/visualization/executions/active"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$").isArray());
        // Initially empty since no executions are running
    }

    @Test
    @DisplayName("Should return execution history via REST API")
    void testGetExecutionHistory() throws Exception {
        mockMvc.perform(get("/visualization/executions/history"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$").isArray());
        // Initially empty since no executions have occurred
    }

    @Test
    @DisplayName("Should return execution history with custom limit")
    void testGetExecutionHistoryWithLimit() throws Exception {
        mockMvc.perform(get("/visualization/executions/history?limit=10"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    @DisplayName("Should return 404 for non-existent execution status")
    void testGetNonExistentExecutionStatus() throws Exception {
        mockMvc.perform(get("/visualization/executions/non-existent-id"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Should return available node types via REST API")
    void testGetNodeTypes() throws Exception {
        mockMvc.perform(get("/visualization/creator/node-types"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(5))
                .andExpect(jsonPath("$[0].type").value("input"))
                .andExpect(jsonPath("$[0].name").value("Input Node"))
                .andExpect(jsonPath("$[0].description").value("Entry point for agent workflow - receives initial data"))
                .andExpect(jsonPath("$[0].requiredFields").isArray())
                .andExpect(jsonPath("$[1].type").value("output"))
                .andExpect(jsonPath("$[1].name").value("Output Node"))
                .andExpect(jsonPath("$[2].type").value("prompt"))
                .andExpect(jsonPath("$[2].name").value("Prompt Node"))
                .andExpect(jsonPath("$[3].type").value("tool"))
                .andExpect(jsonPath("$[3].name").value("Tool Node"))
                .andExpect(jsonPath("$[4].type").value("conditional"))
                .andExpect(jsonPath("$[4].name").value("Conditional Node"));
    }

    @Test
    @DisplayName("Should return available templates via REST API")
    void testGetTemplates() throws Exception {
        mockMvc.perform(get("/visualization/creator/templates"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(4))
                .andExpect(jsonPath("$[0].id").value("simple-chain"))
                .andExpect(jsonPath("$[0].name").value("Simple Chain"))
                .andExpect(jsonPath("$[0].nodes").isArray())
                .andExpect(jsonPath("$[1].id").value("parallel-processing"))
                .andExpect(jsonPath("$[1].name").value("Parallel Processing"))
                .andExpect(jsonPath("$[2].id").value("orchestrator"))
                .andExpect(jsonPath("$[2].name").value("Orchestrator Pattern"))
                .andExpect(jsonPath("$[3].id").value("conditional"))
                .andExpect(jsonPath("$[3].name").value("Conditional Logic"));
    }

    @Test
    @DisplayName("Should validate graph definition via REST API")
    void testValidateGraphDefinition() throws Exception {
        String validGraphJson = """
            {
                "name": "test-graph",
                "systemPrompt": "Test system prompt",
                "nodes": [
                    {
                        "nodeId": "step1",
                        "prompt": "Process input: {input}",
                        "dependsOn": null
                    },
                    {
                        "nodeId": "step2",
                        "prompt": "Analyze: {step1}",
                        "dependsOn": ["step1"]
                    }
                ]
            }
            """;

        mockMvc.perform(post("/visualization/creator/validate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(validGraphJson))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.valid").value(true))
                .andExpect(jsonPath("$.errors").isArray())
                .andExpect(jsonPath("$.errors").isEmpty())
                .andExpect(jsonPath("$.message").value("Graph is valid"));
    }

    @Test
    @DisplayName("Should detect validation errors in graph definition")
    void testValidateInvalidGraphDefinition() throws Exception {
        String invalidGraphJson = """
            {
                "name": null,
                "systemPrompt": "Test system prompt",
                "nodes": [
                    {
                        "nodeId": "step1",
                        "prompt": null,
                        "tool": null,
                        "conditional": null
                    }
                ]
            }
            """;

        mockMvc.perform(post("/visualization/creator/validate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(invalidGraphJson))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.valid").value(false))
                .andExpect(jsonPath("$.errors").isArray())
                .andExpect(jsonPath("$.errors").isNotEmpty())
                .andExpect(jsonPath("$.message").value("Graph has validation errors"));
    }

    @Test
    @DisplayName("Should generate YAML from graph definition via REST API")
    void testGenerateYamlFromGraphDefinition() throws Exception {
        String graphJson = """
            {
                "name": "test-yaml-agent",
                "systemPrompt": "Test YAML generation",
                "nodes": [
                    {
                        "nodeId": "step1",
                        "prompt": "Process: {input}",
                        "dependsOn": null
                    }
                ]
            }
            """;

        mockMvc.perform(post("/visualization/creator/generate-yaml")
                .contentType(MediaType.APPLICATION_JSON)
                .content(graphJson))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.TEXT_PLAIN))
                .andExpect(header().string("Content-Disposition", 
                    "attachment; filename=\"test-yaml-agent-workflow.yml\""))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("agents:")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("list:")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("name: test-yaml-agent")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("type: graph")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("nodeId: step1")));
    }

    @Test
    @DisplayName("Should integrate GraphVisualizationService with actual data")
    void testGraphVisualizationServiceIntegration() {
        // Test that the service can extract data from the configured agents
        var allGraphs = graphVisualizationService.getAllAgentGraphs();
        assertEquals(2, allGraphs.size());
        
        var agent1Graph = graphVisualizationService.getAgentGraph("test-agent-1");
        assertTrue(agent1Graph.isPresent());
        assertEquals("test-agent-1", agent1Graph.get().agentName());
        assertEquals(2, agent1Graph.get().nodes().size());
        assertEquals(1, agent1Graph.get().edges().size());
        
        var agent2Graph = graphVisualizationService.getAgentGraph("test-agent-2");
        assertTrue(agent2Graph.isPresent());
        assertEquals("test-agent-2", agent2Graph.get().agentName());
        assertEquals(4, agent2Graph.get().nodes().size());
        assertEquals(4, agent2Graph.get().edges().size());
    }

    @Test
    @DisplayName("Should integrate ExecutionStatusService with tracking capabilities")
    void testExecutionStatusServiceIntegration() {
        // Test that the service can track executions
        String executionId = executionStatusService.startExecution("test-agent", 
            Arrays.asList("step1", "step2"));
        assertNotNull(executionId);
        
        // Update node status
        executionStatusService.updateNodeStatus(executionId, "step1", 
            ExecutionStatusService.NodeState.RUNNING, null, null, null);
        executionStatusService.updateNodeStatus(executionId, "step1", 
            ExecutionStatusService.NodeState.COMPLETED, "Result 1", null, 1000L);
        
        // Complete execution
        executionStatusService.completeExecution(executionId, true);
        
        // Verify tracking
        var status = executionStatusService.getExecutionStatus(executionId);
        assertTrue(status.isPresent());
        assertEquals("test-agent", status.get().execution().agentName());
        assertEquals(ExecutionStatusService.ExecutionState.COMPLETED, 
            status.get().execution().state());
        
        var history = executionStatusService.getExecutionHistory(10);
        assertEquals(1, history.size());
    }

    @Test
    @DisplayName("Should handle CORS requests properly")
    void testCorsSupport() throws Exception {
        mockMvc.perform(options("/visualization/config")
                .header("Origin", "http://localhost:3000")
                .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Should handle malformed JSON requests gracefully")
    void testMalformedJsonHandling() throws Exception {
        String malformedJson = "{ invalid json }";

        mockMvc.perform(post("/visualization/creator/validate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(malformedJson))
                .andExpect(status().isBadRequest());
    }
}




