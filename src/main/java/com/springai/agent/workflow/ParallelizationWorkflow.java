package com.springai.agent.workflow;

import com.springai.agent.workflow.dependency.DependencyManager;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 * Workflow implementation that executes multiple child workflows in parallel.
 * 
 * This workflow enables concurrent execution of multiple sub-workflows, allowing
 * for improved performance when tasks can be processed independently. Key features:
 * 
 * - Parallel execution of child workflows using CompletableFuture
 * - Configurable thread pool management with ExecutorService
 * - Result aggregation using customizable aggregator prompts
 * - Builder pattern for flexible workflow configuration
 * - Automatic result collection and synchronization
 * 
 * The workflow executes all child workflows concurrently with the same input and
 * context, then aggregates their results. If an aggregator prompt is provided,
 * the results are processed through the chat model for intelligent synthesis.
 * Otherwise, results are simply concatenated.
 * 
 * Example usage:
 * <pre>
 * ParallelizationWorkflow workflow = new ParallelizationWorkflow(chatModel)
 *     .parallel(childWorkflows, "Synthesize these results: {results}");
 * </pre>
 * 
 * @author Spring AI Agent Team
 * @since 1.0.0
 */
public class ParallelizationWorkflow implements Workflow {
    private final ChatModel chatModel;
    private final List<Workflow> childWorkflows;
    private final String aggregatorPrompt;
    private final ExecutorService executorService;
    private final DependencyManager dependencyManager;
    
    // Maps to store workflow IDs and dependencies
    private final Map<String, Workflow> workflowsById;
    private final Map<String, List<String>> dependencies;
    
    public ParallelizationWorkflow(ChatModel chatModel) {
        this.chatModel = chatModel;
        this.childWorkflows = List.of();
        this.aggregatorPrompt = "";
        this.executorService = Executors.newCachedThreadPool();
        this.dependencyManager = new DependencyManager();
        this.workflowsById = new HashMap<>();
        this.dependencies = new HashMap<>();
    }
    
    /**
     * Creates a new ParallelizationWorkflow with the specified child workflows and aggregator prompt.
     * Child workflows will be executed in parallel if they have no dependencies.
     * 
     * @param workflows List of child workflows to execute
     * @param aggregatorPrompt Prompt to use for aggregating results
     * @return A new ParallelizationWorkflow instance
     */
    public ParallelizationWorkflow parallel(List<Workflow> workflows, String aggregatorPrompt) {
        return new ParallelizationWorkflow(chatModel, workflows, aggregatorPrompt, executorService);
    }
    
    /**
     * Creates a new ParallelizationWorkflow with named child workflows and dependencies.
     * This allows defining dependencies between workflows.
     * 
     * @param workflowsById Map of workflow IDs to workflow instances
     * @param dependencies Map of workflow IDs to their dependency IDs
     * @param aggregatorPrompt Prompt to use for aggregating results
     * @return A new ParallelizationWorkflow instance
     */
    public ParallelizationWorkflow parallelWithDependencies(
            Map<String, Workflow> workflowsById,
            Map<String, List<String>> dependencies,
            String aggregatorPrompt) {
        return new ParallelizationWorkflow(chatModel, workflowsById, dependencies, aggregatorPrompt, executorService);
    }
    
    private ParallelizationWorkflow(ChatModel chatModel, List<Workflow> childWorkflows, String aggregatorPrompt, ExecutorService executorService) {
        this.chatModel = chatModel;
        this.childWorkflows = childWorkflows;
        this.aggregatorPrompt = aggregatorPrompt;
        this.executorService = executorService;
        this.dependencyManager = new DependencyManager();
        this.workflowsById = new HashMap<>();
        this.dependencies = new HashMap<>();
        
        // Generate IDs for workflows without explicit dependencies
        for (int i = 0; i < childWorkflows.size(); i++) {
            workflowsById.put("workflow-" + i, childWorkflows.get(i));
        }
    }
    
    private ParallelizationWorkflow(ChatModel chatModel, Map<String, Workflow> workflowsById, 
                                   Map<String, List<String>> dependencies, String aggregatorPrompt, 
                                   ExecutorService executorService) {
        this.chatModel = chatModel;
        this.workflowsById = workflowsById;
        this.dependencies = dependencies;
        this.childWorkflows = new ArrayList<>(workflowsById.values());
        this.aggregatorPrompt = aggregatorPrompt;
        this.executorService = executorService;
        this.dependencyManager = new DependencyManager();
        
        // Validate dependencies
        validateDependencies();
    }
    
