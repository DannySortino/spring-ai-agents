package com.springai.agents.executor;

import com.springai.agents.node.LlmNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@DisplayName("LlmExecutor")
class LlmExecutorTest {

    private ChatModel mockChatModel(String response) {
        ChatModel chatModel = mock(ChatModel.class);
        Generation generation = new Generation(new AssistantMessage(response));
        ChatResponse chatResponse = new ChatResponse(List.of(generation));
        when(chatModel.call(any(Prompt.class))).thenReturn(chatResponse);
        return chatModel;
    }

    @Test
    @DisplayName("sends interpolated prompt to ChatModel")
    void sendsPrompt() {
        ChatModel chatModel = mockChatModel("analysis result");
        var executor = new LlmExecutor(chatModel);
        var node = LlmNode.builder()
                .id("analyze")
                .promptTemplate("Analyze: {input}")
                .build();
        var ctx = NodeContext.builder()
                .resolvedInput("user data")
                .executionContext(Map.of("currentInput", "user data"))
                .build();

        Object result = executor.execute(node, ctx);

        assertEquals("analysis result", result);
        verify(chatModel).call(any(Prompt.class));
    }

    @Test
    @DisplayName("prepends system prompt when set")
    void systemPrompt() {
        ChatModel chatModel = mockChatModel("response");
        var executor = new LlmExecutor(chatModel);
        var node = LlmNode.builder()
                .id("node")
                .promptTemplate("Query: {input}")
                .systemPrompt("You are a helpful assistant.")
                .build();
        var ctx = NodeContext.builder()
                .resolvedInput("")
                .executionContext(Map.of("currentInput", "test"))
                .build();

        executor.execute(node, ctx);

        var captor = org.mockito.ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel).call(captor.capture());
        String sentPrompt = captor.getValue().getContents();
        assertTrue(sentPrompt.contains("You are a helpful assistant."));
        assertTrue(sentPrompt.contains("Query: test"));
    }

    @Test
    @DisplayName("interpolates dependency results in prompt template")
    void interpolatesDependencies() {
        ChatModel chatModel = mockChatModel("merged");
        var executor = new LlmExecutor(chatModel);
        var node = LlmNode.builder()
                .id("merge")
                .promptTemplate("Combine {a} and {b}")
                .build();
        var ctx = NodeContext.builder()
                .resolvedInput("")
                .dependencyResult("a", "first-result")
                .dependencyResult("b", "second-result")
                .executionContext(Map.of())
                .build();

        executor.execute(node, ctx);

        var captor = org.mockito.ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel).call(captor.capture());
        String sentPrompt = captor.getValue().getContents();
        assertTrue(sentPrompt.contains("first-result"));
        assertTrue(sentPrompt.contains("second-result"));
    }

    @Test
    @DisplayName("reports correct node type")
    void nodeType() {
        assertEquals(LlmNode.class, new LlmExecutor(mockChatModel("")).getNodeType());
    }
}

