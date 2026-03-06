package com.springai.agents.executor;

import lombok.Builder;
import lombok.NonNull;
import lombok.Singular;
import lombok.Value;

import static java.util.Map.of;

import java.util.Map;

/**
 * Immutable context passed to a {@link NodeExecutor} during node execution.
 * <p>
 * Encapsulates all information an executor needs to process a node:
 * the resolved input from dependencies, all dependency results for placeholder
 * interpolation, and the shared execution context for metadata.
 * <p>
 * Dependency results are stored as {@code Object} to support typed node outputs.
 * Use {@link #getDependencyResult(String, Class)} for type-safe access.
 *
 * <pre>{@code
 * NodeContext ctx = NodeContext.builder()
 *     .resolvedInput("user text here")
 *     .dependencyResult("input", "user text here")
 *     .executionContext(Map.of("currentInput", "user text here"))
 *     .build();
 * }</pre>
 */
@Value
@Builder
public class NodeContext {

    /**
     * The resolved input string for this node.
     * For InputNodes, this is the raw user input.
     * For other nodes, this is the combined output from dependency nodes.
     */
    @NonNull
    String resolvedInput;

    /**
     * Map of dependency nodeId → output object.
     * Used for {@code {nodeId}} placeholder interpolation in templates
     * and for typed access via {@link #getDependencyResult(String, Class)}.
     */
    @Singular("dependencyResult")
    Map<String, Object> dependencyResults;

    /**
     * Shared execution context carrying agent metadata, timestamps, etc.
     * Keys include: {@code currentInput}, {@code agentName}, {@code timestamp}.
     */
    @Builder.Default
    Map<String, Object> executionContext = of();

    /**
     * Retrieve a dependency result cast to the expected type.
     *
     * @param nodeId The dependency node ID.
     * @param type   The expected result type.
     * @param <T>    The result type.
     * @return The typed result, or {@code null} if not found.
     * @throws ClassCastException if the stored result is not assignable to the given type.
     */
    @SuppressWarnings("unchecked")
    public <T> T getDependencyResult(String nodeId, Class<T> type) {
        Object result = dependencyResults.get(nodeId);
        if (result == null) return null;
        return (T) type.cast(result);
    }

    /**
     * Get a dependency result as a String, using {@code String.valueOf()} for non-string values.
     */
    public String getDependencyResultAsString(String nodeId) {
        Object result = dependencyResults.get(nodeId);
        return result != null ? String.valueOf(result) : null;
    }
}