    /**
     * Validates that all dependencies are correctly configured.
     * Throws an IllegalArgumentException if there are any validation errors.
     */
    private void validateDependencies() {
        if (workflowsById.isEmpty() || dependencies.isEmpty()) {
            return;
        }
        
        // Validate dependencies
        DependencyManager.ValidationResult result = dependencyManager.validateDependencies(
            workflowsById.keySet(), dependencies);
        if (!result.isValid()) {
            throw new IllegalArgumentException("Dependency validation failed: " + result.getErrorMessage());
        }
    }
    
    @Override
    public String execute(String input, Map<String, Object> context) {
        // Create a map to store workflow results by ID
        Map<String, String> workflowResults = new HashMap<>();
        
        // Create a context map for each workflow to avoid interference
        Map<String, Object> sharedContext = new HashMap<>(context);
        
        // If we have dependencies, execute workflows in dependency order
        if (!dependencies.isEmpty()) {
            try {
                // Get topological sort of workflows (respects dependencies)
                List<String> executionOrder = dependencyManager.getTopologicalOrder(dependencies);
                
                // Execute workflows in dependency order
                for (String workflowId : executionOrder) {
                    Workflow workflow = workflowsById.get(workflowId);
                    if (workflow == null) {
                        continue;
                    }
                    
                    // Process dependencies for this workflow
                    processDependencies(workflowId, sharedContext, workflowResults);
                    
                    // Execute the workflow
                    String result = workflow.execute(input, sharedContext);
                    
                    // Store the result
                    workflowResults.put(workflowId, result);
                }
            } catch (IllegalArgumentException e) {
                // If there's a cycle in the dependencies, fall back to parallel execution
                return executeAllInParallel(input, context);
            }
        } else {
            // No dependencies, execute all in parallel
            return executeAllInParallel(input, context);
        }
        
        // Collect all results in the original order
        List<String> results = new ArrayList<>();
        for (String workflowId : workflowsById.keySet()) {
            String result = workflowResults.get(workflowId);
            if (result != null) {
                results.add(result);
            }
        }
        
        // Aggregate results using the aggregator prompt
        if (aggregatorPrompt != null && !aggregatorPrompt.isEmpty()) {
            String combinedResults = String.join("\n\n", results);
            String processedPrompt = aggregatorPrompt.replace("{results}", combinedResults);
            
            Prompt prompt = new Prompt(processedPrompt);
            return chatModel.call(prompt).getResult().getOutput().getText();
        }
        
        // If no aggregator, just return combined results
        return String.join("\n\n", results);
    }
    
    /**
     * Executes all workflows in parallel without considering dependencies.
     * This is used as a fallback when there are no dependencies or when there's a cycle.
     */
    private String executeAllInParallel(String input, Map<String, Object> context) {
        // Execute all child workflows in parallel
        List<CompletableFuture<String>> futures = childWorkflows.stream()
            .map(workflow -> CompletableFuture.supplyAsync(
                () -> workflow.execute(input, context), 
                executorService
            ))
            .toList();
        
        // Wait for all to complete and collect results
        List<String> results = futures.stream()
            .map(CompletableFuture::join)
            .collect(Collectors.toList());
        
        // Aggregate results using the aggregator prompt
        if (aggregatorPrompt != null && !aggregatorPrompt.isEmpty()) {
            String combinedResults = String.join("\n\n", results);
            String processedPrompt = aggregatorPrompt.replace("{results}", combinedResults);
            
            Prompt prompt = new Prompt(processedPrompt);
            return chatModel.call(prompt).getResult().getOutput().getText();
        }
        
        // If no aggregator, just return combined results
        return String.join("\n\n", results);
    }
    
    /**
     * Processes dependencies for a workflow by making the results of its dependencies
     * available in the context.
     */
    private void processDependencies(String workflowId, Map<String, Object> context, Map<String, String> workflowResults) {
        List<String> workflowDependencies = dependencies.getOrDefault(workflowId, List.of());
        if (workflowDependencies.isEmpty()) {
            return;
        }
        
        // Create a map of dependency results
        Map<String, Object> dependencyResults = new HashMap<>();
        for (String dependencyId : workflowDependencies) {
            String result = workflowResults.get(dependencyId);
            if (result != null) {
                dependencyResults.put(dependencyId, result);
            }
        }
        
        // Add dependency results to the context
        context.put("dependencyResults", dependencyResults);
    }
}
