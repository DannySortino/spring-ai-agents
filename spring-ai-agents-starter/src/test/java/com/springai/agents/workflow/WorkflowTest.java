package com.springai.agents.workflow;

import com.springai.agents.node.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Workflow")
class WorkflowTest {

    private Workflow sequential;
    private Workflow parallel;
    private Workflow diamond;

    @BeforeEach
    void setUp() {
        // input → process → output
        sequential = WorkflowBuilder.create()
                .name("sequential")
                .description("Sequential workflow")
                .node(InputNode.builder().id("input").build())
                .node(LlmNode.builder().id("process").promptTemplate("P").build())
                .node(OutputNode.builder().id("output").build())
                .edge("input", "process")
                .edge("process", "output")
                .build();

        // input → (a, b) → output
        parallel = WorkflowBuilder.create()
                .node(InputNode.builder().id("input").build())
                .node(LlmNode.builder().id("a").promptTemplate("A").build())
                .node(LlmNode.builder().id("b").promptTemplate("B").build())
                .node(OutputNode.builder().id("output").build())
                .edge("input", "a")
                .edge("input", "b")
                .edge("a", "output")
                .edge("b", "output")
                .build();

        // input → (a, b) → c → output
        diamond = WorkflowBuilder.create()
                .node(InputNode.builder().id("input").build())
                .node(LlmNode.builder().id("a").promptTemplate("A").build())
                .node(LlmNode.builder().id("b").promptTemplate("B").build())
                .node(LlmNode.builder().id("c").promptTemplate("C").build())
                .node(OutputNode.builder().id("output").build())
                .edge("input", "a")
                .edge("input", "b")
                .edge("a", "c")
                .edge("b", "c")
                .edge("c", "output")
                .build();
    }

    @Test
    @DisplayName("getNode returns correct node")
    void getNode() {
        assertInstanceOf(InputNode.class, sequential.getNode("input"));
        assertInstanceOf(LlmNode.class, sequential.getNode("process"));
        assertInstanceOf(OutputNode.class, sequential.getNode("output"));
        assertNull(sequential.getNode("nonexistent"));
    }

    @Test
    @DisplayName("getDependencies returns upstream node IDs")
    void getDependencies() {
        assertEquals(Set.of(), sequential.getDependencies("input"));
        assertEquals(Set.of("input"), sequential.getDependencies("process"));
        assertEquals(Set.of("process"), sequential.getDependencies("output"));

        // Diamond: c depends on both a and b
        assertEquals(Set.of("a", "b"), diamond.getDependencies("c"));
    }

    @Test
    @DisplayName("getDownstream returns downstream node IDs")
    void getDownstream() {
        assertEquals(Set.of("process"), sequential.getDownstream("input"));
        assertEquals(Set.of("output"), sequential.getDownstream("process"));
        assertEquals(Set.of(), sequential.getDownstream("output"));

        // Parallel: input fans out to a and b
        assertEquals(Set.of("a", "b"), parallel.getDownstream("input"));
    }

    @Test
    @DisplayName("inputNodeIds and outputNodeIds are indexed")
    void nodeIdIndexes() {
        assertEquals(Set.of("input"), sequential.getInputNodeIds());
        assertEquals(Set.of("output"), sequential.getOutputNodeIds());
    }

    @Test
    @DisplayName("name and description are set")
    void nameAndDescription() {
        assertEquals("sequential", sequential.getName());
        assertEquals("Sequential workflow", sequential.getDescription());
        assertEquals("default", parallel.getName());
    }

    @Test
    @DisplayName("topological order respects dependencies — sequential")
    void topologicalOrderSequential() {
        List<String> order = sequential.getTopologicalOrder();
        assertEquals(3, order.size());
        assertTrue(order.indexOf("input") < order.indexOf("process"));
        assertTrue(order.indexOf("process") < order.indexOf("output"));
    }

    @Test
    @DisplayName("topological order respects dependencies — diamond")
    void topologicalOrderDiamond() {
        List<String> order = diamond.getTopologicalOrder();
        assertEquals(5, order.size());
        assertTrue(order.indexOf("input") < order.indexOf("a"));
        assertTrue(order.indexOf("input") < order.indexOf("b"));
        assertTrue(order.indexOf("a") < order.indexOf("c"));
        assertTrue(order.indexOf("b") < order.indexOf("c"));
        assertTrue(order.indexOf("c") < order.indexOf("output"));
    }

    @Test
    @DisplayName("level groups — sequential has 3 levels")
    void levelGroupsSequential() {
        Map<Integer, List<String>> levels = sequential.getLevelGroups();
        assertEquals(3, levels.size());
        assertEquals(List.of("input"), levels.get(0));
        assertEquals(List.of("process"), levels.get(1));
        assertEquals(List.of("output"), levels.get(2));
    }

    @Test
    @DisplayName("level groups — parallel fan-out has nodes at same level")
    void levelGroupsParallel() {
        Map<Integer, List<String>> levels = parallel.getLevelGroups();
        assertEquals(3, levels.size());
        assertEquals(List.of("input"), levels.get(0));
        // a and b are at the same level (both depend only on input)
        assertTrue(levels.get(1).containsAll(List.of("a", "b")));
        assertEquals(2, levels.get(1).size());
        assertEquals(List.of("output"), levels.get(2));
    }

    @Test
    @DisplayName("level groups — diamond has 4 levels")
    void levelGroupsDiamond() {
        Map<Integer, List<String>> levels = diamond.getLevelGroups();
        assertEquals(4, levels.size());
        assertEquals(List.of("input"), levels.get(0));
        assertTrue(levels.get(1).containsAll(List.of("a", "b")));
        assertEquals(List.of("c"), levels.get(2));
        assertEquals(List.of("output"), levels.get(3));
    }

    @Test
    @DisplayName("size returns number of nodes")
    void size() {
        assertEquals(3, sequential.size());
        assertEquals(4, parallel.size());
        assertEquals(5, diamond.size());
    }

    @Test
    @DisplayName("workflow is immutable — cannot modify nodes map")
    void immutable() {
        assertThrows(UnsupportedOperationException.class, () ->
                sequential.getNodes().put("new", InputNode.builder().id("new").build()));
    }
}

