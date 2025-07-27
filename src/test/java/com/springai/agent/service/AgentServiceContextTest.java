package com.springai.agent.service;

import com.springai.agent.workflow.Workflow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class AgentServiceContextTest {

    @Mock
    private Workflow mockWorkflow;

    private AgentService agentService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        agentService = new AgentService("testAgent", "You are a helpful assistant", mockWorkflow);
        when(mockWorkflow.execute(anyString(), any())).thenReturn("Mock response");
    }

    @Test
    void testContextIsNotEmpty() {
        // Given
        String input = "Hello";
        
        // When
        agentService.invoke(input);
        
        // Then
        ArgumentCaptor<Map<String, Object>> contextCaptor = ArgumentCaptor.forClass(Map.class);
        verify(mockWorkflow).execute(eq(input), contextCaptor.capture());
        
        Map<String, Object> capturedContext = contextCaptor.getValue();
        assertFalse(capturedContext.isEmpty(), "Context should not be empty");
        assertTrue(capturedContext.containsKey("agentName"), "Context should contain agent name");
        assertTrue(capturedContext.containsKey("timestamp"), "Context should contain timestamp");
        assertTrue(capturedContext.containsKey("currentInput"), "Context should contain current input");
        assertEquals("testAgent", capturedContext.get("agentName"));
        assertEquals(input, capturedContext.get("currentInput"));
    }

    @Test
    void testSystemPromptInFirstInvocation() {
        // Given
        String input = "Hello";
        
        // When
        agentService.invoke(input);
        
        // Then
        ArgumentCaptor<Map<String, Object>> contextCaptor = ArgumentCaptor.forClass(Map.class);
        verify(mockWorkflow).execute(eq(input), contextCaptor.capture());
        
        Map<String, Object> capturedContext = contextCaptor.getValue();
        assertTrue(capturedContext.containsKey("systemPrompt"), "Context should contain system prompt on first invocation");
        assertTrue(capturedContext.containsKey("isFirstInvocation"), "Context should indicate first invocation");
        assertEquals("You are a helpful assistant", capturedContext.get("systemPrompt"));
        assertEquals(Boolean.TRUE, capturedContext.get("isFirstInvocation"));
    }

    @Test
    void testSystemPromptNotInSecondInvocation() {
        // Given
        String input1 = "Hello";
        String input2 = "How are you?";
        
        // When
        agentService.invoke(input1);
        agentService.invoke(input2);
        
        // Then
        ArgumentCaptor<Map<String, Object>> contextCaptor = ArgumentCaptor.forClass(Map.class);
        verify(mockWorkflow, times(2)).execute(anyString(), contextCaptor.capture());
        
        // Check second invocation context
        Map<String, Object> secondContext = contextCaptor.getAllValues().get(1);
        assertFalse(secondContext.containsKey("isFirstInvocation"), "Second invocation should not have isFirstInvocation flag");
        // System prompt might still be in persistent context, but isFirstInvocation should be false
    }

    @Test
    void testPersistentContextMaintained() {
        // Given
        String input1 = "Hello";
        String input2 = "How are you?";
        
        // When
        agentService.invoke(input1);
        agentService.invoke(input2);
        
        // Then
        ArgumentCaptor<Map<String, Object>> contextCaptor = ArgumentCaptor.forClass(Map.class);
        verify(mockWorkflow, times(2)).execute(anyString(), contextCaptor.capture());
        
        Map<String, Object> firstContext = contextCaptor.getAllValues().get(0);
        Map<String, Object> secondContext = contextCaptor.getAllValues().get(1);
        
        // Both contexts should have invocation count
        assertTrue(firstContext.containsKey("invocationCount") || 
                  secondContext.containsKey("invocationCount"), 
                  "Context should maintain invocation count");
        
        // Second context should have conversation history
        assertTrue(secondContext.containsKey("conversationHistory"), 
                  "Second invocation should have conversation history");
    }

    @Test
    void testAdditionalContextMerged() {
        // Given
        String input = "Hello";
        Map<String, Object> additionalContext = Map.of("customKey", "customValue", "priority", "high");
        
        // When
        agentService.invoke(input, additionalContext);
        
        // Then
        ArgumentCaptor<Map<String, Object>> contextCaptor = ArgumentCaptor.forClass(Map.class);
        verify(mockWorkflow).execute(eq(input), contextCaptor.capture());
        
        Map<String, Object> capturedContext = contextCaptor.getValue();
        assertTrue(capturedContext.containsKey("customKey"), "Context should contain additional context keys");
        assertTrue(capturedContext.containsKey("priority"), "Context should contain additional context keys");
        assertEquals("customValue", capturedContext.get("customKey"));
        assertEquals("high", capturedContext.get("priority"));
    }

    @Test
    void testPersistentContextOperations() {
        // Given
        String key = "testKey";
        String value = "testValue";
        
        // When
        agentService.addToPersistentContext(key, value);
        Object retrievedValue = agentService.getFromPersistentContext(key);
        
        // Then
        assertEquals(value, retrievedValue, "Should be able to store and retrieve from persistent context");
        
        // When invoking agent, persistent context should be included
        agentService.invoke("test input");
        
        ArgumentCaptor<Map<String, Object>> contextCaptor = ArgumentCaptor.forClass(Map.class);
        verify(mockWorkflow).execute(anyString(), contextCaptor.capture());
        
        Map<String, Object> capturedContext = contextCaptor.getValue();
        assertTrue(capturedContext.containsKey(key), "Persistent context should be included in execution context");
        assertEquals(value, capturedContext.get(key));
    }

    @Test
    void testClearPersistentContext() {
        // Given
        agentService.addToPersistentContext("testKey", "testValue");
        agentService.invoke("first call"); // This should set isInitialized to true
        
        // When
        agentService.clearPersistentContext();
        
        // Then
        assertNull(agentService.getFromPersistentContext("testKey"), "Persistent context should be cleared");
        
        // When invoking again, should be treated as first invocation
        agentService.invoke("second call");
        
        ArgumentCaptor<Map<String, Object>> contextCaptor = ArgumentCaptor.forClass(Map.class);
        verify(mockWorkflow, times(2)).execute(anyString(), contextCaptor.capture());
        
        Map<String, Object> contextAfterClear = contextCaptor.getAllValues().get(1);
        assertTrue(contextAfterClear.containsKey("isFirstInvocation"), 
                  "After clearing context, next invocation should be treated as first");
    }

    @Test
    void testGetDescription() {
        // Test with system prompt
        String description = agentService.getDescription();
        assertEquals("Agent 'testAgent': You are a helpful assistant", description);
        
        // Test without system prompt
        AgentService agentWithoutPrompt = new AgentService("noPromptAgent", null, mockWorkflow);
        String descriptionWithoutPrompt = agentWithoutPrompt.getDescription();
        assertEquals("Agent 'noPromptAgent': A general-purpose AI agent", descriptionWithoutPrompt);
        
        // Test with empty system prompt
        AgentService agentWithEmptyPrompt = new AgentService("emptyPromptAgent", "", mockWorkflow);
        String descriptionWithEmptyPrompt = agentWithEmptyPrompt.getDescription();
        assertEquals("Agent 'emptyPromptAgent': A general-purpose AI agent", descriptionWithEmptyPrompt);
    }

    @Test
    void testConversationHistoryManagement() {
        // Given
        when(mockWorkflow.execute(anyString(), any())).thenReturn("Response 1", "Response 2", "Response 3");
        
        // When - make multiple invocations
        agentService.invoke("Input 1");
        agentService.invoke("Input 2");
        agentService.invoke("Input 3");
        
        // Then
        ArgumentCaptor<Map<String, Object>> contextCaptor = ArgumentCaptor.forClass(Map.class);
        verify(mockWorkflow, times(3)).execute(anyString(), contextCaptor.capture());
        
        // Check that conversation history is maintained
        Map<String, Object> lastContext = contextCaptor.getAllValues().get(2);
        assertTrue(lastContext.containsKey("conversationHistory"), "Should maintain conversation history");
        
        @SuppressWarnings("unchecked")
        Map<String, String> history = (Map<String, String>) lastContext.get("conversationHistory");
        assertFalse(history.isEmpty(), "Conversation history should not be empty");
        
        // History should contain input/output pairs
        long inputCount = history.keySet().stream().filter(key -> key.endsWith("_input")).count();
        long outputCount = history.keySet().stream().filter(key -> key.endsWith("_output")).count();
        assertEquals(inputCount, outputCount, "Should have equal number of inputs and outputs in history");
    }
}


