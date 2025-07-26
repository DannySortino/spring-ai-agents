package com.springai.agent.workflow;

import com.springai.agent.config.AppProperties.WorkflowStepDef;
import com.springai.agent.config.AppProperties.ConditionalStepDef;
import com.springai.agent.config.AppProperties.ConditionDef;
import com.springai.agent.config.ConditionType;
import com.springai.agent.service.McpToolService;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Workflow implementation that executes steps based on dependency relationships between nodes.
 * 
 * This workflow treats each step as a node in a directed acyclic graph (DAG), where edges
 * represent data dependencies between nodes. Key features:
 * 
 * - Graph-based execution with arbitrary dependencies (A→B, B→C, A→C)
 * - Topological sorting to determine execution order
 * - Cycle detection to prevent infinite loops
 * - Parallel execution of independent nodes
 * - Result passing between dependent nodes
 * - Extensible dependency management
 * 
 * Each node can depend on multiple other nodes, and the results from all dependencies
 * are passed to the LLM call for that node. This enables complex data flow patterns
 * that aren't possible with simple sequential or parallel workflows.
 * 
 * Example configuration:
 * <pre>
 * workflow:
 *   type: graph
 *   chain:
 *     - nodeId: "A"
 *       prompt: "Analyze input: {input}"
 *     - nodeId: "B" 
 *       dependsOn: ["A"]
 *       tool: "search_tool"
 *     - nodeId: "C"
 *       dependsOn: ["A", "B"]
 *       prompt: "Synthesize results from A: {A} and B: {B}"
 * </pre>
 * 
 * @author Danny Sortino
 * @since 1.0.0
 */
public class GraphWorkflow implements Workflow {
    private final ChatModel chatModel;
    private final List<WorkflowStepDef> steps;
    private final McpToolService mcpToolService;
    private final ExecutorService executorService;
    
    // Graph representation
    private final Map<String, WorkflowStepDef> nodeMap;
    private final Map<String, List<String>> adjacencyList;
    private final Map<String, Set<String>> dependencyMap;
    
    public GraphWorkflow(ChatModel chatModel, List<WorkflowStepDef> steps, McpToolService mcpToolService) {
        this.chatModel = chatModel;
        this.steps = steps != null ? steps : List.of();
        this.mcpToolService = mcpToolService;
        int threadPoolSize = Runtime.getRuntime().availableProcessors(); // Adjust size as needed
        this.executorService = Executors.newFixedThreadPool(threadPoolSize);
        
        // Build graph structures
        this.nodeMap = new HashMap<>();
        this.adjacencyList = new HashMap<>();
        this.dependencyMap = new HashMap<>();
        
        buildGraph();
        validateGraph();
    }
    
    public GraphWorkflow(ChatModel chatModel, List<WorkflowStepDef> steps) {
        this(chatModel, steps, null);
    }
    
    /**
     * Build the graph representation from the workflow steps
     */
    private void buildGraph() {
        // First pass: create nodes and initialize structures
        for (WorkflowStepDef step : steps) {
            String nodeId = step.getNodeId();
            if (nodeId == null || nodeId.isEmpty()) {
                throw new IllegalArgumentException("All steps in a graph workflow must have a nodeId");
            }
            
            if (nodeMap.containsKey(nodeId)) {
                throw new IllegalArgumentException("Duplicate nodeId found: " + nodeId);
            }
            
            nodeMap.put(nodeId, step);
            adjacencyList.put(nodeId, new ArrayList<>());
            dependencyMap.put(nodeId, new HashSet<>());
        }
        
        // Second pass: build edges and dependencies
        for (WorkflowStepDef step : steps) {
            String nodeId = step.getNodeId();
            List<String> dependencies = step.getDependsOn();
            
            if (dependencies != null) {
                for (String dependency : dependencies) {
                    if (!nodeMap.containsKey(dependency)) {
                        throw new IllegalArgumentException("Node " + nodeId + " depends on non-existent node: " + dependency);
                    }
                    
                    // Add edge from dependency to current node
                    adjacencyList.get(dependency).add(nodeId);
                    dependencyMap.get(nodeId).add(dependency);
                }
            }
        }
    }
    
    /**
     * Validate the graph for cycles and other issues
     */
    private void validateGraph() {
        if (hasCycle()) {
            throw new IllegalArgumentException("Workflow graph contains cycles - this would create infinite loops");
        }
    }
    
