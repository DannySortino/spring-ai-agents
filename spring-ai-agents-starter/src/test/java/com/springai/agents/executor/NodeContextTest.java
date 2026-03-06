package com.springai.agents.executor;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("NodeContext")
class NodeContextTest {

    @Test
    @DisplayName("getDependencyResult returns typed result")
    void typedAccessString() {
        NodeContext ctx = NodeContext.builder()
                .resolvedInput("")
                .dependencyResult("step1", "hello")
                .build();

        String result = ctx.getDependencyResult("step1", String.class);
        assertEquals("hello", result);
    }

    @Test
    @DisplayName("getDependencyResult returns typed integer")
    void typedAccessInteger() {
        NodeContext ctx = NodeContext.builder()
                .resolvedInput("")
                .dependencyResult("calc", 42)
                .build();

        Integer result = ctx.getDependencyResult("calc", Integer.class);
        assertEquals(42, result);
    }

    @Test
    @DisplayName("getDependencyResult returns null for missing key")
    void typedAccessMissing() {
        NodeContext ctx = NodeContext.builder()
                .resolvedInput("")
                .build();

        assertNull(ctx.getDependencyResult("nonexistent", String.class));
    }

    @Test
    @DisplayName("getDependencyResult throws ClassCastException on type mismatch")
    void typedAccessWrongType() {
        NodeContext ctx = NodeContext.builder()
                .resolvedInput("")
                .dependencyResult("step1", "not an integer")
                .build();

        assertThrows(ClassCastException.class, () ->
                ctx.getDependencyResult("step1", Integer.class));
    }

    @Test
    @DisplayName("getDependencyResultAsString converts objects to strings")
    void asString() {
        NodeContext ctx = NodeContext.builder()
                .resolvedInput("")
                .dependencyResult("count", 42)
                .dependencyResult("text", "hello")
                .build();

        assertEquals("42", ctx.getDependencyResultAsString("count"));
        assertEquals("hello", ctx.getDependencyResultAsString("text"));
        assertNull(ctx.getDependencyResultAsString("missing"));
    }

    @Test
    @DisplayName("builder creates context with all fields")
    void builderAllFields() {
        NodeContext ctx = NodeContext.builder()
                .resolvedInput("input text")
                .dependencyResult("a", "result-a")
                .dependencyResult("b", "result-b")
                .executionContext(Map.of("key", "value"))
                .build();

        assertEquals("input text", ctx.getResolvedInput());
        assertEquals(2, ctx.getDependencyResults().size());
        assertEquals("value", ctx.getExecutionContext().get("key"));
    }

    @Test
    @DisplayName("default execution context is empty map")
    void defaultExecutionContext() {
        NodeContext ctx = NodeContext.builder().resolvedInput("").build();
        assertNotNull(ctx.getExecutionContext());
        assertTrue(ctx.getExecutionContext().isEmpty());
    }

    @Test
    @DisplayName("stores complex POJO as dependency result")
    void pojoResult() {
        record DataPayload(String name, int value) {}
        var payload = new DataPayload("test", 100);

        NodeContext ctx = NodeContext.builder()
                .resolvedInput("")
                .dependencyResult("data", payload)
                .build();

        DataPayload retrieved = ctx.getDependencyResult("data", DataPayload.class);
        assertEquals("test", retrieved.name());
        assertEquals(100, retrieved.value());
    }
}

