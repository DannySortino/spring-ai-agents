package com.springai.agents.executor;

import com.springai.agents.node.ContextNode;
import com.springai.agents.node.InputNode;
import com.springai.agents.node.LlmNode;
import com.springai.agents.node.Node;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("NodeExecutorRegistry")
class NodeExecutorRegistryTest {

    private NodeExecutorRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new NodeExecutorRegistry();
    }

    @Test
    @DisplayName("registers and dispatches sync executor")
    void registerAndDispatch() {
        registry.register(new InputExecutor());

        InputNode node = InputNode.builder().id("input").build();
        NodeContext ctx = NodeContext.builder().resolvedInput("hello").build();

        Object result = registry.execute(node, ctx);
        assertEquals("hello", result);
    }

    @Test
    @DisplayName("throws on missing executor")
    void missingExecutor() {
        LlmNode node = LlmNode.builder().id("llm").promptTemplate("test").build();
        NodeContext ctx = NodeContext.builder().resolvedInput("").build();

        assertThrows(IllegalStateException.class, () -> registry.execute(node, ctx));
    }

    @Test
    @DisplayName("dispatches reactive executor natively when available")
    void reactiveNativeDispatch() {
        // ContextExecutor is sync-only, registry should auto-wrap
        registry.register(new ContextExecutor());

        ContextNode node = ContextNode.builder().id("ctx").contextText("static text").build();
        NodeContext ctx = NodeContext.builder().resolvedInput("").build();

        Mono<Object> result = registry.executeReactive(node, ctx);
        StepVerifier.create(result)
                .expectNext("static text")
                .verifyComplete();
    }

    @Test
    @DisplayName("reactive dispatch wraps sync-only executor in Mono")
    void reactiveWrapsSync() {
        registry.register(new InputExecutor());

        InputNode node = InputNode.builder().id("input").build();
        NodeContext ctx = NodeContext.builder().resolvedInput("async hello").build();

        StepVerifier.create(registry.executeReactive(node, ctx))
                .expectNext("async hello")
                .verifyComplete();
    }

    @Test
    @DisplayName("reactive dispatch returns error for missing executor")
    void reactiveMissingExecutor() {
        LlmNode node = LlmNode.builder().id("llm").promptTemplate("test").build();
        NodeContext ctx = NodeContext.builder().resolvedInput("").build();

        StepVerifier.create(registry.executeReactive(node, ctx))
                .expectError(IllegalStateException.class)
                .verify();
    }

    @Test
    @DisplayName("hasExecutor reports correctly")
    void hasExecutor() {
        assertFalse(registry.hasExecutor(InputNode.class));
        registry.register(new InputExecutor());
        assertTrue(registry.hasExecutor(InputNode.class));
    }
}