    /**
     * Check if the graph has cycles using DFS
     */
    private boolean hasCycle() {
        Set<String> visited = new HashSet<>();
        Set<String> recursionStack = new HashSet<>();
        
        for (String node : nodeMap.keySet()) {
            if (hasCycleDFS(node, visited, recursionStack)) {
                return true;
            }
        }
        
        return false;
    }
    
    private boolean hasCycleDFS(String node, Set<String> visited, Set<String> recursionStack) {
        if (recursionStack.contains(node)) {
            return true; // Back edge found - cycle detected
        }
        
        if (visited.contains(node)) {
            return false; // Already processed
        }
        
        visited.add(node);
        recursionStack.add(node);
        
        for (String neighbor : adjacencyList.get(node)) {
            if (hasCycleDFS(neighbor, visited, recursionStack)) {
                return true;
            }
        }
        
        recursionStack.remove(node);
        return false;
    }
    
    /**
     * Perform topological sort to determine execution order
     */
    private List<String> topologicalSort() {
        Map<String, Integer> inDegree = new HashMap<>();
        
        // Initialize in-degree for all nodes
        for (String node : nodeMap.keySet()) {
            inDegree.put(node, dependencyMap.get(node).size());
        }
        
        // Queue for nodes with no dependencies
        Queue<String> queue = new LinkedList<>();
        for (Map.Entry<String, Integer> entry : inDegree.entrySet()) {
            if (entry.getValue() == 0) {
                queue.offer(entry.getKey());
            }
        }
        
        List<String> result = new ArrayList<>();
        
        while (!queue.isEmpty()) {
            String current = queue.poll();
            result.add(current);
            
            // Reduce in-degree for all neighbors
            for (String neighbor : adjacencyList.get(current)) {
                int newInDegree = inDegree.get(neighbor) - 1;
                inDegree.put(neighbor, newInDegree);
                
                if (newInDegree == 0) {
                    queue.offer(neighbor);
                }
            }
        }
        
        if (result.size() != nodeMap.size()) {
            throw new IllegalStateException("Topological sort failed - graph may have cycles");
        }
        
        return result;
    }
    
    @Override
    public String execute(String input, Map<String, Object> context) {
        if (steps.isEmpty()) {
            return input;
        }
        
        // Get execution order through topological sort
        List<String> executionOrder = topologicalSort();
        
        // Store results for each node
        Map<String, String> nodeResults = new ConcurrentHashMap<>();
        
        // Group nodes by their dependency level for parallel execution
        Map<Integer, List<String>> levelGroups = groupNodesByLevel(executionOrder);
        
        // Execute nodes level by level
        for (Map.Entry<Integer, List<String>> levelEntry : levelGroups.entrySet()) {
            List<String> nodesAtLevel = levelEntry.getValue();
            
            if (nodesAtLevel.size() == 1) {
                // Single node - execute directly
                String nodeId = nodesAtLevel.get(0);
                String result = executeNode(nodeId, input, context, nodeResults);
                nodeResults.put(nodeId, result);
            } else {
                // Multiple nodes - execute in parallel
                List<CompletableFuture<Void>> futures = nodesAtLevel.stream()
                    .map(nodeId -> CompletableFuture.runAsync(() -> {
                        String result = executeNode(nodeId, input, context, nodeResults);
                        nodeResults.put(nodeId, result);
                    }, executorService))
                    .collect(Collectors.toList());
                
                // Wait for all nodes at this level to complete
                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
            }
        }
        
        // Return the result from the last executed node
        String lastNodeId = executionOrder.get(executionOrder.size() - 1);
        return nodeResults.get(lastNodeId);
    }
    
    /**
     * Group nodes by their dependency level for parallel execution
     */
    private Map<Integer, List<String>> groupNodesByLevel(List<String> executionOrder) {
        Map<String, Integer> nodeLevel = new HashMap<>();
        Map<Integer, List<String>> levelGroups = new TreeMap<>();
        
        for (String nodeId : executionOrder) {
            int level = 0;
            
            // Find the maximum level of all dependencies
            for (String dependency : dependencyMap.get(nodeId)) {
                level = Math.max(level, nodeLevel.get(dependency) + 1);
            }
            
            nodeLevel.put(nodeId, level);
            levelGroups.computeIfAbsent(level, k -> new ArrayList<>()).add(nodeId);
        }
        
        return levelGroups;
    }
    
