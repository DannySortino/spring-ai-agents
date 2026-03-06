package com.springai.agents.workflow;

import com.springai.agents.executor.*;
import com.springai.agents.node.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ReactiveWorkflowExecutor")
class ReactiveWorkflowExecutorTest {

    private NodeExecutorRegistry registry;
    private ReactiveWorkflowExecutor executor;

    @BeforeEach
    void setUp() {
        registry = new NodeExecutorRegistry();
        registry.register(new InputExecutor());
        registry.register(new ContextExecutor());
        registry.register(new NodeExecutor<LlmNode>() {
            @Override
            public Object execute(LlmNode node, NodeContext context) {
                return "LLM[" + context.getResolvedInput() + "]";
            }
            @Override
            public Class<LlmNode> getNodeType() { return LlmNode.class; }
        });
        registry.register(new NodeExecutor<OutputNode>() {
            @Override
            public Object execute(OutputNode node, NodeContext context) {
                return context.getResolvedInput();
            }
            @Override
            public Class<OutputNode> getNodeType() { return OutputNode.class; }
        });

        executor = new ReactiveWorkflowExecutor(registry);
    }

    @Test
    @DisplayName("executes sequential workflow reactively")
    void sequential() {
        Workflow workflow = WorkflowBuilder.create()
                .node(InputNode.builder().id("input").build())
                .node(LlmNode.builder().id("process").promptTemplate("Do: {input}").build())
                .node(OutputNode.builder().id("output").build())
                .edge("input", "process")
                .edge("process", "output")
                .build();

        StepVerifier.create(executor.execute(workflow, "hello"))
                .assertNext(result -> {
                    assertEquals("LLM[hello]", result.getOutput());
                    assertEquals(3, result.getNodeResults().size());
                    assertTrue(result.getDurationMs() >= 0);
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("executes parallel workflow reactively")
    void parallel() {
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

        StepVerifier.create(executor.execute(workflow, "test"))
                .assertNext(result -> {
                    assertFalse(result.getOutput().isEmpty());
                    assertEquals(4, result.getNodeResults().size());
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("hooks fire in reactive mode")
    void hooksInReactive() {
        AtomicBoolean beforeFired = new AtomicBoolean(false);
        AtomicBoolean afterFired = new AtomicBoolean(false);

        NodeHooks hooks = NodeHooks.builder()
                .beforeExecute(ctx -> beforeFired.set(true))
                .afterExecute((ctx, result) -> afterFired.set(true))
                .build();

        Workflow workflow = WorkflowBuilder.create()
                .node(InputNode.builder().id("input").hooks(hooks).build())
                .node(OutputNode.builder().id("output").build())
                .edge("input", "output")
                .build();

        StepVerifier.create(executor.execute(workflow, "test"))
                .assertNext(result -> {
                    assertTrue(beforeFired.get());
                    assertTrue(afterFired.get());
                })
                .verifyComplete();
    }
}

