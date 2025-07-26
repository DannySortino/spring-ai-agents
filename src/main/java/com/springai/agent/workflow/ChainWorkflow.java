package com.springai.agent.workflow;

import com.springai.agent.config.AppProperties.WorkflowStepDef;
import com.springai.agent.config.AppProperties.ConditionalStepDef;
import com.springai.agent.config.AppProperties.ConditionDef;
import com.springai.agent.config.AppProperties.ContextManagementDef;
import com.springai.agent.config.ConditionType;
import com.springai.agent.service.McpToolService;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Workflow implementation that executes steps sequentially in a chain pattern.
 * 
 * This workflow processes input through a series of sequential steps, where each
 * step's output becomes the input for the next step. The workflow supports:
 * 
 * - Sequential execution of workflow steps
 * - Conditional logic with if-then-else branching
 * - MCP tool integration for external tool calls
 * - Prompt processing with context variable substitution
 * - System prompt integration for first-step initialization
 * - Backward compatibility with string-based prompts
 * 
 * Each step can be one of:
 * - A prompt step: Processes input through the chat model
 * - A tool step: Calls an external MCP tool
 * - A conditional step: Evaluates conditions and branches execution
 * 
 * The workflow maintains context throughout execution, allowing steps to access
 * previous results and shared state information.
 * 
 * @author Spring AI Agent Team
 * @since 1.0.0
 */
public class ChainWorkflow implements Workflow {
    private final ChatModel chatModel;
    private final List<WorkflowStepDef> steps;
    private final McpToolService mcpToolService;
    
    // Constructor for WorkflowStepDef-based workflow (preferred)
    public ChainWorkflow(ChatModel chatModel, List<WorkflowStepDef> steps, McpToolService mcpToolService) {
        this.chatModel = chatModel;
        this.steps = steps;
        this.mcpToolService = mcpToolService;
    }
    
    // Backward compatibility constructor for string-based prompts
    public ChainWorkflow(ChatModel chatModel, List<String> prompts) {
        this.chatModel = chatModel;
        this.mcpToolService = null;
        // Convert string prompts to WorkflowStepDef objects
        if (prompts != null) {
            this.steps = prompts.stream()
                .map(prompt -> {
                    WorkflowStepDef step = new WorkflowStepDef();
                    step.setPrompt(prompt);
                    return step;
                })
                .toList();
        } else {
            this.steps = List.of();
        }
    }
    
    @Override
    public String execute(String input, Map<String, Object> context) {
        String currentInput = input;
        boolean isFirstStep = true;
        
        // Create a map to store step results by ID if it doesn't exist
        if (!context.containsKey("stepResults")) {
            context.put("stepResults", new java.util.HashMap<String, Object>());
        }
        Map<String, Object> stepResults = (Map<String, Object>) context.get("stepResults");
        
        for (WorkflowStepDef step : steps) {
            // Store previous result for condition evaluation
            context.put("previousResult", currentInput);
            
            // Apply context management before step execution
            applyContextManagementBefore(step, context);
            
            // Process dependencies for this step
            processDependencies(step, context);
            
            // Check if this is a conditional step
            if (step.getConditional() != null) {
                currentInput = executeConditionalStep(step.getConditional(), currentInput, context, isFirstStep);
            } else if (step.getTool() != null && !step.getTool().isEmpty()) {
                // This is a tool call step - pass input directly without field extraction
                if (mcpToolService != null) {
                    currentInput = mcpToolService.callTool(step.getTool(), currentInput, context);
                } else {
                    // Fallback when McpToolService is not available
                    currentInput = "Tool call: " + step.getTool() + " (McpToolService not available)";
                }
            } else if (step.getPrompt() != null && !step.getPrompt().isEmpty()) {
                // This is a prompt step - only use for non-tool related processing
                String processedPrompt = processPrompt(step.getPrompt(), currentInput, context);
                
                // Create prompt for ChatModel
                String fullPrompt = processedPrompt;
                
                // Add system prompt if this is the first step and system prompt is available
                if (isFirstStep && context.containsKey("systemPrompt") && context.get("isFirstInvocation") == Boolean.TRUE) {
                    String systemPrompt = (String) context.get("systemPrompt");
                    if (systemPrompt != null && !systemPrompt.isEmpty()) {
                        fullPrompt = systemPrompt + "\n\n" + processedPrompt;
                    }
                }
                
                // Call ChatModel directly
                Prompt prompt = new Prompt(fullPrompt);
                currentInput = chatModel.call(prompt).getResult().getOutput().getText();
            }
            // If neither tool, prompt, nor conditional is specified, skip this step
            
            // Store the result of this step by its ID if an ID is provided
            if (step.getId() != null && !step.getId().isEmpty()) {
                stepResults.put(step.getId(), currentInput);
            }
            
            // Apply context management after step execution
            applyContextManagementAfter(step, context);
            
            isFirstStep = false;
        }
        
        return currentInput;
    }
    