    /**
     * Execute a single node with its dependencies
     */
    private String executeNode(String nodeId, String originalInput, Map<String, Object> context, Map<String, String> nodeResults) {
        WorkflowStepDef step = nodeMap.get(nodeId);
        
        // Apply context management before step execution
        applyContextManagementBefore(step, context);
        
        // Prepare input for this node
        String nodeInput = prepareNodeInput(nodeId, originalInput, context, nodeResults);
        
        String result;
        if (step.getConditional() != null) {
            // Conditional step - evaluate condition and execute appropriate branch
            ConditionalStepDef conditional = step.getConditional();
            boolean conditionResult = evaluateCondition(conditional.getCondition(), originalInput, context, nodeResults);
            
            if (conditionResult && conditional.getThenStep() != null) {
                result = executeConditionalStep(conditional.getThenStep(), nodeId, originalInput, context, nodeResults);
            } else if (!conditionResult && conditional.getElseStep() != null) {
                result = executeConditionalStep(conditional.getElseStep(), nodeId, originalInput, context, nodeResults);
            } else {
                // No matching step, return input unchanged
                result = nodeInput;
            }
        } else if (step.getTool() != null && !step.getTool().isEmpty()) {
            // Tool call step
            if (mcpToolService != null) {
                result = mcpToolService.callTool(step.getTool(), nodeInput, context);
            } else {
                result = "Tool call: " + step.getTool() + " (McpToolService not available)";
            }
        } else if (step.getPrompt() != null && !step.getPrompt().isEmpty()) {
            // Prompt step
            String processedPrompt = processPrompt(step.getPrompt(), nodeInput, context, nodeResults, nodeId);
            
            // Add system prompt if this is a root node (no dependencies)
            if (dependencyMap.get(nodeId).isEmpty() && context.containsKey("systemPrompt") && context.get("isFirstInvocation") == Boolean.TRUE) {
                String systemPrompt = (String) context.get("systemPrompt");
                if (systemPrompt != null && !systemPrompt.isEmpty()) {
                    processedPrompt = systemPrompt + "\n\n" + processedPrompt;
                }
            }
            
            Prompt prompt = new Prompt(processedPrompt);
            result = chatModel.call(prompt).getResult().getOutput().getText();
        } else {
            // No valid step type, return input unchanged
            result = nodeInput;
        }
        
        // Store result in context for compatibility with ChainWorkflow behavior
        context.put("previousResult", result);
        
        // Apply context management after step execution
        applyContextManagementAfter(step, context);
        
        return result;
    }
    
    /**
     * Prepare input for a node based on its dependencies
     */
    private String prepareNodeInput(String nodeId, String originalInput, Map<String, Object> context, Map<String, String> nodeResults) {
        Set<String> dependencies = dependencyMap.get(nodeId);
        
        if (dependencies.isEmpty()) {
            // Root node - use original input
            return originalInput;
        } else if (dependencies.size() == 1) {
            // Single dependency - use its result
            String dependency = dependencies.iterator().next();
            return nodeResults.get(dependency);
        } else {
            // Multiple dependencies - combine results
            StringBuilder combined = new StringBuilder();
            for (String dependency : dependencies) {
                if (combined.length() > 0) {
                    combined.append("\n\n");
                }
                combined.append("Result from ").append(dependency).append(": ").append(nodeResults.get(dependency));
            }
            return combined.toString();
        }
    }
    
    /**
     * Process prompt template with dependency results
     */
    private String processPrompt(String prompt, String input, Map<String, Object> context, Map<String, String> nodeResults, String currentNodeId) {
        String processed = prompt.replace("{input}", input);
        
        // Replace placeholders for dependency results
        for (String dependency : dependencyMap.get(currentNodeId)) {
            String placeholder = "{" + dependency + "}";
            String result = nodeResults.get(dependency);
            if (result != null) {
                processed = processed.replace(placeholder, result);
            }
        }
        
        // Replace context variables
        for (Map.Entry<String, Object> entry : context.entrySet()) {
            String placeholder = "{" + entry.getKey() + "}";
            if (entry.getValue() != null) {
                processed = processed.replace(placeholder, entry.getValue().toString());
            }
        }
        
        return processed;
    }
    
    /**
     * Apply context management before step execution
     */
    private void applyContextManagementBefore(WorkflowStepDef step, Map<String, Object> context) {
        if (step.getContextManagement() != null) {
            // If removeKeys is specified, it takes precedence over clearBefore
            if (step.getContextManagement().getRemoveKeys() != null) {
                for (String key : step.getContextManagement().getRemoveKeys()) {
                    context.remove(key);
                }
            } else if (step.getContextManagement().isClearBefore()) {
                clearContextWithPreservation(context, step.getContextManagement().getPreserveKeys());
            }
        }
    }
    
