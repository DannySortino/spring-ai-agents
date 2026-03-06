package com.springai.agents.executor;

import com.springai.agents.node.OutputNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@DisplayName("OutputExecutor")
class OutputExecutorTest {

    private ChatModel mockChatModel() {
        ChatModel chatModel = mock(ChatModel.class);
        Generation generation = new Generation(new AssistantMessage("LLM response"));
        ChatResponse response = new ChatResponse(List.of(generation));
        when(chatModel.call(any(org.springframework.ai.chat.prompt.Prompt.class))).thenReturn(response);
        return chatModel;
    }

    @Test
    @DisplayName("passes through input when no handler or prompt")
    void passthrough() {
        var executor = new OutputExecutor(mockChatModel());
        var node = OutputNode.builder().id("output").build();
        var ctx = NodeContext.builder().resolvedInput("raw output").build();

        Object result = executor.execute(node, ctx);
        assertEquals("raw output", result);
    }

    @Test
    @DisplayName("uses LLM post-processing when postProcessPrompt is set")
    void llmPostProcess() {
        ChatModel chatModel = mockChatModel();
        var executor = new OutputExecutor(chatModel);
        var node = OutputNode.builder()
                .id("output")
                .postProcessPrompt("Summarize: {analyze}")
                .build();
        var ctx = NodeContext.builder()
                .resolvedInput("")
                .dependencyResult("analyze", "detailed analysis text")
                .build();

        Object result = executor.execute(node, ctx);
        assertEquals("LLM response", result);
        verify(chatModel).call(any(org.springframework.ai.chat.prompt.Prompt.class));
    }

    @Test
    @DisplayName("custom outputHandler takes priority over postProcessPrompt")
    void outputHandlerPriority() {
        ChatModel chatModel = mockChatModel();
        var executor = new OutputExecutor(chatModel);
        var node = OutputNode.builder()
                .id("output")
                .postProcessPrompt("This should be ignored")
                .outputHandler(ctx -> "CUSTOM: " + ctx.getResolvedInput())
                .build();
        var ctx = NodeContext.builder().resolvedInput("data").build();

        Object result = executor.execute(node, ctx);
        assertEquals("CUSTOM: data", result);
        // LLM should NOT be called
        verify(chatModel, never()).call(any(org.springframework.ai.chat.prompt.Prompt.class));
    }

    @Test
    @DisplayName("outputHandler can access typed dependency results")
    void outputHandlerTypedAccess() {
        var executor = new OutputExecutor(mockChatModel());
        var node = OutputNode.builder()
                .id("output")
                .outputHandler(ctx -> {
                    String val = ctx.getDependencyResult("prev", String.class);
                    return "Formatted: " + val.toUpperCase();
                })
                .build();
        var ctx = NodeContext.builder()
                .resolvedInput("")
                .dependencyResult("prev", "hello")
                .build();

        Object result = executor.execute(node, ctx);
        assertEquals("Formatted: HELLO", result);
    }

    @Test
    @DisplayName("reports correct node type")
    void nodeType() {
        assertEquals(OutputNode.class, new OutputExecutor(mockChatModel()).getNodeType());
    }
}