    /**
     * Execute a conditional step by evaluating the condition and executing the appropriate branch
     */
    private String executeConditionalStep(ConditionalStepDef conditionalStep, String input, Map<String, Object> context, boolean isFirstStep) {
        boolean conditionResult = evaluateCondition(conditionalStep.getCondition(), input, context);
        
        WorkflowStepDef stepToExecute = conditionResult ? conditionalStep.getThenStep() : conditionalStep.getElseStep();
        
        if (stepToExecute == null) {
            // No step to execute, return input unchanged
            return input;
        }
        
        return executeSingleStep(stepToExecute, input, context, isFirstStep);
    }
    
    /**
     * Execute a single workflow step (tool, prompt, or nested conditional)
     */
    private String executeSingleStep(WorkflowStepDef step, String input, Map<String, Object> context, boolean isFirstStep) {
        // Apply context management before step execution
        applyContextManagementBefore(step, context);
        
        // Process dependencies for this step
        processDependencies(step, context);
        
        String result;
        if (step.getConditional() != null) {
            // Recursive conditional step
            result = executeConditionalStep(step.getConditional(), input, context, isFirstStep);
        } else if (step.getTool() != null && !step.getTool().isEmpty()) {
            // Tool call step - pass input directly
            if (mcpToolService != null) {
                result = mcpToolService.callTool(step.getTool(), input, context);
            } else {
                result = "Tool call: " + step.getTool() + " (McpToolService not available)";
            }
        } else if (step.getPrompt() != null && !step.getPrompt().isEmpty()) {
            // Prompt step
            String processedPrompt = processPrompt(step.getPrompt(), input, context);
            
            // Add system prompt if this is the first step
            if (isFirstStep && context.containsKey("systemPrompt") && context.get("isFirstInvocation") == Boolean.TRUE) {
                String systemPrompt = (String) context.get("systemPrompt");
                if (systemPrompt != null && !systemPrompt.isEmpty()) {
                    processedPrompt = systemPrompt + "\n\n" + processedPrompt;
                }
            }
            
            Prompt prompt = new Prompt(processedPrompt);
            result = chatModel.call(prompt).getResult().getOutput().getText();
        } else {
            // No valid step type, return input unchanged
            result = input;
        }
        
        // Store the result of this step by its ID if an ID is provided
        if (step.getId() != null && !step.getId().isEmpty()) {
            Map<String, Object> stepResults = (Map<String, Object>) context.getOrDefault("stepResults", new java.util.HashMap<>());
            stepResults.put(step.getId(), result);
            context.put("stepResults", stepResults);
        }
        
        // Apply context management after step execution
        applyContextManagementAfter(step, context);
        
        return result;
    }
    
