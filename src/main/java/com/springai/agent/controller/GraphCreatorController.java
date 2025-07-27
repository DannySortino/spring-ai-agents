package com.springai.agent.controller;

import com.springai.agent.config.AppProperties;
import com.springai.agent.config.WorkflowType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import lombok.Getter;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * REST controller for interactive graph creation web interface.
 * Provides APIs for creating, validating, and generating YAML configurations.
 * 
 * @author Danny Sortino
 * @since 1.0.0
 */
@RestController
@RequestMapping("${spring.ai.agents.visualization.base-path:/visualization}/creator")
@CrossOrigin(origins = "*")
public class GraphCreatorController {
    
    private static final Logger logger = LoggerFactory.getLogger(GraphCreatorController.class);

    private final ObjectMapper yamlMapper;
    
    public GraphCreatorController(AppProperties appProperties) {
        this.yamlMapper = new ObjectMapper(new YAMLFactory()
            .disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER)
            .enable(YAMLGenerator.Feature.MINIMIZE_QUOTES)
            .enable(YAMLGenerator.Feature.INDENT_ARRAYS_WITH_INDICATOR));
    }
    
    /**
     * Validate a graph configuration.
     */
    @PostMapping("/validate")
    public ResponseEntity<ValidationResult> validateGraph(@RequestBody GraphDefinition graphDef) {
        logger.debug("Validating graph: {}", graphDef.getName());
        
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        
        // Basic validation
        if (graphDef.getName() == null || graphDef.getName().trim().isEmpty()) {
            errors.add("Agent name is required");
        }
        
        if (graphDef.getSystemPrompt() == null || graphDef.getSystemPrompt().trim().isEmpty()) {
            warnings.add("System prompt is recommended");
        }
        
        if (graphDef.getNodes() == null || graphDef.getNodes().isEmpty()) {
            errors.add("At least one node is required");
        } else {
            validateNodes(graphDef.getNodes(), errors, warnings);
        }
        
        ValidationResult result = new ValidationResult(
            errors.isEmpty(),
            errors,
            warnings,
            errors.isEmpty() ? "Graph is valid" : "Graph has validation errors"
        );
        
        return ResponseEntity.ok(result);
    }
    
    /**
     * Generate YAML configuration from graph definition.
     */
    @PostMapping("/generate-yaml")
    public ResponseEntity<String> generateYaml(@RequestBody GraphDefinition graphDef) {
        logger.debug("Generating YAML for graph: {}", graphDef.getName());
        
        try {
            // Convert to AppProperties structure
            AppProperties.AgentDef agentDef = convertToAgentDef(graphDef);
            
            // Create a wrapper structure for YAML generation
            Map<String, Object> yamlStructure = new LinkedHashMap<>();
            Map<String, Object> springConfig = new LinkedHashMap<>();
            Map<String, Object> aiConfig = new LinkedHashMap<>();
            
            List<Map<String, Object>> agents = new ArrayList<>();
            agents.add(convertAgentToMap(agentDef));
            
            aiConfig.put("agents", agents);
            springConfig.put("ai", aiConfig);
            yamlStructure.put("spring", springConfig);
            
            String yaml = yamlMapper.writeValueAsString(yamlStructure);
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.TEXT_PLAIN);
            headers.set("Content-Disposition", "attachment; filename=\"" + graphDef.getName() + "-workflow.yml\"");
            
            return ResponseEntity.ok()
                    .headers(headers)
                    .body(yaml);
                    
        } catch (Exception e) {
            logger.error("Error generating YAML", e);
            return ResponseEntity.internalServerError()
                    .body("Error generating YAML: " + e.getMessage());
        }
    }
    
    /**
     * Get available node types and their properties.
     */
    @GetMapping("/node-types")
    public ResponseEntity<List<NodeTypeInfo>> getNodeTypes() {
        List<NodeTypeInfo> nodeTypes = Arrays.asList(
            new NodeTypeInfo("input", "Input Node", "Entry point for agent workflow - receives initial data", 
                Arrays.asList("nodeId", "description"), 
                Arrays.asList("validation", "schema")),
            new NodeTypeInfo("output", "Output Node", "Exit point for agent workflow - returns final result", 
                Arrays.asList("nodeId", "description", "dependsOn"), 
                Arrays.asList("format", "validation")),
            new NodeTypeInfo("prompt", "Prompt Node", "Executes an AI prompt", 
                Arrays.asList("nodeId", "prompt", "dependsOn"), 
                Arrays.asList("retry", "contextManagement")),
            new NodeTypeInfo("tool", "Tool Node", "Calls an MCP tool", 
                Arrays.asList("nodeId", "tool", "dependsOn"), 
                Arrays.asList("retry", "contextManagement")),
            new NodeTypeInfo("conditional", "Conditional Node", "Executes conditional logic", 
                Arrays.asList("nodeId", "conditional", "dependsOn"), 
                Arrays.asList("retry", "contextManagement"))
        );
        
        return ResponseEntity.ok(nodeTypes);
    }
    
    /**
     * Get example graph templates.
     */
    @GetMapping("/templates")
    public ResponseEntity<List<GraphTemplate>> getTemplates() {
        List<GraphTemplate> templates = Arrays.asList(
            createSimpleChainTemplate(),
            createParallelProcessingTemplate(),
            createOrchestratorTemplate(),
            createConditionalTemplate()
        );
        
        return ResponseEntity.ok(templates);
    }
    
    /**
     * Validate individual nodes.
     */
    private void validateNodes(List<NodeDefinition> nodes, List<String> errors, List<String> warnings) {
        Set<String> nodeIds = new HashSet<>();
        Set<String> allNodeIds = nodes.stream()
                .map(NodeDefinition::getNodeId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        
        for (NodeDefinition node : nodes) {
            // Check for duplicate node IDs
            if (node.getNodeId() == null || node.getNodeId().trim().isEmpty()) {
                errors.add("Node ID is required for all nodes");
            } else if (!nodeIds.add(node.getNodeId())) {
                errors.add("Duplicate node ID: " + node.getNodeId());
            }
            
            // Check node content
            if ((node.getPrompt() == null || node.getPrompt().trim().isEmpty()) &&
                (node.getTool() == null || node.getTool().trim().isEmpty()) &&
                node.getConditional() == null) {
                errors.add("Node " + node.getNodeId() + " must have either a prompt, tool, or conditional");
            }
            
            // Check dependencies
            if (node.getDependsOn() != null) {
                for (String dep : node.getDependsOn()) {
                    if (!allNodeIds.contains(dep)) {
                        errors.add("Node " + node.getNodeId() + " depends on non-existent node: " + dep);
                    }
                }
            }
        }
        
        // Check for cycles (simplified check)
        if (hasCycles(nodes)) {
            errors.add("Graph contains circular dependencies");
        }
        
        // Check for disconnected nodes
        List<String> rootNodes = nodes.stream()
                .filter(node -> node.getDependsOn() == null || node.getDependsOn().isEmpty())
                .map(NodeDefinition::getNodeId)
                .toList();
        
        if (rootNodes.isEmpty() && !nodes.isEmpty()) {
            warnings.add("No root nodes found - all nodes have dependencies");
        }
    }
    
    /**
     * Simple cycle detection.
     */
    private boolean hasCycles(List<NodeDefinition> nodes) {
        // Skip cycle detection if there are duplicate node IDs (will be caught by duplicate ID validation)
        Set<String> nodeIds = new HashSet<>();
        for (NodeDefinition node : nodes) {
            if (node.getNodeId() != null && !nodeIds.add(node.getNodeId())) {
                return false; // Skip cycle detection when duplicates exist
            }
        }
        
        Map<String, List<String>> graph = nodes.stream()
                .collect(Collectors.toMap(
                    NodeDefinition::getNodeId,
                    node -> node.getDependsOn() != null ? node.getDependsOn() : Collections.emptyList()
                ));
        
        Set<String> visited = new HashSet<>();
        Set<String> recursionStack = new HashSet<>();
        
        for (String node : graph.keySet()) {
            if (hasCycleDFS(node, graph, visited, recursionStack)) {
                return true;
            }
        }
        
        return false;
    }
    
    private boolean hasCycleDFS(String node, Map<String, List<String>> graph, 
                               Set<String> visited, Set<String> recursionStack) {
        if (recursionStack.contains(node)) {
            return true;
        }
        
        if (visited.contains(node)) {
            return false;
        }
        
        visited.add(node);
        recursionStack.add(node);
        
        List<String> dependencies = graph.get(node);
        if (dependencies != null) {
            for (String dep : dependencies) {
                if (hasCycleDFS(dep, graph, visited, recursionStack)) {
                    return true;
                }
            }
        }
        
        recursionStack.remove(node);
        return false;
    }
    
    /**
     * Convert GraphDefinition to AgentDef.
     */
    private AppProperties.AgentDef convertToAgentDef(GraphDefinition graphDef) {
        AppProperties.WorkflowDef workflow = new AppProperties.WorkflowDef();
        workflow.setType(WorkflowType.GRAPH);
        workflow.setChain(graphDef.getNodes().stream()
                .map(this::convertToWorkflowStepDef)
                .collect(Collectors.toList()));
        
        AppProperties.AgentDef agentDef = new AppProperties.AgentDef();
        agentDef.setName(graphDef.getName());
        agentDef.setSystemPrompt(graphDef.getSystemPrompt());
        agentDef.setWorkflow(workflow);
        
        return agentDef;
    }
    
    /**
     * Convert NodeDefinition to WorkflowStepDef.
     */
    private AppProperties.WorkflowStepDef convertToWorkflowStepDef(NodeDefinition node) {
        AppProperties.WorkflowStepDef stepDef = new AppProperties.WorkflowStepDef();
        stepDef.setNodeId(node.getNodeId());
        stepDef.setPrompt(node.getPrompt());
        stepDef.setTool(node.getTool());
        stepDef.setDependsOn(node.getDependsOn());
        
        return stepDef;
    }
    
    /**
     * Convert AgentDef to Map for YAML generation.
     */
    private Map<String, Object> convertAgentToMap(AppProperties.AgentDef agentDef) {
        Map<String, Object> agent = new LinkedHashMap<>();
        agent.put("name", agentDef.getName());
        if (agentDef.getSystemPrompt() != null) {
            agent.put("systemPrompt", agentDef.getSystemPrompt());
        }
        
        Map<String, Object> workflow = new LinkedHashMap<>();
        workflow.put("type", "graph");
        
        List<Map<String, Object>> chain = agentDef.getWorkflow().getChain().stream()
                .map(this::convertStepToMap)
                .collect(Collectors.toList());
        workflow.put("chain", chain);
        
        agent.put("workflow", workflow);
        return agent;
    }
    
    /**
     * Convert WorkflowStepDef to Map.
     */
    private Map<String, Object> convertStepToMap(AppProperties.WorkflowStepDef step) {
        Map<String, Object> stepMap = new LinkedHashMap<>();
        stepMap.put("nodeId", step.getNodeId());
        
        if (step.getPrompt() != null) {
            stepMap.put("prompt", step.getPrompt());
        }
        if (step.getTool() != null) {
            stepMap.put("tool", step.getTool());
        }
        if (step.getDependsOn() != null && !step.getDependsOn().isEmpty()) {
            stepMap.put("dependsOn", step.getDependsOn());
        }
        
        return stepMap;
    }
    
    /**
     * Create template examples.
     */
    private GraphTemplate createSimpleChainTemplate() {
        List<NodeDefinition> nodes = Arrays.asList(
            new NodeDefinition("step1", "Process input: {input}", null, null, null),
            new NodeDefinition("step2", "Analyze result: {step1}", null, List.of("step1"), null),
            new NodeDefinition("step3", "Generate final output: {step2}", null, List.of("step2"), null)
        );
        
        return new GraphTemplate("simple-chain", "Simple Chain", 
                "A basic sequential workflow with three steps", nodes);
    }
    
    private GraphTemplate createParallelProcessingTemplate() {
        List<NodeDefinition> nodes = Arrays.asList(
            new NodeDefinition("input", "Process input: {input}", null, null, null),
            new NodeDefinition("branch_a", "Process branch A: {input}", null, List.of("input"), null),
            new NodeDefinition("branch_b", "Process branch B: {input}", null, List.of("input"), null),
            new NodeDefinition("merge", "Merge results: A={branch_a}, B={branch_b}", null, 
                    Arrays.asList("branch_a", "branch_b"), null)
        );
        
        return new GraphTemplate("parallel-processing", "Parallel Processing", 
                "Parallel execution with convergence", nodes);
    }
    
    private GraphTemplate createOrchestratorTemplate() {
        List<NodeDefinition> nodes = Arrays.asList(
            new NodeDefinition("manager", "Analyze and coordinate: {input}", null, null, null),
            new NodeDefinition("worker1", "Technical analysis: {manager}", null, List.of("manager"), null),
            new NodeDefinition("worker2", "Business analysis: {manager}", null, List.of("manager"), null),
            new NodeDefinition("synthesizer", "Combine results: Tech={worker1}, Business={worker2}", null, 
                    Arrays.asList("worker1", "worker2"), null)
        );
        
        return new GraphTemplate("orchestrator", "Orchestrator Pattern", 
                "Manager-worker-synthesizer pattern", nodes);
    }
    
    private GraphTemplate createConditionalTemplate() {
        List<NodeDefinition> nodes = Arrays.asList(
            new NodeDefinition("check", null, null, null, 
                new ConditionalDefinition("input", "urgent", "CONTAINS", 
                    "Handle urgent request: {input}", "Handle normal request: {input}")),
            new NodeDefinition("followup", "Follow up on: {check}", null, List.of("check"), null)
        );
        
        return new GraphTemplate("conditional", "Conditional Logic", 
                "Workflow with conditional branching", nodes);
    }
    
    // Data classes
    @Setter
    @Getter
    public static class GraphDefinition {
        private String name;
        private String systemPrompt;
        private List<NodeDefinition> nodes;
        
        // Constructors, getters, setters
        public GraphDefinition() {}

    }
    
    @Setter
    @Getter
    public static class NodeDefinition {
        // Getters and setters
        private String nodeId;
        private String prompt;
        private String tool;
        private List<String> dependsOn;
        private ConditionalDefinition conditional;
        
        public NodeDefinition() {}
        
        public NodeDefinition(String nodeId, String prompt, String tool, List<String> dependsOn, ConditionalDefinition conditional) {
            this.nodeId = nodeId;
            this.prompt = prompt;
            this.tool = tool;
            this.dependsOn = dependsOn;
            this.conditional = conditional;
        }

    }
    
    @Setter
    @Getter
    public static class ConditionalDefinition {
        // Getters and setters
        private String field;
        private String value;
        private String type;
        private String thenPrompt;
        private String elsePrompt;
        
        public ConditionalDefinition() {}
        
        public ConditionalDefinition(String field, String value, String type, String thenPrompt, String elsePrompt) {
            this.field = field;
            this.value = value;
            this.type = type;
            this.thenPrompt = thenPrompt;
            this.elsePrompt = elsePrompt;
        }

    }

    public record ValidationResult(boolean valid, List<String> errors, List<String> warnings, String message) {
    }

    public record NodeTypeInfo(String type, String name, String description, List<String> requiredFields,
                               List<String> optionalFields) {
    }

    public record GraphTemplate(String id, String name, String description, List<NodeDefinition> nodes) {
    }
}