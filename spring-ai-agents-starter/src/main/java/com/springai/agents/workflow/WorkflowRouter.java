package com.springai.agents.workflow;

import java.util.List;

/**
 * Strategy interface for selecting the most appropriate workflow from a list
 * based on user input.
 * <p>
 * Used by agents with multiple registered workflows to determine which one
 * should handle a given request. The framework provides two implementations:
 * <ul>
 *   <li>{@link LlmWorkflowRouter} — uses an LLM to match input to workflow descriptions</li>
 *   <li>{@link DefaultWorkflowRouter} — returns the first workflow (fallback)</li>
 * </ul>
 * <p>
 * Users can provide their own implementation as a Spring bean to customize routing logic
 * (e.g., keyword-based, regex, classification model).
 *
 * @see LlmWorkflowRouter
 * @see DefaultWorkflowRouter
 */
public interface WorkflowRouter {

    /**
     * Select the most appropriate workflow for the given input.
     *
     * @param workflows Available workflows (guaranteed non-empty).
     * @param input     The raw user input string.
     * @return The selected workflow. Must not return {@code null}.
     */
    Workflow selectWorkflow(List<Workflow> workflows, String input);
}