    /**
     * Evaluate a condition against the current input and context
     */
    private boolean evaluateCondition(ConditionDef condition, String input, Map<String, Object> context) {
        if (condition == null) {
            return false;
        }
        
        String fieldValue = getFieldValue(condition.getField(), input, context);
        String expectedValue = condition.getValue();
        
        return switch (condition.getType()) {
            case EQUALS -> condition.isIgnoreCase() ? 
                fieldValue.equalsIgnoreCase(expectedValue) : 
                fieldValue.equals(expectedValue);
            case CONTAINS -> condition.isIgnoreCase() ? 
                fieldValue.toLowerCase().contains(expectedValue.toLowerCase()) : 
                fieldValue.contains(expectedValue);
            case REGEX -> Pattern.matches(expectedValue, fieldValue);
            case EXISTS -> fieldValue != null && !fieldValue.isEmpty();
            case EMPTY -> fieldValue == null || fieldValue.isEmpty();
        };
    }
    
    /**
     * Get field value from input or context based on field path
     */
    private String getFieldValue(String field, String input, Map<String, Object> context) {
        if (field == null) {
            return "";
        }
        
        return switch (field.toLowerCase()) {
            case "input" -> input != null ? input : "";
            case "previousresult" -> context.get("previousResult") != null ? 
                context.get("previousResult").toString() : "";
            default -> {
                // Handle context field access (e.g., "context.userId")
                if (field.startsWith("context.")) {
                    String contextKey = field.substring(8); // Remove "context." prefix
                    Object value = context.get(contextKey);
                    yield value != null ? value.toString() : "";
                }
                // Direct context key access
                Object value = context.get(field);
                yield value != null ? value.toString() : "";
            }
        };
    }
    
    /**
     * Process dependencies for a workflow step
     * 
     * This method implements the data dependency management between workflow steps.
     * It treats the workflow as a graph where nodes are tool calls and edges are data
     * dependencies between them. The method:
     * 
     * 1. Checks if the step has any dependencies defined (edges pointing to this node)
     * 2. Retrieves the results of those dependent steps from the context
     * 3. Makes those results available to the current step in two ways:
     *    a. Through result mappings: Maps dependency results to named variables in the context
     *    b. Through a dependencyResults map: Makes all dependency results available in a map
     * 
     * This allows each step to access the results of its dependencies when executing,
     * enabling complex workflows where steps depend on data from previous steps.
     * 
     * @param step The workflow step to process dependencies for
     * @param context The context map to update with dependency results
     */
    private void processDependencies(WorkflowStepDef step, Map<String, Object> context) {
        if (step == null) {
            return;
        }
    
        // Get the step results map from context
        Map<String, Object> stepResults = (Map<String, Object>) context.getOrDefault("stepResults", new java.util.HashMap<>());
    
        // Process result mappings if defined
        // This maps dependency results to named variables in the context
        // Example: {"userInfo": "user-step", "billingInfo": "billing-step"}
        if (step.getResultMapping() != null && !step.getResultMapping().isEmpty()) {
            for (Map.Entry<String, String> mapping : step.getResultMapping().entrySet()) {
                String variableName = mapping.getKey();
                String dependencyId = mapping.getValue();
            
                // Get the result of the dependency step
                Object dependencyResult = stepResults.get(dependencyId);
                if (dependencyResult != null) {
                    // Add the mapped result to the context with the specified variable name
                    // This allows referencing the dependency result by a meaningful name
                    context.put(variableName, dependencyResult);
                }
            }
        }
    
        // Process direct dependencies if defined
        // This makes all dependency results available in a dependencyResults map
        if (step.getDependencies() != null && !step.getDependencies().isEmpty()) {
            // Create a map to store dependency results
            Map<String, Object> dependencyResults = new java.util.HashMap<>();
        
            for (String dependencyId : step.getDependencies()) {
                Object dependencyResult = stepResults.get(dependencyId);
                if (dependencyResult != null) {
                    dependencyResults.put(dependencyId, dependencyResult);
                }
            }
        
            // Add all dependency results to the context
            // This allows referencing all dependencies through the dependencyResults map
            context.put("dependencyResults", dependencyResults);
        }
    }
    
