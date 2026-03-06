package com.springai.agents.workflow;

import com.springai.agents.executor.*;
import com.springai.agents.node.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("WorkflowExecutor")
class WorkflowExecutorTest {

    private NodeExecutorRegistry registry;
    private WorkflowExecutor executor;

    @BeforeEach
    void setUp() {
        registry = new NodeExecutorRegistry();
        registry.register(new InputExecutor());
        registry.register(new ContextExecutor());
        // Simple LLM-like executor that echoes input
        registry.register(new NodeExecutor<LlmNode>() {
            @Override
            public Object execute(LlmNode node, NodeContext context) {
                return "LLM[" + context.getResolvedInput() + "]";
            }
            @Override
            public Class<LlmNode> getNodeType() { return LlmNode.class; }
        });
        // Output executor that passes through
        registry.register(new NodeExecutor<OutputNode>() {
            @Override
            public Object execute(OutputNode node, NodeContext context) {
                return context.getResolvedInput();
            }
            @Override
            public Class<OutputNode> getNodeType() { return OutputNode.class; }
        });

        executor = new WorkflowExecutor(registry);
    }

    @Test
    @DisplayName("executes sequential workflow: input → process → output")
    void sequentialWorkflow() {
        Workflow workflow = WorkflowBuilder.create()
                .node(InputNode.builder().id("input").build())
                .node(LlmNode.builder().id("process").promptTemplate("Do: {input}").build())
                .node(OutputNode.builder().id("output").build())
                .edge("input", "process")
                .edge("process", "output")
                .build();

        WorkflowResult result = executor.execute(workflow, "hello");

        assertNotNull(result);
        assertEquals("LLM[hello]", result.getOutput());
        assertTrue(result.getDurationMs() >= 0);
        assertEquals(3, result.getNodeResults().size());
    }

    @Test
    @DisplayName("executes parallel fan-out workflow")
    void parallelFanOut() {
        Workflow workflow = WorkflowBuilder.create()
                .node(InputNode.builder().id("input").build())
                .node(LlmNode.builder().id("a").promptTemplate("A").build())
                .node(LlmNode.builder().id("b").promptTemplate("B").build())
                .node(OutputNode.builder().id("output").build())
                .edge("input", "a")
                .edge("input", "b")
                .edge("a", "output")
                .edge("b", "output")
                .build();

        WorkflowResult result = executor.execute(workflow, "test");

        assertNotNull(result.getOutput());
        assertFalse(result.getOutput().isEmpty());
        assertEquals(4, result.getNodeResults().size());
        // Both a and b should have been executed
        assertNotNull(result.getNodeResults().get("a"));
        assertNotNull(result.getNodeResults().get("b"));
    }

    @Test
    @DisplayName("executes diamond pattern correctly")
    void diamondPattern() {
        Workflow workflow = WorkflowBuilder.create()
                .node(InputNode.builder().id("input").build())
                .node(LlmNode.builder().id("a").promptTemplate("A").build())
                .node(LlmNode.builder().id("b").promptTemplate("B").build())
                .node(LlmNode.builder().id("merge").promptTemplate("Merge").build())
                .node(OutputNode.builder().id("output").build())
                .edge("input", "a")
                .edge("input", "b")
                .edge("a", "merge")
                .edge("b", "merge")
                .edge("merge", "output")
                .build();

        WorkflowResult result = executor.execute(workflow, "data");

        assertEquals(5, result.getNodeResults().size());
        assertNotNull(result.getOutput());
    }

    @Test
    @DisplayName("passes additional context to all nodes")
    void additionalContext() {
        // Use a custom executor that reads context
        NodeExecutorRegistry customRegistry = new NodeExecutorRegistry();
        customRegistry.register(new InputExecutor());
        customRegistry.register(new NodeExecutor<OutputNode>() {
            @Override
            public Object execute(OutputNode node, NodeContext context) {
                return "agent=" + context.getExecutionContext().get("agentName");
            }
            @Override
            public Class<OutputNode> getNodeType() { return OutputNode.class; }
        });

        var customExecutor = new WorkflowExecutor(customRegistry);
        Workflow workflow = WorkflowBuilder.create()
                .node(InputNode.builder().id("input").build())
                .node(OutputNode.builder().id("output").build())
                .edge("input", "output")
                .build();

        WorkflowResult result = customExecutor.execute(workflow, "test",
                Map.of("agentName", "my-agent"));

        assertEquals("agent=my-agent", result.getOutput());
    }

