package com.springai.agent.service;

import com.springai.agent.config.McpClientConfiguration.McpClientService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.Map;
import java.util.concurrent.Callable;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class McpToolServiceTest {

    @Mock
    private McpClientService mcpClientService;

    @Mock
    private RetryService retryService;

    @Mock
    private ChatModel chatModel;

    private McpToolService mcpToolService;

    @BeforeEach
    void setUp() throws Exception {
        // Mock the mcpClientService to return empty servers map for external calls
        when(mcpClientService.getRegisteredServers()).thenReturn(Map.of());
        
        // Mock getToolSchema to return empty map (no schema available) for most tests
        lenient().when(mcpClientService.getToolSchema(anyString())).thenReturn(Map.of());
        
        // Mock the retryService to execute operations directly without retry
        when(retryService.executeWithRetry(any(), any(), any())).thenAnswer(invocation -> {
            try {
                Callable<?> operation = invocation.getArgument(0);
                return operation.call();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        
        // Mock the chatModel to return a simple response following the correct chain
        AssistantMessage mockAssistantMessage = mock(AssistantMessage.class);
        Generation mockGeneration = mock(Generation.class);
        ChatResponse mockChatResponse = mock(ChatResponse.class);
        
        lenient().when(mockAssistantMessage.getText()).thenReturn("formatted input");
        lenient().when(mockGeneration.getOutput()).thenReturn(mockAssistantMessage);
        lenient().when(mockChatResponse.getResult()).thenReturn(mockGeneration);
        lenient().when(chatModel.call(any(Prompt.class))).thenReturn(mockChatResponse);
        
        mcpToolService = new McpToolService(mcpClientService, retryService, chatModel);
    }

    @Test
    void testCallToolWithInvoiceTool() {
        // Given
        String toolName = "invoiceTool";
        String input = "test input";

        // When
        String result = mcpToolService.callTool(toolName, input);

        // Then
        assertNotNull(result);
        assertTrue(result.contains("Tool 'invoiceTool' executed with input: test input"));
        assertTrue(result.contains("Generic response generated"));
    }

    @Test
    void testCallToolWithDisputeTool() {
        // Given
        String toolName = "disputeTool";
        String input = "transaction123";

        // When
        String result = mcpToolService.callTool(toolName, input);

        // Then
        assertNotNull(result);
        assertTrue(result.contains("Tool 'disputeTool' executed with input: transaction123"));
        assertTrue(result.contains("Generic response generated"));
    }

    @Test
    void testCallToolWithPaymentTool() {
        // Given
        String toolName = "paymentTool";
        String input = "payment data";

        // When
        String result = mcpToolService.callTool(toolName, input);

        // Then
        assertNotNull(result);
        assertTrue(result.contains("Tool 'paymentTool' executed with input: payment data"));
        assertTrue(result.contains("Generic response generated"));
    }

    @Test
    void testCallToolWithUnknownTool() {
        // Given
        String toolName = "unknownTool";
        String input = "test input";

        // When
        String result = mcpToolService.callTool(toolName, input);

        // Then
        assertNotNull(result);
        assertTrue(result.contains("Tool 'unknownTool' executed with input: test input"));
        assertTrue(result.contains("Generic response generated"));
    }

    @Test
    void testCallToolWithContext() {
        // Given
        String toolName = "invoiceTool";
        String input = "Get invoice for {invoiceId} from {userId}";
        Map<String, Object> context = Map.of(
            "invoiceId", "INV-123",
            "userId", "user456"
        );

        // When
        String result = mcpToolService.callTool(toolName, input, context);

        // Then
        assertNotNull(result);
        assertTrue(result.contains("Tool 'invoiceTool' executed with input: Get invoice for INV-123 from user456"));
        assertTrue(result.contains("Generic response generated"));
        // The context should have been processed and replaced in the input
    }

    @Test
    void testCallToolWithEmptyContext() {
        // Given
        String toolName = "invoiceTool";
        String input = "test input";
        Map<String, Object> context = Map.of();

        // When
        String result = mcpToolService.callTool(toolName, input, context);

        // Then
        assertNotNull(result);
        assertTrue(result.contains("Tool 'invoiceTool' executed with input: test input"));
        assertTrue(result.contains("Generic response generated"));
    }

    @Test
    void testCallToolWithNullInput() {
        // Given
        String toolName = "invoiceTool";
        String input = null;

        // When
        String result = mcpToolService.callTool(toolName, input);

        // Then
        assertNotNull(result);
        assertTrue(result.contains("Tool 'invoiceTool' executed with input: null"));
        assertTrue(result.contains("Generic response generated"));
    }

    @Test
    void testCallToolWithEmptyInput() {
        // Given
        String toolName = "invoiceTool";
        String input = "";

        // When
        String result = mcpToolService.callTool(toolName, input);

        // Then
        assertNotNull(result);
        assertTrue(result.contains("Tool 'invoiceTool' executed with input: "));
        assertTrue(result.contains("Generic response generated"));
    }

    @Test
    void testCallToolCaseInsensitive() {
        // Given
        String toolName = "INVOICETOOL"; // uppercase
        String input = "test input";

        // When
        String result = mcpToolService.callTool(toolName, input);

        // Then
        assertNotNull(result);
        assertTrue(result.contains("Tool 'INVOICETOOL' executed with input: test input"));
        assertTrue(result.contains("Generic response generated"));
    }

    @Test
    void testCallToolWithLLMInputFormatting() {
        // Given: A tool with schema that should trigger LLM formatting
        String toolName = "schemaBasedTool";
        String input = "Create invoice for customer John with amount 100";
        Map<String, Object> context = Map.of("userId", "user123");
        
        // Mock tool schema to trigger LLM formatting
        Map<String, Object> toolSchema = Map.of(
            "name", "schemaBasedTool",
            "description", "A tool that creates invoices",
            "inputSchema", "{\"type\":\"object\",\"properties\":{\"customer\":{\"type\":\"string\"},\"amount\":{\"type\":\"number\"}}}"
        );
        when(mcpClientService.getToolSchema("schemaBasedTool")).thenReturn(toolSchema);

        // When
        String result = mcpToolService.callTool(toolName, input, context);

        // Then
        assertNotNull(result);
        // Verify that the LLM was called for input formatting
        verify(chatModel, times(1)).call(any(Prompt.class));
        // The result should contain the formatted input from the LLM
        assertTrue(result.contains("Tool 'schemaBasedTool' executed with input: formatted input"));
        assertTrue(result.contains("Generic response generated"));
    }

    @Test
    void testCallToolWithEmergencyResponseTool() {
        // Given
        String toolName = "emergencyResponseTool";
        String input = "critical incident";

        // When
        String result = mcpToolService.callTool(toolName, input);

        // Then
        assertNotNull(result);
        assertTrue(result.contains("Tool 'emergencyResponseTool' executed with input: critical incident"));
        assertTrue(result.contains("Generic response generated"));
    }

    @Test
    void testCallToolWithPerformanceAnalysisTool() {
        // Given
        String toolName = "performanceAnalysisTool";
        String input = "system metrics";

        // When
        String result = mcpToolService.callTool(toolName, input);

        // Then
        assertNotNull(result);
        assertTrue(result.contains("Tool 'performanceAnalysisTool' executed with input: system metrics"));
        assertTrue(result.contains("Generic response generated"));
    }

    @Test
    void testCallToolWithContextVariableReplacement() {
        // Given
        String toolName = "dataTool";
        String input = "Process data for {userId} with {sessionId}";
        Map<String, Object> context = Map.of(
            "userId", "user123",
            "sessionId", "session456"
        );

        // When
        String result = mcpToolService.callTool(toolName, input, context);

        // Then
        assertNotNull(result);
        assertTrue(result.contains("Tool 'dataTool' executed with input: Process data for user123 with session456"));
        assertTrue(result.contains("Generic response generated"));
    }
}


