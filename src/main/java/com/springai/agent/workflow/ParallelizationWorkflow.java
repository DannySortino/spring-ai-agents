package com.springai.agent.workflow;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;

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
    
    public ParallelizationWorkflow(ChatModel chatModel) {
        this.chatModel = chatModel;
        this.childWorkflows = List.of();
        this.aggregatorPrompt = "";
        this.executorService = Executors.newCachedThreadPool();
    }
    
    public ParallelizationWorkflow parallel(List<Workflow> workflows, String aggregatorPrompt) {
        return new ParallelizationWorkflow(chatModel, workflows, aggregatorPrompt, executorService);
    }
    
    private ParallelizationWorkflow(ChatModel chatModel, List<Workflow> childWorkflows, String aggregatorPrompt, ExecutorService executorService) {
        this.chatModel = chatModel;
        this.childWorkflows = childWorkflows;
        this.aggregatorPrompt = aggregatorPrompt;
        this.executorService = executorService;
    }
    
    @Override
    public String execute(String input, Map<String, Object> context) {
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
}
