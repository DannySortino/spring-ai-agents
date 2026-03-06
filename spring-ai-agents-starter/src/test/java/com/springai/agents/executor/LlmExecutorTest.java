package com.springai.agents.executor;

import com.springai.agents.node.LlmNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

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

    private ChatModel mockChatModelWithUsage(String response, long promptTokens, long completionTokens) {
        ChatModel chatModel = mock(ChatModel.class);
        Generation generation = new Generation(new AssistantMessage(response));
        
        Usage usage = mock(Usage.class);
        when(usage.getPromptTokens()).thenReturn(promptTokens);
        when(usage.getCompletionTokens()).thenReturn(completionTokens);
        when(usage.getTotalTokens()).thenReturn(promptTokens + completionTokens);
        
        ChatResponseMetadata metadata = mock(ChatResponseMetadata.class);
        when(metadata.getUsage()).thenReturn(usage);
        
        ChatResponse chatResponse = mock(ChatResponse.class);
        when(chatResponse.getResult()).thenReturn(generation);
        when(chatResponse.getMetadata()).thenReturn(metadata);
        
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
                .executionContext(new ConcurrentHashMap<>(Map.of("currentInput", "user data")))
                .build();

        Object result = executor.execute(node, ctx);

        assertEquals("analysis result", result);
        verify(chatModel).call(any(Prompt.class));
    }

    @Test
    @DisplayName("sends system prompt as proper SystemMessage")
    void systemPromptAsMessage() {
        ChatModel chatModel = mockChatModel("response");
        var executor = new LlmExecutor(chatModel);
        var node = LlmNode.builder()
                .id("node")
                .promptTemplate("Query: {input}")
                .systemPrompt("You are a helpful assistant.")
                .build();
        var ctx = NodeContext.builder()
                .resolvedInput("")
                .executionContext(new ConcurrentHashMap<>(Map.of("currentInput", "test")))
                .build();

        executor.execute(node, ctx);

        var captor = org.mockito.ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel).call(captor.capture());
        
        List<Message> messages = captor.getValue().getInstructions();
        assertEquals(2, messages.size(), "Should have system + user messages");
        
        // First message should be system
        assertEquals(MessageType.SYSTEM, messages.get(0).getMessageType());
        assertEquals("You are a helpful assistant.", messages.get(0).getText());
        
        // Second message should be user
        assertEquals(MessageType.USER, messages.get(1).getMessageType());
        assertTrue(messages.get(1).getText().contains("Query:"));
    }

    @Test
    @DisplayName("sends only user message when no system prompt")
    void noSystemPrompt() {
        ChatModel chatModel = mockChatModel("response");
        var executor = new LlmExecutor(chatModel);
        var node = LlmNode.builder()
                .id("node")
                .promptTemplate("Query: {input}")
                .build();
        var ctx = NodeContext.builder()
                .resolvedInput("")
                .executionContext(new ConcurrentHashMap<>(Map.of("currentInput", "test")))
                .build();

        executor.execute(node, ctx);

        var captor = org.mockito.ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel).call(captor.capture());
        
        List<Message> messages = captor.getValue().getInstructions();
        assertEquals(1, messages.size(), "Should have only user message");
        assertEquals(MessageType.USER, messages.get(0).getMessageType());
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
                .executionContext(new ConcurrentHashMap<>())
                .build();

        executor.execute(node, ctx);

        var captor = org.mockito.ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel).call(captor.capture());
        
        // Get the user message text from instructions
        List<Message> messages = captor.getValue().getInstructions();
        assertEquals(1, messages.size());
        String userMessageText = messages.get(0).getText();
        assertTrue(userMessageText.contains("first-result"));
        assertTrue(userMessageText.contains("second-result"));
    }

    @Test
    @DisplayName("extracts and stores usage metadata when available")
    void extractsUsageMetadata() {
        ChatModel chatModel = mockChatModelWithUsage("response", 100, 50);
        var executor = new LlmExecutor(chatModel);
        var node = LlmNode.builder()
                .id("analyze")
                .promptTemplate("Test prompt")
                .build();
        var executionContext = new ConcurrentHashMap<String, Object>();
        var ctx = NodeContext.builder()
                .resolvedInput("")
                .executionContext(executionContext)
                .build();

        executor.execute(node, ctx);

        // Verify usage was stored in execution context
        assertTrue(executionContext.containsKey("analyze_usage"), "Usage should be stored in context");
        Usage storedUsage = (Usage) executionContext.get("analyze_usage");
        assertEquals(100L, storedUsage.getPromptTokens());
        assertEquals(50L, storedUsage.getCompletionTokens());
        assertEquals(150L, storedUsage.getTotalTokens());
    }

    @Test
    @DisplayName("handles unmodifiable execution context gracefully")
    void handlesUnmodifiableContext() {
        ChatModel chatModel = mockChatModelWithUsage("response", 100, 50);
        var executor = new LlmExecutor(chatModel);
        var node = LlmNode.builder()
                .id("analyze")
                .promptTemplate("Test prompt")
                .build();
        // Use unmodifiable map - should not throw
        var ctx = NodeContext.builder()
                .resolvedInput("")
                .executionContext(Map.of())
                .build();

        // Should not throw despite unmodifiable map
        Object result = executor.execute(node, ctx);
        assertEquals("response", result);
    }

    @Test
    @DisplayName("reports correct node type")
    void nodeType() {
        assertEquals(LlmNode.class, new LlmExecutor(mockChatModel("")).getNodeType());
    }
}
