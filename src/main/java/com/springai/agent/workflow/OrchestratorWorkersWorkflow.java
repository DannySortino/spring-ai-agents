package com.springai.agent.workflow;

import com.springai.agent.workflow.dependency.DependencyManager;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Workflow implementation that uses a manager-worker pattern with orchestration and synthesis.
 * 
 * This workflow implements a sophisticated three-phase execution pattern:
 * 
 * 1. **Orchestration Phase**: A manager analyzes the input and makes decisions about
 *    task delegation and execution strategy
 * 2. **Execution Phase**: Multiple specialized worker workflows execute their tasks
 *    based on the manager's guidance
 * 3. **Synthesis Phase**: A synthesizer combines and processes all worker results
 *    along with the manager's decisions into a final response
 * 
 * Key features:
 * - Manager-driven task orchestration and delegation
 * - Parallel execution of specialized worker workflows
 * - Intelligent result synthesis combining manager decisions and worker outputs
 * - Context variable substitution in all prompts
 * - Flexible worker configuration through named workflow mapping
 * 
 * This pattern is ideal for complex tasks that benefit from specialized processing
 * by different workers, coordinated by a central manager, and synthesized into
 * a coherent final result.
 * 
 * @author Spring AI Agent Team
 * @since 1.0.0
 */
public class OrchestratorWorkersWorkflow implements Workflow {
    private final ChatModel chatModel;
    private final String managerPrompt;
    private final Map<String, Workflow> workers;
    private final String synthesizerPrompt;
    private final DependencyManager dependencyManager;
    private final Map<String, List<String>> dependencies;
    
    /**
     * Creates a new OrchestratorWorkersWorkflow with the specified manager prompt, workers, and synthesizer prompt.
     * Workers will be executed in parallel without dependencies.
     * 
     * @param chatModel The chat model to use
     * @param managerPrompt The prompt for the manager
     * @param workers Map of worker names to worker workflows
     * @param synthesizerPrompt The prompt for the synthesizer
     */
    public OrchestratorWorkersWorkflow(
            ChatModel chatModel, 
            String managerPrompt, 
            Map<String, Workflow> workers, 
            String synthesizerPrompt) {
        this.chatModel = chatModel;
        this.managerPrompt = managerPrompt;
        this.workers = workers;
        this.synthesizerPrompt = synthesizerPrompt;
        this.dependencyManager = new DependencyManager();
        this.dependencies = new HashMap<>();
    }
    
    /**
     * Creates a new OrchestratorWorkersWorkflow with the specified manager prompt, workers, dependencies, and synthesizer prompt.
     * Workers will be executed in an order that respects their dependencies.
     * 
     * @param chatModel The chat model to use
     * @param managerPrompt The prompt for the manager
     * @param workers Map of worker names to worker workflows
     * @param dependencies Map of worker names to their dependency worker names
     * @param synthesizerPrompt The prompt for the synthesizer
     */
    public OrchestratorWorkersWorkflow(
            ChatModel chatModel, 
            String managerPrompt, 
            Map<String, Workflow> workers, 
            Map<String, List<String>> dependencies,
            String synthesizerPrompt) {
        this.chatModel = chatModel;
        this.managerPrompt = managerPrompt;
        this.workers = workers;
        this.synthesizerPrompt = synthesizerPrompt;
        this.dependencyManager = new DependencyManager();
        this.dependencies = dependencies;
        
        // Validate dependencies
        validateDependencies();
    }
    
    /**
     * Validates that all dependencies are correctly configured.
     * Throws an IllegalArgumentException if there are any validation errors.
     */
    private void validateDependencies() {
        if (workers.isEmpty() || dependencies.isEmpty()) {
            return;
        }
        
        // Validate dependencies
        DependencyManager.ValidationResult result = dependencyManager.validateDependencies(
            workers.keySet(), dependencies);
        if (!result.isValid()) {
            throw new IllegalArgumentException("Dependency validation failed: " + result.getErrorMessage());
        }
    }
    
