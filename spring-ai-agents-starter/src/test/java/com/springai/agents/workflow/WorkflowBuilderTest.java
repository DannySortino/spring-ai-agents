package com.springai.agents.workflow;

import com.springai.agents.node.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("WorkflowBuilder")
class WorkflowBuilderTest {

    @Nested
    @DisplayName("valid builds")
    class ValidBuilds {

        @Test
        @DisplayName("builds minimal workflow: input → output")
        void minimalWorkflow() {
            Workflow workflow = WorkflowBuilder.create()
                    .node(InputNode.builder().id("input").build())
                    .node(OutputNode.builder().id("output").build())
                    .edge("input", "output")
                    .build();

            assertEquals(2, workflow.size());
            assertEquals(1, workflow.getEdges().size());
            assertNotNull(workflow.getNode("input"));
            assertNotNull(workflow.getNode("output"));
        }

        @Test
        @DisplayName("builds sequential workflow: input → process → output")
        void sequentialWorkflow() {
            Workflow workflow = WorkflowBuilder.create()
                    .node(InputNode.builder().id("input").build())
                    .node(LlmNode.builder().id("process").promptTemplate("Do: {input}").build())
                    .node(OutputNode.builder().id("output").build())
                    .edge("input", "process")
                    .edge("process", "output")
                    .build();

            assertEquals(3, workflow.size());
            assertEquals(2, workflow.getEdges().size());
        }

        @Test
        @DisplayName("builds parallel fan-out workflow")
        void parallelFanOut() {
            Workflow workflow = WorkflowBuilder.create()
                    .node(InputNode.builder().id("input").build())
                    .node(LlmNode.builder().id("a").promptTemplate("A: {input}").build())
                    .node(LlmNode.builder().id("b").promptTemplate("B: {input}").build())
                    .node(OutputNode.builder().id("output").build())
                    .edge("input", "a")
                    .edge("input", "b")
                    .edge("a", "output")
                    .edge("b", "output")
                    .build();

            assertEquals(4, workflow.size());
            assertEquals(4, workflow.getEdges().size());
        }

        @Test
        @DisplayName("builds diamond pattern")
        void diamondPattern() {
            Workflow workflow = WorkflowBuilder.create()
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

            assertEquals(5, workflow.size());
        }

        @Test
        @DisplayName("sets name and description")
        void nameAndDescription() {
            Workflow workflow = WorkflowBuilder.create()
                    .name("test-workflow")
                    .description("A test workflow")
                    .node(InputNode.builder().id("input").build())
                    .node(OutputNode.builder().id("output").build())
                    .edge("input", "output")
                    .build();

            assertEquals("test-workflow", workflow.getName());
            assertEquals("A test workflow", workflow.getDescription());
        }

        @Test
        @DisplayName("defaults name to 'default' and description to empty")
        void defaultNameAndDescription() {
            Workflow workflow = WorkflowBuilder.create()
                    .node(InputNode.builder().id("input").build())
                    .node(OutputNode.builder().id("output").build())
                    .edge("input", "output")
                    .build();

            assertEquals("default", workflow.getName());
            assertEquals("", workflow.getDescription());
        }

        @Test
        @DisplayName("supports Edge.from().to() syntax")
        void edgeBuilderSyntax() {
            Workflow workflow = WorkflowBuilder.create()
                    .node(InputNode.builder().id("input").build())
                    .node(OutputNode.builder().id("output").build())
                    .edge(Edge.from("input").to("output"))
                    .build();

            assertEquals(1, workflow.getEdges().size());
        }

        @Test
        @DisplayName("supports edge(Node, Node) — type-safe node references")
        void edgeByNodeReference() {
            var input = InputNode.builder().id("input").build();
            var output = OutputNode.builder().id("output").build();

            Workflow workflow = WorkflowBuilder.create()
                    .nodes(input, output)
                    .edge(input, output)
                    .build();

            assertEquals(2, workflow.size());
            assertEquals(1, workflow.getEdges().size());
        }