    /**
     * Process a prompt by replacing placeholders with values from input and context
     * 
     * This method supports the data dependency management by allowing prompts to reference:
     * 1. The current input: {input}
     * 2. Any context variable: {variableName}
     * 3. Any step result by ID: {step.stepId}
     * 4. Any dependency result: {dependency.dependencyId}
     * 
     * This enables complex workflows where prompts can incorporate results from previous steps,
     * either through direct step references or through the dependency management system.
     * 
     * Examples:
     * - "Analyze this user data: {input}"
     * - "Summarize the user info: {userInfo} and billing info: {billingInfo}"
     * - "Compare these results: {step.step1} vs {step.step2}"
     * - "Process this dependency: {dependency.user-info-step}"
     * 
     * @param prompt The prompt template with placeholders
     * @param input The current input to replace {input} placeholder
     * @param context The context map containing variables and step results
     * @return The processed prompt with all placeholders replaced
     */
    private String processPrompt(String prompt, String input, Map<String, Object> context) {
        // Replace {input} placeholder with the current input
        String processed = prompt.replace("{input}", input);
        
        // Replace context variables: {variableName}
        // This includes any variables added through result mappings
        for (Map.Entry<String, Object> entry : context.entrySet()) {
            processed = processed.replace("{" + entry.getKey() + "}", String.valueOf(entry.getValue()));
        }
        
        // Replace step result references: {step.stepId}
        // This allows directly referencing any step result by its ID
        Map<String, Object> stepResults = (Map<String, Object>) context.getOrDefault("stepResults", new java.util.HashMap<>());
        for (Map.Entry<String, Object> entry : stepResults.entrySet()) {
            processed = processed.replace("{step." + entry.getKey() + "}", String.valueOf(entry.getValue()));
        }
        
        // Replace dependency result references: {dependency.dependencyId}
        // This allows referencing dependency results through the dependencyResults map
        if (context.containsKey("dependencyResults")) {
            Map<String, Object> dependencyResults = (Map<String, Object>) context.get("dependencyResults");
            for (Map.Entry<String, Object> entry : dependencyResults.entrySet()) {
                processed = processed.replace("{dependency." + entry.getKey() + "}", String.valueOf(entry.getValue()));
            }
        }
        
        return processed;
    }
    
    /**
     * Apply context management before step execution
     */
    private void applyContextManagementBefore(WorkflowStepDef step, Map<String, Object> context) {
        ContextManagementDef contextMgmt = step.getContextManagement();
        if (contextMgmt == null) {
            return; // No context management configured, keep all context (default behavior)
        }
        
        if (contextMgmt.getRemoveKeys() != null && !contextMgmt.getRemoveKeys().isEmpty()) {
            // Remove specific keys (takes precedence over clearBefore)
            for (String key : contextMgmt.getRemoveKeys()) {
                context.remove(key);
            }
        } else if (contextMgmt.isClearBefore()) {
            // Clear all context except preserved keys
            clearContextWithPreservation(context, contextMgmt.getPreserveKeys());
        }
    }
    
    /**
     * Apply context management after step execution
     */
    private void applyContextManagementAfter(WorkflowStepDef step, Map<String, Object> context) {
        ContextManagementDef contextMgmt = step.getContextManagement();
        if (contextMgmt == null) {
            return; // No context management configured, keep all context (default behavior)
        }
        
        if (contextMgmt.getRemoveKeys() != null && !contextMgmt.getRemoveKeys().isEmpty()) {
            // Remove specific keys (already handled in before, but could be different keys)
            // For now, we'll use the same removeKeys for both before and after
            return;
        } else if (contextMgmt.isClearAfter()) {
            // Clear all context except preserved keys
            clearContextWithPreservation(context, contextMgmt.getPreserveKeys());
        }
    }
    
    /**
     * Clear context while preserving specified keys
     */
    private void clearContextWithPreservation(Map<String, Object> context, List<String> preserveKeys) {
        if (preserveKeys == null || preserveKeys.isEmpty()) {
            // Clear all context
            context.clear();
        } else {
            // Preserve specified keys
            Map<String, Object> preserved = new java.util.HashMap<>();
            for (String key : preserveKeys) {
                if (context.containsKey(key)) {
                    preserved.put(key, context.get(key));
                }
            }
            context.clear();
            context.putAll(preserved);
        }
    }
}