    @Override
    public String execute(String input, Map<String, Object> context) {
        // First, use the manager to decide which workers to engage and how
        String processedManagerPrompt = processPrompt(managerPrompt, input, context);
        Prompt managerPromptObj = new Prompt(processedManagerPrompt);
        String managerDecision = chatModel.call(managerPromptObj).getResult().getOutput().getText();
        
        // Create a map to store worker results
        Map<String, String> workerResults = new HashMap<>();
        
        // Create a shared context for workers
        Map<String, Object> sharedContext = new HashMap<>(context);
        
        // Add the manager decision to the context
        sharedContext.put("managerDecision", managerDecision);
        
        // If we have dependencies, execute workers in dependency order
        if (!dependencies.isEmpty()) {
            try {
                // Get topological sort of workers (respects dependencies)
                List<String> executionOrder = dependencyManager.getTopologicalOrder(dependencies);
                
                // Execute workers in dependency order
                for (String workerId : executionOrder) {
                    Workflow worker = workers.get(workerId);
                    if (worker == null) {
                        continue;
                    }
                    
                    // Process dependencies for this worker
                    processDependencies(workerId, sharedContext, workerResults);
                    
                    // Execute the worker
                    String result = worker.execute(input, sharedContext);
                    
                    // Store the result
                    workerResults.put(workerId, result);
                }
            } catch (IllegalArgumentException e) {
                // If there's a cycle in the dependencies, fall back to parallel execution
                executeAllWorkers(input, sharedContext, workerResults);
            }
        } else {
            // No dependencies, execute all workers
            executeAllWorkers(input, sharedContext, workerResults);
        }
        
        // Synthesize the results
        String combinedResults = workerResults.entrySet().stream()
            .map(entry -> entry.getKey() + ": " + entry.getValue())
            .collect(Collectors.joining("\n\n"));
        
        String processedSynthesizerPrompt = synthesizerPrompt
            .replace("{managerDecision}", managerDecision)
            .replace("{workerResults}", combinedResults);
        processedSynthesizerPrompt = processPrompt(processedSynthesizerPrompt, input, sharedContext);
        
        Prompt synthesizerPromptObj = new Prompt(processedSynthesizerPrompt);
        return chatModel.call(synthesizerPromptObj).getResult().getOutput().getText();
    }
    
    /**
     * Executes all workers without considering dependencies.
     * This is used when there are no dependencies or when there's a cycle.
     */
    private void executeAllWorkers(String input, Map<String, Object> context, Map<String, String> workerResults) {
        // Execute all workers
        for (Map.Entry<String, Workflow> entry : workers.entrySet()) {
            String workerId = entry.getKey();
            Workflow worker = entry.getValue();
            
            // Execute the worker
            String result = worker.execute(input, context);
            
            // Store the result
            workerResults.put(workerId, result);
        }
    }
    
    /**
     * Processes dependencies for a worker by making the results of its dependencies
     * available in the context.
     */
    private void processDependencies(String workerId, Map<String, Object> context, Map<String, String> workerResults) {
        List<String> workerDependencies = dependencies.getOrDefault(workerId, List.of());
        if (workerDependencies.isEmpty()) {
            return;
        }
        
        // Create a map of dependency results
        Map<String, Object> dependencyResults = new HashMap<>();
        for (String dependencyId : workerDependencies) {
            String result = workerResults.get(dependencyId);
            if (result != null) {
                dependencyResults.put(dependencyId, result);
            }
        }
        
        // Add dependency results to the context
        context.put("dependencyResults", dependencyResults);
    }
    
    private String processPrompt(String prompt, String input, Map<String, Object> context) {
        String processed = prompt.replace("{input}", input);
        
        // Replace context variables
        for (Map.Entry<String, Object> entry : context.entrySet()) {
            processed = processed.replace("{" + entry.getKey() + "}", String.valueOf(entry.getValue()));
        }
        
        return processed;
    }
}
