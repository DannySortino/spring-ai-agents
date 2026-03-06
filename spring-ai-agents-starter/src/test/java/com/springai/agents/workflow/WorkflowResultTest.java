package com.springai.agents.workflow;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("WorkflowResult")
class WorkflowResultTest {

    @Test
    @DisplayName("getNodeResult returns typed result")
    void typedAccess() {
        WorkflowResult result = WorkflowResult.builder()
                .output("final output")
                .nodeResults(Map.of("calc", 42, "text", "hello"))
                .durationMs(100L)
                .build();

        assertEquals(42, result.getNodeResult("calc", Integer.class));
        assertEquals("hello", result.getNodeResult("text", String.class));
    }

    @Test
    @DisplayName("getNodeResult returns null for missing key")
    void missingKey() {
        WorkflowResult result = WorkflowResult.builder()
                .output("")
                .nodeResults(Map.of())
                .durationMs(0L)
                .build();

        assertNull(result.getNodeResult("nonexistent", String.class));
    }

    @Test
    @DisplayName("getNodeResult throws ClassCastException for type mismatch")
    void typeMismatch() {
        WorkflowResult result = WorkflowResult.builder()
                .output("")
                .nodeResults(Map.of("text", "hello"))
                .durationMs(0L)
                .build();

        assertThrows(ClassCastException.class, () ->
                result.getNodeResult("text", Integer.class));
    }
}

