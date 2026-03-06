package com.springai.agents.executor;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Utility class for interpolating {@code {placeholder}} tokens in prompt/URL/body templates.
 * <p>
 * Replaces:
 * <ul>
 *   <li>{@code {nodeId}} — with the corresponding entry from dependency results</li>
 *   <li>{@code {input}} — with the raw user input from the execution context</li>
 *   <li>{@code {contextKey}} — with values from the execution context map</li>
 * </ul>
 * <p>
 * Unresolved placeholders are left as-is. Non-string values are converted
 * via {@code String.valueOf()}.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class PromptInterpolator {

    /**
     * Interpolate a template string with dependency results and execution context.
     *
     * @param template          The template string containing {@code {placeholder}} tokens.
     * @param dependencyResults Map of nodeId → output (String or Object) from completed dependency nodes.
     * @param executionContext   Map of context keys → values (e.g., currentInput, agentName).
     * @return The interpolated string.
     */
    public static String interpolate(String template,
                                     Map<String, Object> dependencyResults,
                                     Map<String, Object> executionContext) {
        if (template == null) return "";

        String result = template;

        // Replace {input} with raw user input from context
        if (executionContext != null && executionContext.containsKey("currentInput")) {
            result = result.replace("{input}", String.valueOf(executionContext.get("currentInput")));
        }

        // Replace {nodeId} with dependency results
        if (dependencyResults != null) {
            for (var entry : dependencyResults.entrySet()) {
                if (entry.getValue() != null) {
                    result = result.replace("{" + entry.getKey() + "}", String.valueOf(entry.getValue()));
                }
            }
        }

        // Replace {contextKey} with execution context values
        if (executionContext != null) {
            for (var entry : executionContext.entrySet()) {
                if (entry.getValue() != null) {
                    result = result.replace("{" + entry.getKey() + "}", String.valueOf(entry.getValue()));
                }
            }
        }

        return result;
    }
}