    @Test
    @DisplayName("supports typed results in node results map")
    void typedResults() {
        NodeExecutorRegistry typedRegistry = new NodeExecutorRegistry();
        typedRegistry.register(new InputExecutor());
        typedRegistry.register(new NodeExecutor<LlmNode>() {
            @Override
            public Object execute(LlmNode node, NodeContext context) {
                return 42; // Return an Integer, not a String
            }
            @Override
            public Class<LlmNode> getNodeType() { return LlmNode.class; }
        });
        typedRegistry.register(new NodeExecutor<OutputNode>() {
            @Override
            public Object execute(OutputNode node, NodeContext context) {
                return context.getResolvedInput();
            }
            @Override
            public Class<OutputNode> getNodeType() { return OutputNode.class; }
        });

        var typedExecutor = new WorkflowExecutor(typedRegistry);
        Workflow workflow = WorkflowBuilder.create()
                .node(InputNode.builder().id("input").build())
                .node(LlmNode.builder().id("calc").promptTemplate("calc").build())
                .node(OutputNode.builder().id("output").build())
                .edge("input", "calc")
                .edge("calc", "output")
                .build();

        WorkflowResult result = typedExecutor.execute(workflow, "test");

        // calc result should be stored as Integer
        assertEquals(42, result.getNodeResult("calc", Integer.class));
        // output should get String.valueOf(42) as resolved input
        assertEquals("42", result.getOutput());
    }

    @Test
    @DisplayName("context node injects static text")
    void contextNodeIntegration() {
        Workflow workflow = WorkflowBuilder.create()
                .node(InputNode.builder().id("input").build())
                .node(ContextNode.builder().id("guidelines").contextText("Be helpful.").build())
                .node(LlmNode.builder().id("process").promptTemplate("P").build())
                .node(OutputNode.builder().id("output").build())
                .edge("input", "process")
                .edge("guidelines", "process")
                .edge("process", "output")
                .build();

        WorkflowResult result = executor.execute(workflow, "question");

        assertEquals("Be helpful.", result.getNodeResults().get("guidelines"));
    }

    @Test
    @DisplayName("beforeExecute hook fires before node execution")
    void beforeExecuteHook() {
        AtomicBoolean hookFired = new AtomicBoolean(false);
        AtomicReference<String> capturedInput = new AtomicReference<>();

        NodeHooks hooks = NodeHooks.builder()
                .beforeExecute(ctx -> {
                    hookFired.set(true);
                    capturedInput.set(ctx.getResolvedInput());
                })
                .build();

        Workflow workflow = WorkflowBuilder.create()
                .node(InputNode.builder().id("input").hooks(hooks).build())
                .node(OutputNode.builder().id("output").build())
                .edge("input", "output")
                .build();

        executor.execute(workflow, "hook test");

        assertTrue(hookFired.get());
        assertEquals("hook test", capturedInput.get());
    }

    @Test
    @DisplayName("afterExecute hook fires after node execution with result")
    void afterExecuteHook() {
        AtomicReference<Object> capturedResult = new AtomicReference<>();

        NodeHooks hooks = NodeHooks.builder()
                .afterExecute((ctx, result) -> capturedResult.set(result))
                .build();

        Workflow workflow = WorkflowBuilder.create()
                .node(InputNode.builder().id("input").hooks(hooks).build())
                .node(OutputNode.builder().id("output").build())
                .edge("input", "output")
                .build();

        executor.execute(workflow, "result test");

        assertEquals("result test", capturedResult.get());
    }

    @Test
    @DisplayName("both hooks fire in correct order")
    void hookOrder() {
        StringBuilder order = new StringBuilder();

        NodeHooks hooks = NodeHooks.builder()
                .beforeExecute(ctx -> order.append("before,"))
                .afterExecute((ctx, result) -> order.append("after"))
                .build();

        Workflow workflow = WorkflowBuilder.create()
                .node(InputNode.builder().id("input").hooks(hooks).build())
                .node(OutputNode.builder().id("output").build())
                .edge("input", "output")
                .build();

        executor.execute(workflow, "test");

        assertEquals("before,after", order.toString());
    }
}

