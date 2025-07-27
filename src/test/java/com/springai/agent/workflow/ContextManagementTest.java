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
        WorkflowStepDef inputNode = new WorkflowStepDef();
        inputNode.setNodeId("input_node");
        inputNode.setPrompt("Receive input: {input}");
        
        WorkflowStepDef step1 = new WorkflowStepDef();
        step1.setNodeId("step1");
        step1.setPrompt("Process: {input_node}");
        step1.setDependsOn(List.of("input_node"));
        
        WorkflowStepDef step2 = new WorkflowStepDef();
        step2.setNodeId("step2");
        step2.setPrompt("Analyze: {step1}");
        step2.setDependsOn(List.of("step1"));
        
        WorkflowStepDef outputNode = new WorkflowStepDef();
        outputNode.setNodeId("output_node");
        outputNode.setPrompt("Final output: {step2}");
        outputNode.setDependsOn(List.of("step2"));

        graphWorkflow = new GraphWorkflow(chatModel, List.of(inputNode, step1, step2, outputNode), mcpToolService);

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
        ContextManagementDef contextMgmt = new ContextManagementDef();
        contextMgmt.setClearBefore(true);
        contextMgmt.setPreserveKeys(List.of("agentName", "systemPrompt"));
            
        WorkflowStepDef inputNode = new WorkflowStepDef();
        inputNode.setNodeId("input_node");
        inputNode.setPrompt("Receive input: {input}");
        
        WorkflowStepDef step1 = new WorkflowStepDef();
        step1.setNodeId("step1");
        step1.setPrompt("Process: {input_node}");
        step1.setDependsOn(List.of("input_node"));
        
        WorkflowStepDef step2 = new WorkflowStepDef();
        step2.setNodeId("step2");
        step2.setPrompt("Analyze: {step1}");
        step2.setDependsOn(List.of("step1"));
        step2.setContextManagement(contextMgmt);
        
        WorkflowStepDef outputNode = new WorkflowStepDef();
        outputNode.setNodeId("output_node");
        outputNode.setPrompt("Final output: {step2}");
        outputNode.setDependsOn(List.of("step2"));

        graphWorkflow = new GraphWorkflow(chatModel, List.of(inputNode, step1, step2, outputNode), mcpToolService);

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
        ContextManagementDef contextMgmt = new ContextManagementDef();
        contextMgmt.setClearAfter(true);
        contextMgmt.setPreserveKeys(List.of("agentName"));
            
        WorkflowStepDef inputNode = new WorkflowStepDef();
        inputNode.setNodeId("input_node");
        inputNode.setPrompt("Receive input: {input}");
        
        WorkflowStepDef step1 = new WorkflowStepDef();
        step1.setNodeId("step1");
        step1.setPrompt("Process: {input_node}");
        step1.setDependsOn(List.of("input_node"));
        step1.setContextManagement(contextMgmt);
        
        WorkflowStepDef step2 = new WorkflowStepDef();
        step2.setNodeId("step2");
        step2.setPrompt("Analyze: {step1}");
        step2.setDependsOn(List.of("step1"));
        
        WorkflowStepDef outputNode = new WorkflowStepDef();
        outputNode.setNodeId("output_node");
        outputNode.setPrompt("Final output: {step2}");
        outputNode.setDependsOn(List.of("step2"));

        graphWorkflow = new GraphWorkflow(chatModel, List.of(inputNode, step1, step2, outputNode), mcpToolService);

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
        ContextManagementDef contextMgmt = new ContextManagementDef();
        contextMgmt.setRemoveKeys(List.of("customKey", "temporaryData"));
            
        WorkflowStepDef inputNode = new WorkflowStepDef();
        inputNode.setNodeId("input_node");
        inputNode.setPrompt("Receive input: {input}");
        
        WorkflowStepDef step1 = new WorkflowStepDef();
        step1.setNodeId("step1");
        step1.setPrompt("Process: {input_node}");
        step1.setDependsOn(List.of("input_node"));
        
        WorkflowStepDef step2 = new WorkflowStepDef();
        step2.setNodeId("step2");
        step2.setPrompt("Analyze: {step1}");
        step2.setDependsOn(List.of("step1"));
        step2.setContextManagement(contextMgmt);
        
        WorkflowStepDef outputNode = new WorkflowStepDef();
        outputNode.setNodeId("output_node");
        outputNode.setPrompt("Final output: {step2}");
        outputNode.setDependsOn(List.of("step2"));

        graphWorkflow = new GraphWorkflow(chatModel, List.of(inputNode, step1, step2, outputNode), mcpToolService);

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
        ContextManagementDef clearAfterConfig = new ContextManagementDef();
        clearAfterConfig.setClearAfter(true);
        clearAfterConfig.setPreserveKeys(List.of("agentName"));
            
        ContextManagementDef removeKeysConfig = new ContextManagementDef();
        removeKeysConfig.setRemoveKeys(List.of("temporaryData"));
            
        WorkflowStepDef inputNode = new WorkflowStepDef();
        inputNode.setNodeId("input_node");
        inputNode.setPrompt("Receive input: {input}");
        
        WorkflowStepDef step1 = new WorkflowStepDef();
        step1.setNodeId("step1");
        step1.setPrompt("Process: {input_node}");
        step1.setDependsOn(List.of("input_node"));
        
        WorkflowStepDef step2 = new WorkflowStepDef();
        step2.setNodeId("step2");
        step2.setPrompt("Analyze: {step1}");
        step2.setDependsOn(List.of("step1"));
        step2.setContextManagement(clearAfterConfig);
        
        WorkflowStepDef step3 = new WorkflowStepDef();
        step3.setNodeId("step3");
        step3.setPrompt("Finalize: {step2}");
        step3.setDependsOn(List.of("step2"));
        step3.setContextManagement(removeKeysConfig);
        
        WorkflowStepDef outputNode = new WorkflowStepDef();
        outputNode.setNodeId("output_node");
        outputNode.setPrompt("Final output: {step3}");
        outputNode.setDependsOn(List.of("step3"));

        graphWorkflow = new GraphWorkflow(chatModel, List.of(inputNode, step1, step2, step3, outputNode), mcpToolService);

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
        ContextManagementDef contextMgmt = new ContextManagementDef();
        contextMgmt.setClearAfter(true);
        contextMgmt.setPreserveKeys(List.of("agentName"));
            
        WorkflowStepDef inputNode = new WorkflowStepDef();
        inputNode.setNodeId("input_node");
        inputNode.setPrompt("Receive input: {input}");
        
        WorkflowStepDef toolStep = new WorkflowStepDef();
        toolStep.setNodeId("tool_step");
        toolStep.setTool("testTool");
        toolStep.setDependsOn(List.of("input_node"));
        toolStep.setContextManagement(contextMgmt);
        
        WorkflowStepDef outputNode = new WorkflowStepDef();
        outputNode.setNodeId("output_node");
        outputNode.setPrompt("Final output: {tool_step}");
        outputNode.setDependsOn(List.of("tool_step"));

        graphWorkflow = new GraphWorkflow(chatModel, List.of(inputNode, toolStep, outputNode), mcpToolService);

        // When: Execute workflow with initial context
        Map<String, Object> context = new HashMap<>();
        context.put("customKey", "customValue");
        context.put("agentName", "testAgent");
        
        String result = graphWorkflow.execute("test input", context);

        // Then: Tool should be called and context managed
        assertNotNull(result);
        verify(mcpToolService).callTool(eq("testTool"), eq("Mock response"), any(Map.class));
        assertFalse(context.containsKey("customKey")); // Should be cleared after tool execution
        assertTrue(context.containsKey("agentName")); // Should be preserved
    }

    @Test
    void testPreserveKeysOnly_ClearsEverythingExceptSpecifiedKeys() {
        // Given: A workflow that clears context but preserves specific keys
        ContextManagementDef contextMgmt = new ContextManagementDef();
        contextMgmt.setClearAfter(true);
        contextMgmt.setPreserveKeys(List.of("systemPrompt", "isFirstInvocation"));
            
        WorkflowStepDef inputNode = new WorkflowStepDef();
        inputNode.setNodeId("input_node");
        inputNode.setPrompt("Receive input: {input}");
        
        WorkflowStepDef step = new WorkflowStepDef();
        step.setNodeId("step1");
        step.setPrompt("Process: {input_node}");
        step.setDependsOn(List.of("input_node"));
        step.setContextManagement(contextMgmt);
        
        WorkflowStepDef outputNode = new WorkflowStepDef();
        outputNode.setNodeId("output_node");
        outputNode.setPrompt("Final output: {step1}");
        outputNode.setDependsOn(List.of("step1"));

        graphWorkflow = new GraphWorkflow(chatModel, List.of(inputNode, step, outputNode), mcpToolService);

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
        ContextManagementDef contextMgmt = new ContextManagementDef();
        contextMgmt.setClearAfter(true);
        contextMgmt.setRemoveKeys(List.of("onlyThis"));
            
        WorkflowStepDef inputNode = new WorkflowStepDef();
        inputNode.setNodeId("input_node");
        inputNode.setPrompt("Receive input: {input}");
        
        WorkflowStepDef step = new WorkflowStepDef();
        step.setNodeId("step1");
        step.setPrompt("Process: {input_node}");
        step.setDependsOn(List.of("input_node"));
        step.setContextManagement(contextMgmt);
        
        WorkflowStepDef outputNode = new WorkflowStepDef();
        outputNode.setNodeId("output_node");
        outputNode.setPrompt("Final output: {step1}");
        outputNode.setDependsOn(List.of("step1"));

        graphWorkflow = new GraphWorkflow(chatModel, List.of(inputNode, step, outputNode), mcpToolService);

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