        @Test
        @DisplayName("supports batch nodes() and edge(Node, Node)")
        void batchNodesAndEdgesByRef() {
            var input = InputNode.builder().id("input").build();
            var process = LlmNode.builder().id("process").promptTemplate("P").build();
            var output = OutputNode.builder().id("output").build();

            Workflow workflow = WorkflowBuilder.create()
                    .nodes(input, process, output)
                    .edge(input, process)
                    .edge(process, output)
                    .build();

            assertEquals(3, workflow.size());
            assertEquals(2, workflow.getEdges().size());
        }

        @Test
        @DisplayName("supports batch edges() with Edge records")
        void batchEdges() {
            var input = InputNode.builder().id("input").build();
            var output = OutputNode.builder().id("output").build();

            Workflow workflow = WorkflowBuilder.create()
                    .nodes(input, output)
                    .edges(Edge.from("input").to("output"))
                    .build();

            assertEquals(1, workflow.getEdges().size());
        }
    }

    @Nested
    @DisplayName("validation errors")
    class ValidationErrors {

        @Test
        @DisplayName("rejects missing InputNode")
        void missingInputNode() {
            var builder = WorkflowBuilder.create()
                    .node(OutputNode.builder().id("output").build());

            var ex = assertThrows(IllegalArgumentException.class, builder::build);
            assertTrue(ex.getMessage().contains("InputNode"));
        }

        @Test
        @DisplayName("rejects missing OutputNode")
        void missingOutputNode() {
            var builder = WorkflowBuilder.create()
                    .node(InputNode.builder().id("input").build());

            var ex = assertThrows(IllegalArgumentException.class, builder::build);
            assertTrue(ex.getMessage().contains("OutputNode"));
        }

        @Test
        @DisplayName("rejects duplicate node IDs")
        void duplicateNodeIds() {
            var builder = WorkflowBuilder.create()
                    .node(InputNode.builder().id("input").build());

            assertThrows(IllegalArgumentException.class, () ->
                    builder.node(InputNode.builder().id("input").build()));
        }

        @Test
        @DisplayName("rejects edge referencing non-existent source")
        void nonExistentEdgeSource() {
            var builder = WorkflowBuilder.create()
                    .node(InputNode.builder().id("input").build())
                    .node(OutputNode.builder().id("output").build())
                    .edge("nonexistent", "output");

            var ex = assertThrows(IllegalArgumentException.class, builder::build);
            assertTrue(ex.getMessage().contains("nonexistent"));
        }

        @Test
        @DisplayName("rejects edge referencing non-existent target")
        void nonExistentEdgeTarget() {
            var builder = WorkflowBuilder.create()
                    .node(InputNode.builder().id("input").build())
                    .node(OutputNode.builder().id("output").build())
                    .edge("input", "nonexistent");

            var ex = assertThrows(IllegalArgumentException.class, builder::build);
            assertTrue(ex.getMessage().contains("nonexistent"));
        }

        @Test
        @DisplayName("rejects simple cycle: A → B → A")
        void simpleCycle() {
            var builder = WorkflowBuilder.create()
                    .node(InputNode.builder().id("input").build())
                    .node(LlmNode.builder().id("a").promptTemplate("A").build())
                    .node(LlmNode.builder().id("b").promptTemplate("B").build())
                    .node(OutputNode.builder().id("output").build())
                    .edge("input", "a")
                    .edge("a", "b")
                    .edge("b", "a")  // cycle!
                    .edge("b", "output");

            var ex = assertThrows(IllegalArgumentException.class, builder::build);
            assertTrue(ex.getMessage().contains("cycle"));
        }

        @Test
        @DisplayName("rejects self-loop: A → A")
        void selfLoop() {
            var builder = WorkflowBuilder.create()
                    .node(InputNode.builder().id("input").build())
                    .node(LlmNode.builder().id("a").promptTemplate("A").build())
                    .node(OutputNode.builder().id("output").build())
                    .edge("input", "a")
                    .edge("a", "a")  // self-loop!
                    .edge("a", "output");

            var ex = assertThrows(IllegalArgumentException.class, builder::build);
            assertTrue(ex.getMessage().contains("cycle"));
        }
    }
}

