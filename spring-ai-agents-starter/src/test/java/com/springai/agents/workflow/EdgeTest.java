package com.springai.agents.workflow;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Edge")
class EdgeTest {

    @Test
    @DisplayName("record equality based on from and to")
    void equality() {
        Edge e1 = new Edge("a", "b");
        Edge e2 = new Edge("a", "b");
        assertEquals(e1, e2);
        assertEquals(e1.hashCode(), e2.hashCode());
    }

    @Test
    @DisplayName("different edges are not equal")
    void inequality() {
        Edge e1 = new Edge("a", "b");
        Edge e2 = new Edge("a", "c");
        assertNotEquals(e1, e2);
    }

    @Test
    @DisplayName("fluent builder creates edge")
    void fluentBuilder() {
        Edge edge = Edge.from("input").to("output");
        assertEquals("input", edge.from());
        assertEquals("output", edge.to());
    }

    @Test
    @DisplayName("rejects null from")
    void nullFrom() {
        assertThrows(NullPointerException.class, () -> new Edge(null, "to"));
    }

    @Test
    @DisplayName("rejects null to")
    void nullTo() {
        assertThrows(NullPointerException.class, () -> new Edge("from", null));
    }
}

