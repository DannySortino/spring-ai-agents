package com.springai.agents.visualization.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Model Enums")
class ModelEnumsTest {

    @Test
    @DisplayName("ExecutionStatus has all expected values")
    void executionStatusValues() {
        ExecutionStatus[] values = ExecutionStatus.values();
        assertEquals(3, values.length);
        assertNotNull(ExecutionStatus.valueOf("RUNNING"));
        assertNotNull(ExecutionStatus.valueOf("COMPLETED"));
        assertNotNull(ExecutionStatus.valueOf("FAILED"));
    }

    @Test
    @DisplayName("NodeStatus has all expected values")
    void nodeStatusValues() {
        NodeStatus[] values = NodeStatus.values();
        assertEquals(5, values.length);
        assertNotNull(NodeStatus.valueOf("PENDING"));
        assertNotNull(NodeStatus.valueOf("RUNNING"));
        assertNotNull(NodeStatus.valueOf("COMPLETED"));
        assertNotNull(NodeStatus.valueOf("FAILED"));
        assertNotNull(NodeStatus.valueOf("SKIPPED"));
    }

    @Test
    @DisplayName("invalid enum value throws IllegalArgumentException")
    void invalidEnumThrows() {
        assertThrows(IllegalArgumentException.class, () -> ExecutionStatus.valueOf("INVALID"));
        assertThrows(IllegalArgumentException.class, () -> NodeStatus.valueOf("INVALID"));
    }
}

