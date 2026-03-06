package com.springai.agents.executor;

import com.springai.agents.node.ContextNode;
import com.springai.agents.node.InputNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("InputExecutor & ContextExecutor")
class SimpleExecutorTest {

    @Test
    @DisplayName("InputExecutor passes through raw input")
    void inputExecutorPassthrough() {
        var executor = new InputExecutor();
        var ctx = NodeContext.builder().resolvedInput("hello world").build();
        var node = InputNode.builder().id("input").build();

        Object result = executor.execute(node, ctx);
        assertEquals("hello world", result);
    }

    @Test
    @DisplayName("InputExecutor reports correct node type")
    void inputExecutorNodeType() {
        assertEquals(InputNode.class, new InputExecutor().getNodeType());
    }

    @Test
    @DisplayName("ContextExecutor returns static text")
    void contextExecutorReturnsStaticText() {
        var executor = new ContextExecutor();
        var node = ContextNode.builder().id("ctx").contextText("static content").build();
        var ctx = NodeContext.builder().resolvedInput("").build();

        Object result = executor.execute(node, ctx);
        assertEquals("static content", result);
    }

    @Test
    @DisplayName("ContextExecutor reports correct node type")
    void contextExecutorNodeType() {
        assertEquals(ContextNode.class, new ContextExecutor().getNodeType());
    }
}

