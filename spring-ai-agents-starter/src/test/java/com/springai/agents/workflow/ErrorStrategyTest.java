package com.springai.agents.workflow;

import com.springai.agents.executor.*;
import com.springai.agents.node.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ErrorStrategy")
class ErrorStrategyTest {

    private WorkflowExecutor createExecutor(NodeExecutorRegistry registry) {
        return new WorkflowExecutor(registry);
    }

    @Test
    @DisplayName("FAIL_FAST propagates exception")
    void failFast() {
        NodeExecutorRegistry registry = new NodeExecutorRegistry();
        registry.register(new InputExecutor());
        registry.register(new NodeExecutor<LlmNode>() {
            @Override
            public Object execute(LlmNode node, NodeContext ctx) {
                throw new RuntimeException("LLM unavailable");
            }
            @Override
            public Class<LlmNode> getNodeType() { return LlmNode.class; }
        });
        registry.register(new NodeExecutor<OutputNode>() {
            @Override
            public Object execute(OutputNode node, NodeContext ctx) { return ctx.getResolvedInput(); }
            @Override
            public Class<OutputNode> getNodeType() { return OutputNode.class; }
        });

        Workflow workflow = WorkflowBuilder.create()
                .node(InputNode.builder().id("input").build())
                .node(LlmNode.builder().id("process").promptTemplate("P")
                        .config(NodeConfig.builder().errorStrategy(ErrorStrategy.FAIL_FAST).build())
                        .build())
                .node(OutputNode.builder().id("output").build())
                .edge("input", "process")
                .edge("process", "output")
                .build();

        assertThrows(RuntimeException.class, () ->
                createExecutor(registry).execute(workflow, "test"));
    }

    @Test
    @DisplayName("CONTINUE_WITH_DEFAULT uses default value on failure")
    void continueWithDefault() {
        NodeExecutorRegistry registry = new NodeExecutorRegistry();
        registry.register(new InputExecutor());
        registry.register(new NodeExecutor<LlmNode>() {
            @Override
            public Object execute(LlmNode node, NodeContext ctx) {
                throw new RuntimeException("LLM unavailable");
            }
            @Override
            public Class<LlmNode> getNodeType() { return LlmNode.class; }
        });
        registry.register(new NodeExecutor<OutputNode>() {
            @Override
            public Object execute(OutputNode node, NodeContext ctx) { return ctx.getResolvedInput(); }
            @Override
            public Class<OutputNode> getNodeType() { return OutputNode.class; }
        });

        Workflow workflow = WorkflowBuilder.create()
                .node(InputNode.builder().id("input").build())
                .node(LlmNode.builder().id("process").promptTemplate("P")
                        .config(NodeConfig.builder()
                                .errorStrategy(ErrorStrategy.CONTINUE_WITH_DEFAULT)
                                .defaultValue("fallback result")
                                .build())
                        .build())
                .node(OutputNode.builder().id("output").build())
                .edge("input", "process")
                .edge("process", "output")
                .build();

        WorkflowResult result = createExecutor(registry).execute(workflow, "test");

        assertEquals("fallback result", result.getOutput());
        assertEquals("fallback result", result.getNodeResults().get("process"));
    }

    @Test
    @DisplayName("SKIP stores null and continues execution")
    void skipNode() {
        NodeExecutorRegistry registry = new NodeExecutorRegistry();
        registry.register(new InputExecutor());
        registry.register(new NodeExecutor<LlmNode>() {
            @Override
            public Object execute(LlmNode node, NodeContext ctx) {
                throw new RuntimeException("Service down");
            }
            @Override
            public Class<LlmNode> getNodeType() { return LlmNode.class; }
        });
        registry.register(new NodeExecutor<OutputNode>() {
            @Override
            public Object execute(OutputNode node, NodeContext ctx) { return ctx.getResolvedInput(); }
            @Override
            public Class<OutputNode> getNodeType() { return OutputNode.class; }
        });

        Workflow workflow = WorkflowBuilder.create()
                .node(InputNode.builder().id("input").build())
                .node(LlmNode.builder().id("process").promptTemplate("P")
                        .config(NodeConfig.builder()
                                .errorStrategy(ErrorStrategy.SKIP)
                                .build())
                        .build())
                .node(OutputNode.builder().id("output").build())
                .edge("input", "process")
                .edge("process", "output")
                .build();

        WorkflowResult result = createExecutor(registry).execute(workflow, "test");

        // Process node was skipped — absent from results, output gets empty resolved input
        assertNotNull(result.getOutput());
        assertFalse(result.getNodeResults().containsKey("process"));
    }

    @Test
    @DisplayName("default error strategy is FAIL_FAST when no hooks")
    void defaultFailFastNoHooks() {
        NodeExecutorRegistry registry = new NodeExecutorRegistry();
        registry.register(new InputExecutor());
        registry.register(new NodeExecutor<LlmNode>() {
            @Override
            public Object execute(LlmNode node, NodeContext ctx) {
                throw new RuntimeException("error");
            }
            @Override
            public Class<LlmNode> getNodeType() { return LlmNode.class; }
        });
        registry.register(new NodeExecutor<OutputNode>() {
            @Override
            public Object execute(OutputNode node, NodeContext ctx) { return ctx.getResolvedInput(); }
            @Override
            public Class<OutputNode> getNodeType() { return OutputNode.class; }
        });

        Workflow workflow = WorkflowBuilder.create()
                .node(InputNode.builder().id("input").build())
                .node(LlmNode.builder().id("process").promptTemplate("P").build()) // no hooks
                .node(OutputNode.builder().id("output").build())
                .edge("input", "process")
                .edge("process", "output")
                .build();

        assertThrows(RuntimeException.class, () ->
                createExecutor(registry).execute(workflow, "test"));
    }
}

