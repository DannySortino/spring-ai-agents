package com.springai.agent.workflow;

import com.springai.agent.config.AppProperties.WorkflowStepDef;
import com.springai.agent.service.McpToolService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ChainWorkflowTest {

    @Test
    void testChainWorkflowCreation() {
        // Given
        List<String> prompts = List.of("First prompt", "Second prompt");
        
        // When
        ChainWorkflow workflow = new ChainWorkflow(null, prompts);
        
        // Then
        assertNotNull(workflow);
    }

    @Test
    void testChainWorkflowWithEmptyPrompts() {
        // Given
        List<String> prompts = List.of();
        
        // When
        ChainWorkflow workflow = new ChainWorkflow(null, prompts);
        
        // Then
        assertNotNull(workflow);
    }

    @Test
    void testChainWorkflowWithNullPrompts() {
        // Given/When/Then
        assertDoesNotThrow(() -> new ChainWorkflow(null, null));
    }
    
    @Test
    void testDataDependencyManagement() {
        // Given
        // Create a workflow step with dependencies
        WorkflowStepDef step = new WorkflowStepDef();
        step.setId("final-step");
        step.setTool("summarize-tool");
        step.setDependencies(List.of("user-info", "billing-info"));
        step.setResultMapping(Map.of(
            "userInfo", "user-info",
            "billingInfo", "billing-info"
        ));
        
        // Create a mock McpToolService that verifies dependencies are passed correctly
        McpToolService mockToolService = mock(McpToolService.class);
        when(mockToolService.callTool(eq("summarize-tool"), anyString(), any(Map.class)))
            .thenAnswer(invocation -> {
                // Get the context passed to the tool
                Map<String, Object> context = invocation.getArgument(2);
                
                // Verify that the dependencies were correctly mapped
                String userInfo = (String) context.get("userInfo");
                String billingInfo = (String) context.get("billingInfo");
                
                // Return a result that includes the dependency values to verify they were passed correctly
                return "Summary: " + userInfo + " with " + billingInfo;
            });
        
        // Create a workflow with just the final step (we'll pre-populate the step results)
        ChainWorkflow workflow = new ChainWorkflow(null, List.of(step), mockToolService);
        
        // When
        // Pre-populate the context with step results
        Map<String, Object> context = new HashMap<>();
        Map<String, Object> stepResults = new HashMap<>();
        stepResults.put("user-info", "User: John Doe");
        stepResults.put("billing-info", "Premium Plan");
        context.put("stepResults", stepResults);
        
        // Execute just the final step
        String result = workflow.execute("dummy input", context);
        
        // Then
        // Verify the result includes the dependency values
        assertEquals("Summary: User: John Doe with Premium Plan", result);
        
        // Verify that dependencies were correctly processed
        assertTrue(context.containsKey("userInfo"));
        assertTrue(context.containsKey("billingInfo"));
        assertEquals("User: John Doe", context.get("userInfo"));
        assertEquals("Premium Plan", context.get("billingInfo"));
    }
}
