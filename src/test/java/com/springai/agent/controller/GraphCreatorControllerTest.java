package com.springai.agent.controller;

import com.springai.agent.config.AppProperties;
import com.springai.agent.controller.GraphCreatorController.GraphDefinition;
import com.springai.agent.controller.GraphCreatorController.NodeDefinition;
import com.springai.agent.controller.GraphCreatorController.ConditionalDefinition;
import com.springai.agent.controller.GraphCreatorController.ValidationResult;
import com.springai.agent.controller.GraphCreatorController.NodeTypeInfo;
import com.springai.agent.controller.GraphCreatorController.GraphTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for GraphCreatorController.
 * Tests interactive graph creation, validation, and YAML generation.
 */
class GraphCreatorControllerTest {

    private GraphCreatorController controller;
    
    @Mock
    private AppProperties appProperties;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        controller = new GraphCreatorController(appProperties);
    }

    @Test
    @DisplayName("Should validate valid graph successfully")
    void testValidateGraph_ValidGraph() {
        // Given
        NodeDefinition node1 = new NodeDefinition("node1", "Process input: {input}", null, null, null);
        NodeDefinition node2 = new NodeDefinition("node2", "Analyze: {node1}", null, Arrays.asList("node1"), null);
        
        GraphDefinition graphDef = new GraphDefinition();
        graphDef.setName("test-graph");
        graphDef.setSystemPrompt("Test system prompt");
        graphDef.setNodes(Arrays.asList(node1, node2));

        // When
        ResponseEntity<ValidationResult> response = controller.validateGraph(graphDef);

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        
        ValidationResult result = response.getBody();
        assertTrue(result.valid());
        assertTrue(result.errors().isEmpty());
        assertEquals("Graph is valid", result.message());
    }

    @Test
    @DisplayName("Should validate graph with warnings")
    void testValidateGraph_WithWarnings() {
        // Given
        NodeDefinition node1 = new NodeDefinition("node1", "Process input: {input}", null, null, null);
        
        GraphDefinition graphDef = new GraphDefinition();
        graphDef.setName("test-graph");
        graphDef.setSystemPrompt(null); // This should generate a warning
        graphDef.setNodes(Arrays.asList(node1));

        // When
        ResponseEntity<ValidationResult> response = controller.validateGraph(graphDef);

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        
        ValidationResult result = response.getBody();
        assertTrue(result.valid());
        assertTrue(result.errors().isEmpty());
        assertEquals(1, result.warnings().size());
        assertTrue(result.warnings().get(0).contains("System prompt is recommended"));
    }

    @Test
    @DisplayName("Should validate graph with errors")
    void testValidateGraph_WithErrors() {
        // Given
        GraphDefinition graphDef = new GraphDefinition();
        graphDef.setName(null); // Missing name - should cause error
        graphDef.setNodes(null); // Missing nodes - should cause error

        // When
        ResponseEntity<ValidationResult> response = controller.validateGraph(graphDef);

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        
        ValidationResult result = response.getBody();
        assertFalse(result.valid());
        assertEquals(2, result.errors().size());
        assertTrue(result.errors().contains("Agent name is required"));
        assertTrue(result.errors().contains("At least one node is required"));
        assertEquals("Graph has validation errors", result.message());
    }

    @Test
    @DisplayName("Should detect duplicate node IDs")
    void testValidateGraph_DuplicateNodeIds() {
        // Given
        NodeDefinition node1 = new NodeDefinition("duplicate", "First node", null, null, null);
        NodeDefinition node2 = new NodeDefinition("duplicate", "Second node", null, null, null);
        
        GraphDefinition graphDef = new GraphDefinition();
        graphDef.setName("test-graph");
        graphDef.setNodes(Arrays.asList(node1, node2));

        // When
        ResponseEntity<ValidationResult> response = controller.validateGraph(graphDef);

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        
        ValidationResult result = response.getBody();
        assertFalse(result.valid());
        assertTrue(result.errors().stream().anyMatch(error -> error.contains("Duplicate node ID: duplicate")));
    }

    @Test
    @DisplayName("Should detect missing node dependencies")
    void testValidateGraph_MissingDependencies() {
        // Given
        NodeDefinition node1 = new NodeDefinition("node1", "Process input", null, Arrays.asList("nonexistent"), null);
        
        GraphDefinition graphDef = new GraphDefinition();
        graphDef.setName("test-graph");
        graphDef.setNodes(Arrays.asList(node1));

        // When
        ResponseEntity<ValidationResult> response = controller.validateGraph(graphDef);

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        
        ValidationResult result = response.getBody();
        assertFalse(result.valid());
        assertTrue(result.errors().stream().anyMatch(error -> 
            error.contains("depends on non-existent node: nonexistent")));
    }

    @Test
    @DisplayName("Should detect circular dependencies")
    void testValidateGraph_CircularDependencies() {
        // Given
        NodeDefinition node1 = new NodeDefinition("node1", "First node", null, Arrays.asList("node2"), null);
        NodeDefinition node2 = new NodeDefinition("node2", "Second node", null, Arrays.asList("node1"), null);
        
        GraphDefinition graphDef = new GraphDefinition();
        graphDef.setName("test-graph");
        graphDef.setNodes(Arrays.asList(node1, node2));

        // When
        ResponseEntity<ValidationResult> response = controller.validateGraph(graphDef);

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        
        ValidationResult result = response.getBody();
        assertFalse(result.valid());
        assertTrue(result.errors().stream().anyMatch(error -> 
            error.contains("circular dependencies")));
    }

    @Test
    @DisplayName("Should validate nodes without content")
    void testValidateGraph_NodesWithoutContent() {
        // Given
        NodeDefinition emptyNode = new NodeDefinition("empty", null, null, null, null);
        
        GraphDefinition graphDef = new GraphDefinition();
        graphDef.setName("test-graph");
        graphDef.setNodes(Arrays.asList(emptyNode));

        // When
        ResponseEntity<ValidationResult> response = controller.validateGraph(graphDef);

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        
        ValidationResult result = response.getBody();
        assertFalse(result.valid());
        assertTrue(result.errors().stream().anyMatch(error -> 
            error.contains("must have either a prompt, tool, or conditional")));
    }

    @Test
    @DisplayName("Should generate YAML for valid graph")
    void testGenerateYaml_ValidGraph() {
        // Given
        NodeDefinition node1 = new NodeDefinition("node1", "Process input: {input}", null, null, null);
        NodeDefinition node2 = new NodeDefinition("node2", "Analyze: {node1}", null, Arrays.asList("node1"), null);
        
        GraphDefinition graphDef = new GraphDefinition();
        graphDef.setName("test-agent");
        graphDef.setSystemPrompt("Test system prompt");
        graphDef.setNodes(Arrays.asList(node1, node2));

        // When
        ResponseEntity<String> response = controller.generateYaml(graphDef);

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        
        String yaml = response.getBody();
        assertTrue(yaml.contains("agents:"));
        assertTrue(yaml.contains("list:"));
        assertTrue(yaml.contains("name: test-agent"));
        assertTrue(yaml.contains("systemPrompt: Test system prompt"));
        assertTrue(yaml.contains("type: graph"));
        assertTrue(yaml.contains("nodeId: node1"));
        assertTrue(yaml.contains("nodeId: node2"));
        assertTrue(yaml.contains("dependsOn:"));
        assertTrue(yaml.contains("- node1"));
        
        // Check Content-Disposition header
        assertEquals("attachment; filename=\"test-agent-workflow.yml\"", 
                    response.getHeaders().getFirst("Content-Disposition"));
    }

    @Test
    @DisplayName("Should generate YAML for graph with tool nodes")
    void testGenerateYaml_WithToolNodes() {
        // Given
        NodeDefinition toolNode = new NodeDefinition("search", null, "web_search", null, null);
        NodeDefinition promptNode = new NodeDefinition("analyze", "Analyze: {search}", null, Arrays.asList("search"), null);
        
        GraphDefinition graphDef = new GraphDefinition();
        graphDef.setName("tool-agent");
        graphDef.setNodes(Arrays.asList(toolNode, promptNode));

        // When
        ResponseEntity<String> response = controller.generateYaml(graphDef);

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        
        String yaml = response.getBody();
        assertTrue(yaml.contains("tool: web_search"));
        // Check for prompt content more flexibly (YAML might format quotes differently)
        assertTrue(yaml.contains("prompt:") && yaml.contains("Analyze: {search}"));
    }

    @Test
    @DisplayName("Should handle YAML generation with null values")
    void testGenerateYaml_Error() {
        // Given - Create a graph with null values (this doesn't cause YAML generation errors)
        NodeDefinition invalidNode = new NodeDefinition(null, null, null, null, null); // Node with null values
        
        GraphDefinition graphDef = new GraphDefinition();
        graphDef.setName("invalid-agent");
        graphDef.setNodes(Arrays.asList(invalidNode));

        // When
        ResponseEntity<String> response = controller.generateYaml(graphDef);

        // Then - YAML generation succeeds even with null values
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().contains("invalid-agent"));
    }

    @Test
    @DisplayName("Should return available node types")
    void testGetNodeTypes() {
        // When
        ResponseEntity<List<NodeTypeInfo>> response = controller.getNodeTypes();

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(5, response.getBody().size());
        
        List<NodeTypeInfo> nodeTypes = response.getBody();
        
        // Check input node type
        NodeTypeInfo inputType = nodeTypes.stream()
                .filter(type -> "input".equals(type.type()))
                .findFirst()
                .orElse(null);
        assertNotNull(inputType);
        assertEquals("Input Node", inputType.name());
        assertEquals("Entry point for agent workflow - receives initial data", inputType.description());
        assertTrue(inputType.requiredFields().contains("nodeId"));
        
        // Check output node type
        NodeTypeInfo outputType = nodeTypes.stream()
                .filter(type -> "output".equals(type.type()))
                .findFirst()
                .orElse(null);
        assertNotNull(outputType);
        assertEquals("Output Node", outputType.name());
        assertEquals("Exit point for agent workflow - returns final result", outputType.description());
        assertTrue(outputType.requiredFields().contains("nodeId"));
        assertTrue(outputType.requiredFields().contains("dependsOn"));
        
        // Check prompt node type
        NodeTypeInfo promptType = nodeTypes.stream()
                .filter(type -> "prompt".equals(type.type()))
                .findFirst()
                .orElse(null);
        assertNotNull(promptType);
        assertEquals("Prompt Node", promptType.name());
        assertEquals("Executes an AI prompt", promptType.description());
        assertTrue(promptType.requiredFields().contains("nodeId"));
        assertTrue(promptType.requiredFields().contains("prompt"));
        
        // Check tool node type
        NodeTypeInfo toolType = nodeTypes.stream()
                .filter(type -> "tool".equals(type.type()))
                .findFirst()
                .orElse(null);
        assertNotNull(toolType);
        assertEquals("Tool Node", toolType.name());
        assertTrue(toolType.requiredFields().contains("tool"));
        
        // Check conditional node type
        NodeTypeInfo conditionalType = nodeTypes.stream()
                .filter(type -> "conditional".equals(type.type()))
                .findFirst()
                .orElse(null);
        assertNotNull(conditionalType);
        assertEquals("Conditional Node", conditionalType.name());
        assertTrue(conditionalType.requiredFields().contains("conditional"));
    }

    @Test
    @DisplayName("Should return available templates")
    void testGetTemplates() {
        // When
        ResponseEntity<List<GraphTemplate>> response = controller.getTemplates();

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(4, response.getBody().size());
        
        List<GraphTemplate> templates = response.getBody();
        
        // Check simple chain template
        GraphTemplate simpleChain = templates.stream()
                .filter(template -> "simple-chain".equals(template.id()))
                .findFirst()
                .orElse(null);
        assertNotNull(simpleChain);
        assertEquals("Simple Chain", simpleChain.name());
        assertEquals(3, simpleChain.nodes().size());
        
        // Check parallel processing template
        GraphTemplate parallelProcessing = templates.stream()
                .filter(template -> "parallel-processing".equals(template.id()))
                .findFirst()
                .orElse(null);
        assertNotNull(parallelProcessing);
        assertEquals("Parallel Processing", parallelProcessing.name());
        assertEquals(4, parallelProcessing.nodes().size());
        
        // Check orchestrator template
        GraphTemplate orchestrator = templates.stream()
                .filter(template -> "orchestrator".equals(template.id()))
                .findFirst()
                .orElse(null);
        assertNotNull(orchestrator);
        assertEquals("Orchestrator Pattern", orchestrator.name());
        assertEquals(4, orchestrator.nodes().size());
        
        // Check conditional template
        GraphTemplate conditional = templates.stream()
                .filter(template -> "conditional".equals(template.id()))
                .findFirst()
                .orElse(null);
        assertNotNull(conditional);
        assertEquals("Conditional Logic", conditional.name());
        assertEquals(2, conditional.nodes().size());
        
        // Verify conditional template has conditional node
        NodeDefinition conditionalNode = conditional.nodes().stream()
                .filter(node -> node.getConditional() != null)
                .findFirst()
                .orElse(null);
        assertNotNull(conditionalNode);
        assertNotNull(conditionalNode.getConditional());
        assertEquals("input", conditionalNode.getConditional().getField());
        assertEquals("urgent", conditionalNode.getConditional().getValue());
        assertEquals("CONTAINS", conditionalNode.getConditional().getType());
    }

    @Test
    @DisplayName("Should validate graph with empty node ID")
    void testValidateGraph_EmptyNodeId() {
        // Given
        NodeDefinition nodeWithEmptyId = new NodeDefinition("", "Some prompt", null, null, null);
        
        GraphDefinition graphDef = new GraphDefinition();
        graphDef.setName("test-graph");
        graphDef.setNodes(Arrays.asList(nodeWithEmptyId));

        // When
        ResponseEntity<ValidationResult> response = controller.validateGraph(graphDef);

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        
        ValidationResult result = response.getBody();
        assertFalse(result.valid());
        assertTrue(result.errors().stream().anyMatch(error -> 
            error.contains("Node ID is required for all nodes")));
    }

    @Test
    @DisplayName("Should warn about disconnected nodes")
    void testValidateGraph_DisconnectedNodes() {
        // Given - All nodes have dependencies, no root nodes
        NodeDefinition node1 = new NodeDefinition("node1", "First", null, Arrays.asList("node2"), null);
        NodeDefinition node2 = new NodeDefinition("node2", "Second", null, Arrays.asList("node3"), null);
        NodeDefinition node3 = new NodeDefinition("node3", "Third", null, Arrays.asList("node1"), null); // Creates cycle
        
        GraphDefinition graphDef = new GraphDefinition();
        graphDef.setName("test-graph");
        graphDef.setNodes(Arrays.asList(node1, node2, node3));

        // When
        ResponseEntity<ValidationResult> response = controller.validateGraph(graphDef);

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        
        ValidationResult result = response.getBody();
        assertFalse(result.valid()); // Should be invalid due to cycle
        assertTrue(result.errors().stream().anyMatch(error -> 
            error.contains("circular dependencies")));
    }

    @Test
    @DisplayName("Should handle graph with valid tool and prompt combinations")
    void testValidateGraph_ValidToolAndPromptCombinations() {
        // Given
        NodeDefinition toolNode = new NodeDefinition("tool1", null, "search_tool", null, null);
        NodeDefinition promptNode = new NodeDefinition("prompt1", "Analyze results", null, null, null);
        NodeDefinition combinedNode = new NodeDefinition("combined1", "Process: {input}", "analysis_tool", null, null);
        
        GraphDefinition graphDef = new GraphDefinition();
        graphDef.setName("test-graph");
        graphDef.setNodes(Arrays.asList(toolNode, promptNode, combinedNode));

        // When
        ResponseEntity<ValidationResult> response = controller.validateGraph(graphDef);

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        
        ValidationResult result = response.getBody();
        assertTrue(result.valid());
        assertTrue(result.errors().isEmpty());
    }

    @Test
    @DisplayName("Should handle complex dependency validation")
    void testValidateGraph_ComplexDependencies() {
        // Given - Diamond pattern: A -> B, A -> C, B -> D, C -> D
        NodeDefinition nodeA = new NodeDefinition("A", "Node A", null, null, null);
        NodeDefinition nodeB = new NodeDefinition("B", "Node B", null, Arrays.asList("A"), null);
        NodeDefinition nodeC = new NodeDefinition("C", "Node C", null, Arrays.asList("A"), null);
        NodeDefinition nodeD = new NodeDefinition("D", "Node D", null, Arrays.asList("B", "C"), null);
        
        GraphDefinition graphDef = new GraphDefinition();
        graphDef.setName("complex-graph");
        graphDef.setNodes(Arrays.asList(nodeA, nodeB, nodeC, nodeD));

        // When
        ResponseEntity<ValidationResult> response = controller.validateGraph(graphDef);

        // Then
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        
        ValidationResult result = response.getBody();
        assertTrue(result.valid());
        assertTrue(result.errors().isEmpty());
    }
}

