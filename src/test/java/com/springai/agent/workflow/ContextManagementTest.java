package com.springai.agent.workflow;

import com.springai.agent.config.AppProperties.WorkflowStepDef;
import com.springai.agent.config.AppProperties.ContextManagementDef;
import com.springai.agent.service.McpToolService;
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

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Test class for context management functionality in workflows.
 * 
 * Tests various scenarios including:
 * - Default behavior (keeping all context)
 * - Context clearing before steps
 * - Context clearing after steps
 * - Selective key removal and preservation
 * - Mixed scenarios with different steps having different settings
 */
@ExtendWith(MockitoExtension.class)
class ContextManagementTest {

    @Mock
    private ChatModel chatModel;
    
    @Mock
    private McpToolService mcpToolService;
    
    @Mock
    private ChatResponse chatResponse;
    
    @Mock
    private Generation generation;
    
    @Mock
    private AssistantMessage assistantMessage;

    private GraphWorkflow graphWorkflow;

    @BeforeEach
    void setUp() {
        // Setup mock responses with proper message chain
        lenient().when(assistantMessage.getText()).thenReturn("Mock response");
        lenient().when(generation.getOutput()).thenReturn(assistantMessage);
        lenient().when(chatResponse.getResult()).thenReturn(generation);
        lenient().when(chatModel.call(any(Prompt.class))).thenReturn(chatResponse);
        lenient().when(mcpToolService.callTool(anyString(), anyString(), any(Map.class))).thenReturn("Tool response");
    }

    @Test
    void testDefaultBehavior_KeepsAllContext() {
        // Given: A workflow with no context management configuration
        WorkflowStepDef step1 = WorkflowStepDef.builder()
            .nodeId("step1")
            .prompt("Step 1: {input}")
            .build();
        
        WorkflowStepDef step2 = WorkflowStepDef.builder()
            .nodeId("step2")
            .dependsOn(List.of("step1"))
            .prompt("Step 2: {customKey} - {previousResult}")
            .build();

        graphWorkflow = new GraphWorkflow(chatModel, List.of(step1, step2), mcpToolService);

        // When: Execute workflow with initial context
        Map<String, Object> context = new HashMap<>();
        context.put("customKey", "customValue");
        context.put("agentName", "testAgent");
        
        String result = graphWorkflow.execute("test input", context);

        // Then: Context should be preserved throughout execution
        assertNotNull(result);
        assertTrue(context.containsKey("customKey"));
        assertTrue(context.containsKey("agentName"));
        assertTrue(context.containsKey("previousResult"));
        assertEquals("customValue", context.get("customKey"));
        assertEquals("testAgent", context.get("agentName"));
    }

    @Test
    void testClearBefore_RemovesContextBeforeStepExecution() {
        // Given: A workflow with clearBefore configuration
        ContextManagementDef contextMgmt = ContextManagementDef.builder()
            .clearBefore(true)
            .preserveKeys(List.of("systemPrompt", "agentName"))
            .build();
            
        WorkflowStepDef step1 = WorkflowStepDef.builder()
            .nodeId("step1")
            .prompt("Step 1: {input}")
            .build();
        
        WorkflowStepDef step2 = WorkflowStepDef.builder()
            .nodeId("step2")
            .dependsOn(List.of("step1"))
            .prompt("Step 2: {customKey} - {agentName}")
            .contextManagement(contextMgmt)
            .build();

        graphWorkflow = new GraphWorkflow(chatModel, List.of(step1, step2), mcpToolService);

        // When: Execute workflow with initial context
        Map<String, Object> context = new HashMap<>();
        context.put("customKey", "customValue");
        context.put("agentName", "testAgent");
        context.put("systemPrompt", "You are a helpful assistant");
        
        String result = graphWorkflow.execute("test input", context);

        // Then: Context should be cleared before step 2, but preserved keys should remain
        assertNotNull(result);
        assertFalse(context.containsKey("customKey")); // Should be cleared
        assertTrue(context.containsKey("agentName")); // Should be preserved
        assertTrue(context.containsKey("systemPrompt")); // Should be preserved
    }