    /**
     * Apply context management after step execution
     */
    private void applyContextManagementAfter(WorkflowStepDef step, Map<String, Object> context) {
        if (step.getContextManagement() != null && step.getContextManagement().isClearAfter()) {
            clearContextWithPreservation(context, step.getContextManagement().getPreserveKeys());
        }
    }
    
    /**
     * Clear context while preserving specified keys
     */
    private void clearContextWithPreservation(Map<String, Object> context, List<String> preserveKeys) {
        if (preserveKeys == null || preserveKeys.isEmpty()) {
            context.clear();
            return;
        }
        
        Map<String, Object> preserved = new HashMap<>();
        for (String key : preserveKeys) {
            if (context.containsKey(key)) {
                preserved.put(key, context.get(key));
            }
        }
        
        context.clear();
        context.putAll(preserved);
    }
    
    /**
     * Evaluate a condition based on the condition definition
     */
    private boolean evaluateCondition(ConditionDef condition, String input, Map<String, Object> context, Map<String, String> nodeResults) {
        if (condition == null) {
            return false;
        }
        
        String fieldValue = getFieldValue(condition.getField(), input, context, nodeResults);
        if (fieldValue == null) {
            fieldValue = "";
        }
        
        switch (condition.getType()) {
            case EQUALS:
                if (condition.isIgnoreCase()) {
                    return fieldValue.equalsIgnoreCase(condition.getValue());
                } else {
                    return fieldValue.equals(condition.getValue());
                }
                
            case CONTAINS:
                if (condition.isIgnoreCase()) {
                    return fieldValue.toLowerCase().contains(condition.getValue().toLowerCase());
                } else {
                    return fieldValue.contains(condition.getValue());
                }
                
            case REGEX:
                try {
                    Pattern pattern = regexCache.computeIfAbsent(condition.getValue(), Pattern::compile);
                    return pattern.matcher(fieldValue).matches();
                } catch (Exception e) {
                    // Log the exception and return false for invalid regex
                    logger.warning("Invalid regex pattern: " + condition.getValue() + ". Exception: " + e.getMessage());
                    return false;
                }
                
            case EXISTS:
                return fieldValue != null && !fieldValue.trim().isEmpty();
                
            case EMPTY:
                return fieldValue == null || fieldValue.trim().isEmpty();
                
            default:
                return false;
        }
    }
    
    /**
     * Get field value from input, context, or node results
     */
    private String getFieldValue(String field, String input, Map<String, Object> context, Map<String, String> nodeResults) {
        if (field == null) {
            return null;
        }
        
        // Handle special fields
        if ("input".equals(field)) {
            return input;
        }
        
        if ("previousResult".equals(field) && context.containsKey("previousResult")) {
            return String.valueOf(context.get("previousResult"));
        }
        
        // Handle context fields (e.g., "context.userId")
        if (field.startsWith("context.")) {
            String contextKey = field.substring(8); // Remove "context." prefix
            Object value = context.get(contextKey);
            return value != null ? String.valueOf(value) : null;
        }
        
        // Handle node result fields (node IDs)
        if (nodeResults.containsKey(field)) {
            return nodeResults.get(field);
        }
        
        // Handle direct context fields
        Object value = context.get(field);
        return value != null ? String.valueOf(value) : null;
    }
    
    /**
     * Execute a conditional step and return the result
     */
    private String executeConditionalStep(WorkflowStepDef conditionalStep, String nodeId, String originalInput, 
                                        Map<String, Object> context, Map<String, String> nodeResults) {
        if (conditionalStep.getPrompt() != null && !conditionalStep.getPrompt().isEmpty()) {
            // Execute prompt step
            String processedPrompt = processPrompt(conditionalStep.getPrompt(), originalInput, context, nodeResults, nodeId);
            Prompt prompt = new Prompt(processedPrompt);
            return chatModel.call(prompt).getResult().getOutput().getText();
        } else if (conditionalStep.getTool() != null && !conditionalStep.getTool().isEmpty()) {
            // Execute tool step
            if (mcpToolService != null) {
                return mcpToolService.callTool(conditionalStep.getTool(), originalInput, context);
            } else {
                return "Tool call: " + conditionalStep.getTool() + " (McpToolService not available)";
            }
        } else {
            // No valid step type, return input unchanged
            return originalInput;
        }
    }
}