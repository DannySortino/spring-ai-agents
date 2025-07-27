package com.springai.agent.controller;

import com.springai.agent.config.AgentsProperties;
import com.springai.agent.config.AppProperties.VisualizationDef;
import com.springai.agent.config.VisualizationProperties;
import com.springai.agent.service.GraphVisualizationService;
import com.springai.agent.service.ExecutionStatusService;
import com.springai.agent.service.GraphVisualizationService.AgentGraphData;
import com.springai.agent.service.GraphVisualizationService.NodeData;
import com.springai.agent.service.GraphVisualizationService.EdgeData;
import com.springai.agent.service.ExecutionStatusService.ExecutionStatusData;
import com.springai.agent.service.ExecutionStatusService.ExecutionStatus;
import com.springai.agent.service.ExecutionStatusService.ExecutionState;
import com.springai.agent.controller.VisualizationController.VisualizationConfig;
import com.springai.agent.controller.VisualizationController.HealthStatus;
import com.springai.agent.controller.VisualizationController.AgentInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Map;
import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for VisualizationController.
 * Tests REST API endpoints for graph visualization and execution status.
 */
class VisualizationControllerTest {

    private VisualizationController controller;
    
    @Mock
    private VisualizationProperties visualizationProperties;
    
    @Mock
    private AgentsProperties agentsProperties;
    
    @Mock
    private GraphVisualizationService graphVisualizationService;
    
