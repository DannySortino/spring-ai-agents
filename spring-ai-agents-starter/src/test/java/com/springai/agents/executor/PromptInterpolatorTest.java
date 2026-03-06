package com.springai.agents.executor;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("PromptInterpolator")
class PromptInterpolatorTest {

    @Test
    @DisplayName("replaces single placeholder")
    void singlePlaceholder() {
        String result = PromptInterpolator.interpolate(
                "Hello {name}",
                Map.of("name", "World"),
                Map.of());
        assertEquals("Hello World", result);
    }

    @Test
    @DisplayName("replaces multiple placeholders")
    void multiplePlaceholders() {
        String result = PromptInterpolator.interpolate(
                "{a} and {b}",
                Map.of("a", "first", "b", "second"),
                Map.of());
        assertEquals("first and second", result);
    }

    @Test
    @DisplayName("replaces {input} with currentInput from context")
    void inputPlaceholder() {
        String result = PromptInterpolator.interpolate(
                "Process: {input}",
                Map.of(),
                Map.of("currentInput", "user query"));
        assertEquals("Process: user query", result);
    }

    @Test
    @DisplayName("replaces context keys")
    void contextKeys() {
        String result = PromptInterpolator.interpolate(
                "Agent: {agentName}",
                Map.of(),
                Map.of("agentName", "test-agent"));
        assertEquals("Agent: test-agent", result);
    }

    @Test
    @DisplayName("leaves unresolved placeholders as-is")
    void unresolvedPlaceholder() {
        String result = PromptInterpolator.interpolate(
                "Hello {unknown}",
                Map.of(),
                Map.of());
        assertEquals("Hello {unknown}", result);
    }

    @Test
    @DisplayName("returns empty string for null template")
    void nullTemplate() {
        String result = PromptInterpolator.interpolate(null, Map.of(), Map.of());
        assertEquals("", result);
    }

    @Test
    @DisplayName("handles null dependency results map")
    void nullDependencyResults() {
        String result = PromptInterpolator.interpolate("Hello {name}", null, Map.of());
        assertEquals("Hello {name}", result);
    }

    @Test
    @DisplayName("handles null execution context map")
    void nullExecutionContext() {
        String result = PromptInterpolator.interpolate(
                "Hello {name}",
                Map.of("name", "World"),
                null);
        assertEquals("Hello World", result);
    }

    @Test
    @DisplayName("converts non-string objects via String.valueOf()")
    void nonStringObjects() {
        String result = PromptInterpolator.interpolate(
                "Count: {count}",
                Map.of("count", 42),
                Map.of());
        assertEquals("Count: 42", result);
    }

    @Test
    @DisplayName("dependency results take priority over context keys with same name")
    void dependencyOverridesContext() {
        String result = PromptInterpolator.interpolate(
                "Value: {key}",
                Map.of("key", "from-dependency"),
                Map.of("key", "from-context"));
        // Dependency results are applied first, so they should win
        assertEquals("Value: from-dependency", result);
    }

    @Test
    @DisplayName("skips null values in dependency results")
    void nullDependencyValue() {
        Map<String, Object> deps = new java.util.HashMap<>();
        deps.put("name", null);
        String result = PromptInterpolator.interpolate("Hello {name}", deps, Map.of());
        assertEquals("Hello {name}", result);
    }

    @Test
    @DisplayName("handles empty template")
    void emptyTemplate() {
        String result = PromptInterpolator.interpolate("", Map.of("a", "b"), Map.of());
        assertEquals("", result);
    }
}

