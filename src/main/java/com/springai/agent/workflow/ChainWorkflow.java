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
        
        for (WorkflowStepDef step : steps) {
            // Store previous result for condition evaluation
            context.put("previousResult", currentInput);
            
            // Apply context management before step execution
            applyContextManagementBefore(step, context);
            
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
    
    private String processPrompt(String prompt, String input, Map<String, Object> context) {
        String processed = prompt.replace("{input}", input);
        
        // Replace context variables
        for (Map.Entry<String, Object> entry : context.entrySet()) {
            processed = processed.replace("{" + entry.getKey() + "}", String.valueOf(entry.getValue()));
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
