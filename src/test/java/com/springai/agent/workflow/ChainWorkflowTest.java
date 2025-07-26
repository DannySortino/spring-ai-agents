package com.springai.agent.workflow;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

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
}
