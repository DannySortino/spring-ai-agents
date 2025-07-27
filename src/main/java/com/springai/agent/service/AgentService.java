package com.springai.agent.service;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import com.springai.agent.workflow.Workflow;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Core service class for managing individual AI agents.
 * <p>
 * This service is responsible for:
 * - Executing agent workflows with user input
 * - Managing persistent context across invocations
 * - Maintaining conversation history
 * - Handling system prompts and initialization
 * - Providing context manipulation methods
 * <p>
 * Each AgentService instance represents a single configured AI agent with its
 * own workflow, system prompt, and persistent state. The service maintains
 * thread-safe context storage and automatically manages conversation history.
 * 
 * @author Danny Sortino
 * @since 1.0.0
 */
@RequiredArgsConstructor
@Getter
public class AgentService {
    private final String name;
    private final String systemPrompt;
    private final Workflow workflow;
    private final Map<String, Object> persistentContext = new ConcurrentHashMap<>();
    private boolean isInitialized = false;
    
    public String invoke(String input) {
        return invoke(input, Map.of());
    }
    
    public String invoke(String input, Map<String, Object> additionalContext) {
        // Create context for this invocation
        Map<String, Object> context = createExecutionContext(input, additionalContext);
        
        // If this is the first invocation, include system prompt in the context
        if (!isInitialized && systemPrompt != null && !systemPrompt.isEmpty()) {
            context.put("systemPrompt", systemPrompt);
            context.put("isFirstInvocation", true);
            isInitialized = true;
        }
        
        // Execute workflow with context
        String result = workflow.execute(input, context);
        
        // Update persistent context with any results that should be remembered
        updatePersistentContext(input, result, context);
        
        return result;
    }
    
    private Map<String, Object> createExecutionContext(String input, Map<String, Object> additionalContext) {

        // Add persistent context
        Map<String, Object> context = new HashMap<>(persistentContext);
        
        // Add agent metadata
        context.put("agentName", name);
        context.put("timestamp", System.currentTimeMillis());
        context.put("currentInput", input);
        
        // Add any additional context provided
        context.putAll(additionalContext);
        
        return context;
    }
    
    private void updatePersistentContext(String input, String result, Map<String, Object> context) {
        // Store conversation history (keep last 5 interactions)
        @SuppressWarnings("unchecked")
        Map<String, String> history = (Map<String, String>) persistentContext.computeIfAbsent("conversationHistory", k -> new HashMap<String, String>());
        
        // Simple history management - in a real implementation, this could be more sophisticated
        String timestamp = String.valueOf(System.currentTimeMillis());
        history.put(timestamp + "_input", input);
        history.put(timestamp + "_output", result);
        
        // Keep only recent history to prevent memory bloat
        if (history.size() > 10) { // 5 input/output pairs
            String oldestKey = history.keySet().stream().min(String::compareTo).orElse(null);
            history.remove(oldestKey);
            // Remove corresponding input/output pair
            String correspondingKey = oldestKey.replace("_input", "_output").replace("_output", "_input");
            history.remove(correspondingKey);
        }
        
        // Update invocation count
        persistentContext.put("invocationCount", (Integer) persistentContext.getOrDefault("invocationCount", 0) + 1);
    }
    
    public void addToPersistentContext(String key, Object value) {
        persistentContext.put(key, value);
    }
    
    public Object getFromPersistentContext(String key) {
        return persistentContext.get(key);
    }
    
    public void clearPersistentContext() {
        persistentContext.clear();
        isInitialized = false;
    }
    
    public String getDescription() {
        if (systemPrompt != null && !systemPrompt.isEmpty()) {
            return "Agent '" + name + "': " + systemPrompt;
        }
        return "Agent '" + name + "': A general-purpose AI agent";
    }
}
