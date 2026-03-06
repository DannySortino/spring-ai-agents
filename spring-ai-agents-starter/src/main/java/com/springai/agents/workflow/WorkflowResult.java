package com.springai.agents.workflow;

import lombok.Builder;
import lombok.Value;

import java.util.Map;

/**
 * Immutable result of a workflow execution.
 * <p>
 * Node results are stored as {@code Object} to support typed outputs.
 * Use {@link #getNodeResult(String, Class)} for type-safe access.
 *
 * @see WorkflowExecutor
 * @see ReactiveWorkflowExecutor
 */
@Value
@Builder
public class WorkflowResult {

    /** The final output string from the output node. */
    String output;

    /** Map of all nodeId → individual output objects, for debugging/inspection. */
    Map<String, Object> nodeResults;

    /** Total execution time in milliseconds. */
    long durationMs;

    /**
     * Retrieve a node result cast to the expected type.
     *
     * @param nodeId The node ID.
     * @param type   The expected result type.
     * @param <T>    The result type.
     * @return The typed result, or {@code null} if not found.
     * @throws ClassCastException if the stored result is not assignable to the given type.
     */
    public <T> T getNodeResult(String nodeId, Class<T> type) {
        Object result = nodeResults.get(nodeId);
        if (result == null) return null;
        return type.cast(result);
    }
}