    @Test
    void testClearAfter_RemovesContextAfterStepExecution() {
        // Given: A workflow with clearAfter configuration
        ContextManagementDef contextMgmt = ContextManagementDef.builder()
            .clearAfter(true)
            .preserveKeys(List.of("agentName"))
            .build();
            
        WorkflowStepDef step1 = WorkflowStepDef.builder()
            .nodeId("step1")
            .prompt("Step 1: {input}")
            .contextManagement(contextMgmt)
            .build();
        
        WorkflowStepDef step2 = WorkflowStepDef.builder()
            .nodeId("step2")
            .dependsOn(List.of("step1"))
            .prompt("Step 2: {customKey} - {agentName}")
            .build();

        graphWorkflow = new GraphWorkflow(chatModel, List.of(step1, step2), mcpToolService);

        // When: Execute workflow with initial context
        Map<String, Object> context = new HashMap<>();
        context.put("customKey", "customValue");
        context.put("agentName", "testAgent");
        
        String result = graphWorkflow.execute("test input", context);

        // Then: Context should be cleared after step 1, but preserved keys should remain
        assertNotNull(result);
        assertFalse(context.containsKey("customKey")); // Should be cleared after step 1
        assertTrue(context.containsKey("agentName")); // Should be preserved
        assertTrue(context.containsKey("previousResult")); // Added by step 2
    }

    @Test
    void testRemoveSpecificKeys_RemovesOnlySpecifiedKeys() {
        // Given: A workflow with removeKeys configuration
        ContextManagementDef contextMgmt = ContextManagementDef.builder()
            .removeKeys(List.of("customKey", "temporaryData"))
            .build();
            
        WorkflowStepDef step1 = WorkflowStepDef.builder()
            .nodeId("step1")
            .prompt("Step 1: {input}")
            .build();
        
        WorkflowStepDef step2 = WorkflowStepDef.builder()
            .nodeId("step2")
            .dependsOn(List.of("step1"))
            .prompt("Step 2: {agentName}")
            .contextManagement(contextMgmt)
            .build();

        graphWorkflow = new GraphWorkflow(chatModel, List.of(step1, step2), mcpToolService);

        // When: Execute workflow with initial context
        Map<String, Object> context = new HashMap<>();
        context.put("customKey", "customValue");
        context.put("temporaryData", "temp");
        context.put("agentName", "testAgent");
        context.put("keepThis", "important");
        
        String result = graphWorkflow.execute("test input", context);

        // Then: Only specified keys should be removed
        assertNotNull(result);
        assertFalse(context.containsKey("customKey")); // Should be removed
        assertFalse(context.containsKey("temporaryData")); // Should be removed
        assertTrue(context.containsKey("agentName")); // Should be kept
        assertTrue(context.containsKey("keepThis")); // Should be kept
    }

    @Test
    void testMixedContextManagement_DifferentStepsWithDifferentSettings() {
        // Given: A workflow with different context management settings per step
        ContextManagementDef clearAfterConfig = ContextManagementDef.builder()
            .clearAfter(true)
            .preserveKeys(List.of("agentName"))
            .build();
            
        ContextManagementDef removeKeysConfig = ContextManagementDef.builder()
            .removeKeys(List.of("temporaryData"))
            .build();
            
        WorkflowStepDef step1 = WorkflowStepDef.builder()
            .nodeId("step1")
            .prompt("Step 1: {input}")
            .build();
        
        WorkflowStepDef step2 = WorkflowStepDef.builder()
            .nodeId("step2")
            .dependsOn(List.of("step1"))
            .prompt("Step 2: {customKey}")
            .contextManagement(clearAfterConfig)
            .build();
        
        WorkflowStepDef step3 = WorkflowStepDef.builder()
            .nodeId("step3")
            .dependsOn(List.of("step2"))
            .prompt("Step 3: {agentName}")
            .contextManagement(removeKeysConfig)
            .build();

        graphWorkflow = new GraphWorkflow(chatModel, List.of(step1, step2, step3), mcpToolService);

        // When: Execute workflow with initial context
        Map<String, Object> context = new HashMap<>();
        context.put("customKey", "customValue");
        context.put("temporaryData", "temp");
        context.put("agentName", "testAgent");
        
        String result = graphWorkflow.execute("test input", context);

        // Then: Context should be managed according to each step's configuration
        assertNotNull(result);
        assertFalse(context.containsKey("customKey")); // Cleared by step 2
        assertFalse(context.containsKey("temporaryData")); // Removed by step 3
        assertTrue(context.containsKey("agentName")); // Preserved by step 2, kept by step 3
    }