    @Mock
    private ExecutionStatusService executionStatusService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        controller = new VisualizationController(visualizationProperties, agentsProperties, graphVisualizationService, executionStatusService);
    }

    @Test
    @DisplayName("Should return configuration with all features enabled")
    void testGetConfig_AllFeaturesEnabled() {
        // Given
        when(visualizationProperties.isGraphStructure()).thenReturn(true);
        when(visualizationProperties.isRealTimeStatus()).thenReturn(true);
        when(visualizationProperties.isInteractiveCreator()).thenReturn(true);
        when(visualizationProperties.getBasePath()).thenReturn("/visualization");
        when(visualizationProperties.getWebsocketEndpoint()).thenReturn("/ws/status");

        // When
        ResponseEntity<VisualizationConfig> response = controller.getConfig();

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        
        VisualizationConfig config = response.getBody();
        assertTrue(config.graphStructureEnabled());
        assertTrue(config.realTimeStatusEnabled());
        assertTrue(config.interactiveCreatorEnabled());
        assertEquals("/visualization", config.basePath());
        assertEquals("/ws/status", config.websocketEndpoint());
    }

    @Test
    @DisplayName("Should return default configuration when visualization features are disabled")
    void testGetConfig_DefaultConfiguration() {
        // Given
        when(visualizationProperties.isGraphStructure()).thenReturn(false);
        when(visualizationProperties.isRealTimeStatus()).thenReturn(false);
        when(visualizationProperties.isInteractiveCreator()).thenReturn(false);
        when(visualizationProperties.getBasePath()).thenReturn("/visualization");
        when(visualizationProperties.getWebsocketEndpoint()).thenReturn("/ws/status");

        // When
        ResponseEntity<VisualizationConfig> response = controller.getConfig();

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        
        VisualizationConfig config = response.getBody();
        assertFalse(config.graphStructureEnabled()); // Default values
        assertFalse(config.realTimeStatusEnabled());
        assertFalse(config.interactiveCreatorEnabled());
        assertEquals("/visualization", config.basePath());
        assertEquals("/ws/status", config.websocketEndpoint());
    }

    @Test
    @DisplayName("Should return all agent graphs")
    void testGetAllGraphs() {
        // Given
        List<NodeData> nodes = Arrays.asList(
                new NodeData("node1", "Node 1", "Description 1", "prompt", new HashMap<>()),
                new NodeData("node2", "Node 2", "Description 2", "tool", new HashMap<>())
        );
        List<EdgeData> edges = Arrays.asList(
                new EdgeData("node1", "node2")
        );
        
        AgentGraphData graphData = new AgentGraphData("test-agent", "System prompt", nodes, edges);
        List<AgentGraphData> expectedGraphs = Arrays.asList(graphData);
        
        when(graphVisualizationService.getAllAgentGraphs()).thenReturn(expectedGraphs);

        // When
        ResponseEntity<List<AgentGraphData>> response = controller.getAllGraphs();

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(1, response.getBody().size());
        assertEquals("test-agent", response.getBody().get(0).agentName());
        
        verify(graphVisualizationService).getAllAgentGraphs();
    }

    @Test
    @DisplayName("Should return empty list when no graphs available")
    void testGetAllGraphs_Empty() {
        // Given
        when(graphVisualizationService.getAllAgentGraphs()).thenReturn(Collections.emptyList());

        // When
        ResponseEntity<List<AgentGraphData>> response = controller.getAllGraphs();

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().isEmpty());
    }

    @Test
    @DisplayName("Should return specific agent graph")
    void testGetAgentGraph_Found() {
        // Given
        String agentName = "test-agent";
        List<NodeData> nodes = Arrays.asList(
                new NodeData("node1", "Node 1", "Description 1", "prompt", new HashMap<>())
        );
        AgentGraphData graphData = new AgentGraphData(agentName, "System prompt", nodes, Collections.emptyList());
        
        when(graphVisualizationService.getAgentGraph(agentName)).thenReturn(Optional.of(graphData));

        // When
        ResponseEntity<AgentGraphData> response = controller.getAgentGraph(agentName);

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(agentName, response.getBody().agentName());
        
        verify(graphVisualizationService).getAgentGraph(agentName);
    }

    @Test
    @DisplayName("Should return 404 when agent graph not found")
    void testGetAgentGraph_NotFound() {
        // Given
        String agentName = "nonexistent-agent";
        when(graphVisualizationService.getAgentGraph(agentName)).thenReturn(Optional.empty());

        // When
        ResponseEntity<AgentGraphData> response = controller.getAgentGraph(agentName);

        // Then
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertNull(response.getBody());
        
        verify(graphVisualizationService).getAgentGraph(agentName);
    }

    @Test
    @DisplayName("Should return active executions")
    void testGetActiveExecutions() {
        // Given
        ExecutionStatus execution = new ExecutionStatus("exec1", "agent1", ExecutionState.RUNNING, 
                LocalDateTime.now(), null);
        ExecutionStatusData statusData = new ExecutionStatusData(execution, Collections.emptyList());
        List<ExecutionStatusData> expectedExecutions = Arrays.asList(statusData);
        
        when(executionStatusService.getAllActiveExecutions()).thenReturn(expectedExecutions);

        // When
        ResponseEntity<List<ExecutionStatusData>> response = controller.getActiveExecutions();

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(1, response.getBody().size());
        assertEquals("exec1", response.getBody().get(0).execution().executionId());
        
        verify(executionStatusService).getAllActiveExecutions();
    }

    @Test
    @DisplayName("Should return execution history with default limit")
    void testGetExecutionHistory_DefaultLimit() {
        // Given
        ExecutionStatus execution = new ExecutionStatus("exec1", "agent1", ExecutionState.COMPLETED, 
                LocalDateTime.now().minusMinutes(5), LocalDateTime.now());
        ExecutionStatusData statusData = new ExecutionStatusData(execution, Collections.emptyList());
        List<ExecutionStatusData> expectedHistory = Arrays.asList(statusData);
        
        when(executionStatusService.getExecutionHistory(50)).thenReturn(expectedHistory);

        // When
        ResponseEntity<List<ExecutionStatusData>> response = controller.getExecutionHistory(50);

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(1, response.getBody().size());
        
        verify(executionStatusService).getExecutionHistory(50);
    }

    @Test
    @DisplayName("Should return execution history with custom limit")
    void testGetExecutionHistory_CustomLimit() {
        // Given
        int customLimit = 10;
        when(executionStatusService.getExecutionHistory(customLimit)).thenReturn(Collections.emptyList());

        // When
        ResponseEntity<List<ExecutionStatusData>> response = controller.getExecutionHistory(customLimit);

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().isEmpty());
        
        verify(executionStatusService).getExecutionHistory(customLimit);
    }

    @Test
    @DisplayName("Should return specific execution status")
    void testGetExecutionStatus_Found() {
        // Given
        String executionId = "exec1";
        ExecutionStatus execution = new ExecutionStatus(executionId, "agent1", ExecutionState.RUNNING, 
                LocalDateTime.now(), null);
        ExecutionStatusData statusData = new ExecutionStatusData(execution, Collections.emptyList());
        
        when(executionStatusService.getExecutionStatus(executionId)).thenReturn(Optional.of(statusData));

        // When
        ResponseEntity<ExecutionStatusData> response = controller.getExecutionStatus(executionId);

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(executionId, response.getBody().execution().executionId());
        
        verify(executionStatusService).getExecutionStatus(executionId);
    }

    @Test
    @DisplayName("Should return 404 when execution status not found")
    void testGetExecutionStatus_NotFound() {
        // Given
        String executionId = "nonexistent-exec";
        when(executionStatusService.getExecutionStatus(executionId)).thenReturn(Optional.empty());

        // When
        ResponseEntity<ExecutionStatusData> response = controller.getExecutionStatus(executionId);

        // Then
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertNull(response.getBody());
        
        verify(executionStatusService).getExecutionStatus(executionId);
    }

    @Test
    @DisplayName("Should return health status with all features enabled")
    void testHealth_AllFeaturesEnabled() {
        // Given
        when(visualizationProperties.isGraphStructure()).thenReturn(true);
        when(visualizationProperties.isRealTimeStatus()).thenReturn(true);
        when(visualizationProperties.isInteractiveCreator()).thenReturn(true);

        // When
        ResponseEntity<HealthStatus> response = controller.health();

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        
        HealthStatus health = response.getBody();
        assertEquals("UP", health.status());
        assertTrue(health.graphStructureEnabled());
        assertTrue(health.realTimeStatusEnabled());
        assertTrue(health.interactiveCreatorEnabled());
        assertTrue(health.timestamp() > 0);
    }

    @Test
    @DisplayName("Should return agents list")
    void testGetAgents() {
        // Given
        AgentsProperties.AgentDef agent1 = new AgentsProperties.AgentDef();
        agent1.setName("agent1");
        agent1.setSystemPrompt("System prompt 1");
        
        AgentsProperties.AgentDef agent2 = new AgentsProperties.AgentDef();
        agent2.setName("agent2");
        agent2.setSystemPrompt("System prompt 2");
        
        when(agentsProperties.getList()).thenReturn(Arrays.asList(agent1, agent2));

        // When
        ResponseEntity<List<AgentInfo>> response = controller.getAgents();

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(2, response.getBody().size());
        
        AgentInfo info1 = response.getBody().get(0);
        assertEquals("agent1", info1.name());
        assertEquals("System prompt 1", info1.systemPrompt());
        assertEquals("NONE", info1.workflowType());
        assertEquals(0, info1.nodeCount());
        
        AgentInfo info2 = response.getBody().get(1);
        assertEquals("agent2", info2.name());
        assertEquals("System prompt 2", info2.systemPrompt());
        assertEquals("NONE", info2.workflowType());
        assertEquals(0, info2.nodeCount());
    }

    @Test
    @DisplayName("Should handle agents with empty system prompt")
    void testGetAgents_EmptySystemPrompt() {
        // Given
        AgentsProperties.AgentDef agent = new AgentsProperties.AgentDef();
        agent.setName("agent1");
        agent.setSystemPrompt(null);
        
        when(agentsProperties.getList()).thenReturn(Arrays.asList(agent));

        // When
        ResponseEntity<List<AgentInfo>> response = controller.getAgents();

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(1, response.getBody().size());
        
        AgentInfo agentInfo = response.getBody().get(0);
        assertEquals("agent1", agentInfo.name());
        assertNull(agentInfo.systemPrompt());
    }
}

