package com.springai.agent.service;

import com.springai.agent.config.AgentsProperties;
import com.springai.agent.config.AgentsProperties.AgentDef;
import com.springai.agent.config.AgentsProperties.WorkflowDef;
import com.springai.agent.config.AgentsProperties.WorkflowStepDef;
import com.springai.agent.config.AgentsProperties.ConditionalStepDef;
import com.springai.agent.config.AgentsProperties.ConditionDef;
import com.springai.agent.config.WorkflowType;
import com.springai.agent.config.ConditionType;
import com.springai.agent.service.GraphVisualizationService.AgentGraphData;
import com.springai.agent.service.GraphVisualizationService.NodeData;
import com.springai.agent.service.GraphVisualizationService.EdgeData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for GraphVisualizationService.
 * Tests graph data extraction and preparation for visualization.
 */
class GraphVisualizationServiceTest {

    private GraphVisualizationService service;
    private AgentsProperties AgentsProperties;

    @BeforeEach
    void setUp() {
        AgentsProperties = new AgentsProperties();
        service = new GraphVisualizationService(AgentsProperties);
    }

    @Test
    @DisplayName("Should return empty list when no agents configured")
    void testGetAllAgentGraphs_NoAgents() {
        // Given
        AgentsProperties.setList(null);

        // When
        List<AgentGraphData> result = service.getAllAgentGraphs();

        // Then
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("Should return empty list when agents list is empty")
    void testGetAllAgentGraphs_EmptyAgents() {
        // Given
        AgentsProperties.setList(Collections.emptyList());

        // When
        List<AgentGraphData> result = service.getAllAgentGraphs();

        // Then
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("Should filter out agents without workflows")
    void testGetAllAgentGraphs_FilterAgentsWithoutWorkflows() {
        // Given
        AgentDef agentWithoutWorkflow = createAgentDef("agent1", null);
        AgentDef agentWithWorkflow = createAgentDef("agent2", createGraphWorkflow());
        AgentsProperties.setList(Arrays.asList(agentWithoutWorkflow, agentWithWorkflow));

        // When
        List<AgentGraphData> result = service.getAllAgentGraphs();

        // Then
        assertEquals(1, result.size());
        assertEquals("agent2", result.get(0).agentName());
    }

    @Test
    @DisplayName("Should filter out agents with non-graph workflows")
    void testGetAllAgentGraphs_FilterNonGraphWorkflows() {
        // Given
        WorkflowDef nonGraphWorkflow = new WorkflowDef();
        // Leave type as null so it gets filtered out as non-graph workflow
        
        AgentDef agentWithNonGraphWorkflow = createAgentDef("agent1", nonGraphWorkflow);
        AgentDef agentWithGraphWorkflow = createAgentDef("agent2", createGraphWorkflow());
        AgentsProperties.setList(Arrays.asList(agentWithNonGraphWorkflow, agentWithGraphWorkflow));

        // When
        List<AgentGraphData> result = service.getAllAgentGraphs();

        // Then
        assertEquals(1, result.size());
        assertEquals("agent2", result.get(0).agentName());
    }

    @Test
    @DisplayName("Should extract graph data for simple workflow")
    void testGetAllAgentGraphs_SimpleWorkflow() {
        // Given
        WorkflowStepDef step1 = createStep("step1", "Process input: {input}", null, null);
        WorkflowStepDef step2 = createStep("step2", "Analyze: {step1}", Arrays.asList("step1"), null);
        
        WorkflowDef workflow = new WorkflowDef();
        workflow.setType(WorkflowType.GRAPH);
        workflow.setChain(Arrays.asList(step1, step2));
        
        AgentDef agent = createAgentDef("test-agent", workflow);
        agent.setSystemPrompt("Test system prompt");
        AgentsProperties.setList(Arrays.asList(agent));

        // When
        List<AgentGraphData> result = service.getAllAgentGraphs();

        // Then
        assertEquals(1, result.size());
        
        AgentGraphData graphData = result.get(0);
        assertEquals("test-agent", graphData.agentName());
        assertEquals("Test system prompt", graphData.systemPrompt());
        assertEquals(2, graphData.nodes().size());
        assertEquals(1, graphData.edges().size());
        
        // Verify nodes
        NodeData node1 = findNodeById(graphData.nodes(), "step1");
        assertNotNull(node1);
        assertEquals("step1", node1.label());
        assertEquals("Process input: {input}", node1.description());
        assertEquals("prompt", node1.type());
        
        NodeData node2 = findNodeById(graphData.nodes(), "step2");
        assertNotNull(node2);
        assertEquals("step2", node2.label());
        assertEquals("Analyze: {step1}", node2.description());
        assertEquals("prompt", node2.type());
        
        // Verify edge
        EdgeData edge = graphData.edges().get(0);
        assertEquals("step1", edge.source());
        assertEquals("step2", edge.target());
    }

    @Test
    @DisplayName("Should extract graph data for workflow with tool nodes")
    void testGetAllAgentGraphs_WithToolNodes() {
        // Given
        WorkflowStepDef toolStep = createStep("search", null, null, "web_search");
        WorkflowStepDef promptStep = createStep("analyze", "Analyze: {search}", Arrays.asList("search"), null);
        
        WorkflowDef workflow = new WorkflowDef();
        workflow.setType(WorkflowType.GRAPH);
        workflow.setChain(Arrays.asList(toolStep, promptStep));
        
        AgentDef agent = createAgentDef("tool-agent", workflow);
        AgentsProperties.setList(Arrays.asList(agent));

        // When
        List<AgentGraphData> result = service.getAllAgentGraphs();

        // Then
        assertEquals(1, result.size());
        
        AgentGraphData graphData = result.get(0);
        assertEquals(2, graphData.nodes().size());
        
        NodeData toolNode = findNodeById(graphData.nodes(), "search");
        assertNotNull(toolNode);
        assertEquals("tool", toolNode.type());
        assertTrue((Boolean) toolNode.properties().get("hasTool"));
        assertEquals("web_search", toolNode.properties().get("tool"));
        
        NodeData promptNode = findNodeById(graphData.nodes(), "analyze");
        assertNotNull(promptNode);
        assertEquals("prompt", promptNode.type());
        assertTrue((Boolean) promptNode.properties().get("hasPrompt"));
    }

    @Test
    @DisplayName("Should extract graph data for workflow with conditional nodes")
    void testGetAllAgentGraphs_WithConditionalNodes() {
        // Given
        ConditionDef condition = new ConditionDef();
        condition.setType(ConditionType.CONTAINS);
        condition.setValue("urgent");
        
        WorkflowStepDef thenStep = createStep(null, "Handle urgent: {input}", null, null);
        WorkflowStepDef elseStep = createStep(null, "Handle normal: {input}", null, null);
        
        ConditionalStepDef conditional = new ConditionalStepDef();
        conditional.setCondition(condition);
        conditional.setThenStep(thenStep);
        conditional.setElseStep(elseStep);
        
        WorkflowStepDef conditionalStep = createStep("router", null, null, null);
        conditionalStep.setConditional(conditional);
        
        WorkflowDef workflow = new WorkflowDef();
        workflow.setType(WorkflowType.GRAPH);
        workflow.setChain(Arrays.asList(conditionalStep));
        
        AgentDef agent = createAgentDef("conditional-agent", workflow);
        AgentsProperties.setList(Arrays.asList(agent));

        // When
        List<AgentGraphData> result = service.getAllAgentGraphs();

        // Then
        assertEquals(1, result.size());
        
        AgentGraphData graphData = result.get(0);
        assertEquals(3, graphData.nodes().size()); // Main node + then + else
        assertEquals(2, graphData.edges().size()); // Main -> then, Main -> else
        
        NodeData mainNode = findNodeById(graphData.nodes(), "router");
        assertNotNull(mainNode);
        assertEquals("conditional", mainNode.type());
        
        NodeData thenNode = findNodeById(graphData.nodes(), "router_then");
        assertNotNull(thenNode);
        assertEquals("conditional_then", thenNode.type());
        
        NodeData elseNode = findNodeById(graphData.nodes(), "router_else");
        assertNotNull(elseNode);
        assertEquals("conditional_else", elseNode.type());
    }

    @Test
    @DisplayName("Should get specific agent graph by name")
    void testGetAgentGraph_ByName() {
        // Given
        AgentDef agent1 = createAgentDef("agent1", createGraphWorkflow());
        AgentDef agent2 = createAgentDef("agent2", createGraphWorkflow());
        AgentsProperties.setList(Arrays.asList(agent1, agent2));

        // When
        Optional<AgentGraphData> result = service.getAgentGraph("agent2");

        // Then
        assertTrue(result.isPresent());
        assertEquals("agent2", result.get().agentName());
    }

    @Test
    @DisplayName("Should return empty when agent not found")
    void testGetAgentGraph_NotFound() {
        // Given
        AgentDef agent = createAgentDef("agent1", createGraphWorkflow());
        AgentsProperties.setList(Arrays.asList(agent));

        // When
        Optional<AgentGraphData> result = service.getAgentGraph("nonexistent");

        // Then
        assertFalse(result.isPresent());
    }

    @Test
    @DisplayName("Should return empty when agent has no workflow")
    void testGetAgentGraph_NoWorkflow() {
        // Given
        AgentDef agent = createAgentDef("agent1", null);
        AgentsProperties.setList(Arrays.asList(agent));

        // When
        Optional<AgentGraphData> result = service.getAgentGraph("agent1");

        // Then
        assertFalse(result.isPresent());
    }

    @Test
    @DisplayName("Should handle complex dependency patterns")
    void testGetAllAgentGraphs_ComplexDependencies() {
        // Given - Diamond pattern: A -> B, A -> C, B -> D, C -> D
        WorkflowStepDef stepA = createStep("A", "Step A", null, null);
        WorkflowStepDef stepB = createStep("B", "Step B", Arrays.asList("A"), null);
        WorkflowStepDef stepC = createStep("C", "Step C", Arrays.asList("A"), null);
        WorkflowStepDef stepD = createStep("D", "Step D", Arrays.asList("B", "C"), null);
        
        WorkflowDef workflow = new WorkflowDef();
        workflow.setType(WorkflowType.GRAPH);
        workflow.setChain(Arrays.asList(stepA, stepB, stepC, stepD));
        
        AgentDef agent = createAgentDef("complex-agent", workflow);
        AgentsProperties.setList(Arrays.asList(agent));

        // When
        List<AgentGraphData> result = service.getAllAgentGraphs();

        // Then
        assertEquals(1, result.size());
        
        AgentGraphData graphData = result.get(0);
        assertEquals(4, graphData.nodes().size());
        assertEquals(4, graphData.edges().size());
        
        // Verify edges
        assertTrue(hasEdge(graphData.edges(), "A", "B"));
        assertTrue(hasEdge(graphData.edges(), "A", "C"));
        assertTrue(hasEdge(graphData.edges(), "B", "D"));
        assertTrue(hasEdge(graphData.edges(), "C", "D"));
    }

    @Test
    @DisplayName("Should handle workflow with no chain")
    void testGetAllAgentGraphs_NoChain() {
        // Given
        WorkflowDef workflow = new WorkflowDef();
        workflow.setType(WorkflowType.GRAPH);
        workflow.setChain(Collections.emptyList());
        
        AgentDef agent = createAgentDef("empty-agent", workflow);
        AgentsProperties.setList(Arrays.asList(agent));

        // When
        List<AgentGraphData> result = service.getAllAgentGraphs();

        // Then
        assertEquals(1, result.size());
        
        AgentGraphData graphData = result.get(0);
        assertEquals("empty-agent", graphData.agentName());
        assertTrue(graphData.nodes().isEmpty());
        assertTrue(graphData.edges().isEmpty());
    }

    @Test
    @DisplayName("Should correctly identify input_node and output_node types")
    void testNodeTypeIdentification_InputOutputNodes() {
        // Given - Workflow with input_node, regular node, and output_node
        WorkflowStepDef inputNode = createStep("input_node", "Receive user request: {input}", null, null);
        WorkflowStepDef processNode = createStep("process", "Process the request: {input_node}", Arrays.asList("input_node"), null);
        WorkflowStepDef outputNode = createStep("output_node", "Present final result: {process}", Arrays.asList("process"), null);
        
        WorkflowDef workflow = new WorkflowDef();
        workflow.setType(WorkflowType.GRAPH);
        workflow.setChain(Arrays.asList(inputNode, processNode, outputNode));
        
        AgentDef agent = createAgentDef("test-agent", workflow);
        AgentsProperties.setList(Arrays.asList(agent));

        // When
        List<AgentGraphData> result = service.getAllAgentGraphs();

        // Then
        assertEquals(1, result.size());
        
        AgentGraphData graphData = result.get(0);
        assertEquals(3, graphData.nodes().size());
        
        // Verify node types
        NodeData inputNodeData = findNodeById(graphData.nodes(), "input_node");
        assertNotNull(inputNodeData);
        assertEquals("input_node", inputNodeData.type(), "input_node should be identified as 'input_node' type");
        
        NodeData processNodeData = findNodeById(graphData.nodes(), "process");
        assertNotNull(processNodeData);
        assertEquals("prompt", processNodeData.type(), "regular node should be identified as 'prompt' type");
        
        NodeData outputNodeData = findNodeById(graphData.nodes(), "output_node");
        assertNotNull(outputNodeData);
        assertEquals("output_node", outputNodeData.type(), "output_node should be identified as 'output_node' type");
    }

    // Helper methods
    private AgentDef createAgentDef(String name, WorkflowDef workflow) {
        AgentDef agent = new AgentDef();
        agent.setName(name);
        agent.setWorkflow(workflow);
        return agent;
    }

    private WorkflowDef createGraphWorkflow() {
        WorkflowStepDef step = createStep("test_step", "Test prompt", null, null);
        WorkflowDef workflow = new WorkflowDef();
        workflow.setType(WorkflowType.GRAPH);
        workflow.setChain(Arrays.asList(step));
        return workflow;
    }

    private WorkflowStepDef createStep(String nodeId, String prompt, List<String> dependsOn, String tool) {
        WorkflowStepDef step = new WorkflowStepDef();
        step.setNodeId(nodeId);
        step.setPrompt(prompt);
        step.setDependsOn(dependsOn);
        step.setTool(tool);
        return step;
    }

    private NodeData findNodeById(List<NodeData> nodes, String id) {
        return nodes.stream()
                .filter(node -> id.equals(node.id()))
                .findFirst()
                .orElse(null);
    }

    private boolean hasEdge(List<EdgeData> edges, String source, String target) {
        return edges.stream()
                .anyMatch(edge -> source.equals(edge.source()) && target.equals(edge.target()));
    }
}