    @Test
    void testToolStepWithContextManagement() {
        // Given: A workflow with tool step and context management
        ContextManagementDef contextMgmt = ContextManagementDef.builder()
            .clearAfter(true)
            .preserveKeys(List.of("agentName"))
            .build();
            
        WorkflowStepDef toolStep = WorkflowStepDef.builder()
            .nodeId("toolStep")
            .tool("testTool")
            .contextManagement(contextMgmt)
            .build();

        graphWorkflow = new GraphWorkflow(chatModel, List.of(toolStep), mcpToolService);

        // When: Execute workflow with initial context
        Map<String, Object> context = new HashMap<>();
        context.put("customKey", "customValue");
        context.put("agentName", "testAgent");
        
        String result = graphWorkflow.execute("test input", context);

        // Then: Tool should be called and context managed
        assertNotNull(result);
        verify(mcpToolService).callTool(eq("testTool"), eq("test input"), any(Map.class));
        assertFalse(context.containsKey("customKey")); // Should be cleared after tool execution
        assertTrue(context.containsKey("agentName")); // Should be preserved
    }

    @Test
    void testPreserveKeysOnly_ClearsEverythingExceptSpecifiedKeys() {
        // Given: A workflow that clears context but preserves specific keys
        ContextManagementDef contextMgmt = ContextManagementDef.builder()
            .clearBefore(true)
            .preserveKeys(List.of("systemPrompt", "isFirstInvocation"))
            .build();
            
        WorkflowStepDef step = WorkflowStepDef.builder()
            .nodeId("step1")
            .prompt("Process: {input}")
            .contextManagement(contextMgmt)
            .build();

        graphWorkflow = new GraphWorkflow(chatModel, List.of(step), mcpToolService);

        // When: Execute workflow with rich context
        Map<String, Object> context = new HashMap<>();
        context.put("customKey", "customValue");
        context.put("temporaryData", "temp");
        context.put("systemPrompt", "You are helpful");
        context.put("isFirstInvocation", true);
        context.put("agentName", "testAgent");
        
        String result = graphWorkflow.execute("test input", context);

        // Then: Only preserved keys should remain
        assertNotNull(result);
        assertFalse(context.containsKey("customKey"));
        assertFalse(context.containsKey("temporaryData"));
        assertFalse(context.containsKey("agentName"));
        assertTrue(context.containsKey("systemPrompt"));
        assertTrue(context.containsKey("isFirstInvocation"));
    }

    @Test
    void testRemoveKeysOverridesClearSettings() {
        // Given: A workflow with both clear and removeKeys settings (removeKeys should take precedence)
        ContextManagementDef contextMgmt = ContextManagementDef.builder()
            .clearBefore(true) // This should be ignored when removeKeys is specified
            .removeKeys(List.of("onlyThis"))
            .build();
            
        WorkflowStepDef step = WorkflowStepDef.builder()
            .nodeId("step1")
            .prompt("Process: {input}")
            .contextManagement(contextMgmt)
            .build();

        graphWorkflow = new GraphWorkflow(chatModel, List.of(step), mcpToolService);

        // When: Execute workflow with context
        Map<String, Object> context = new HashMap<>();
        context.put("onlyThis", "shouldBeRemoved");
        context.put("keepThis", "shouldBeKept");
        context.put("alsoKeep", "shouldAlsoBeKept");
        
        String result = graphWorkflow.execute("test input", context);

        // Then: Only the specified key should be removed, others should remain
        assertNotNull(result);
        assertFalse(context.containsKey("onlyThis")); // Should be removed
        assertTrue(context.containsKey("keepThis")); // Should be kept
        assertTrue(context.containsKey("alsoKeep")); // Should be kept
    }
}