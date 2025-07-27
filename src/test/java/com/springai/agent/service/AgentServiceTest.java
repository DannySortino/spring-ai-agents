package com.springai.agent.service;

import com.springai.agent.workflow.Workflow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AgentServiceTest {

    @Mock
    private Workflow mockWorkflow;

    private AgentService agentService;

    @BeforeEach
    void setUp() {
        agentService = new AgentService("testAgent", "You are a test assistant", mockWorkflow);
    }

    @Test
    void testAgentServiceCreation() {
        // Given/When/Then
        assertNotNull(agentService);
        assertEquals("testAgent", agentService.getName());
        assertEquals("You are a test assistant", agentService.getSystemPrompt());
        assertEquals(mockWorkflow, agentService.getWorkflow());
    }

    @Test
    void testInvokeCallsWorkflowExecute() {
        // Given
        String input = "test input";
        String expectedOutput = "workflow result";
        when(mockWorkflow.execute(eq(input), any(Map.class))).thenReturn(expectedOutput);

        // When
        String result = agentService.invoke(input);

        // Then
        assertEquals(expectedOutput, result);
        verify(mockWorkflow, times(1)).execute(eq(input), any(Map.class));
    }

    @Test
    void testInvokeWithNullInput() {
        // Given
        String expectedOutput = "null input result";
        when(mockWorkflow.execute(eq(null), any(Map.class))).thenReturn(expectedOutput);

        // When
        String result = agentService.invoke(null);

        // Then
        assertEquals(expectedOutput, result);
        verify(mockWorkflow, times(1)).execute(eq(null), any(Map.class));
    }

    @Test
    void testInvokeWithEmptyInput() {
        // Given
        String input = "";
        String expectedOutput = "empty input result";
        when(mockWorkflow.execute(eq(input), any(Map.class))).thenReturn(expectedOutput);

        // When
        String result = agentService.invoke(input);

        // Then
        assertEquals(expectedOutput, result);
        verify(mockWorkflow, times(1)).execute(eq(input), any(Map.class));
    }

    @Test
    void testGetName() {
        // Given/When/Then
        assertEquals("testAgent", agentService.getName());
    }

    @Test
    void testGetWorkflow() {
        // Given/When/Then
        assertEquals(mockWorkflow, agentService.getWorkflow());
    }

    @Test
    void testAgentServiceWithDifferentName() {
        // Given
        AgentService differentAgent = new AgentService("differentAgent", "You are a different assistant", mockWorkflow);

        // When/Then
        assertEquals("differentAgent", differentAgent.getName());
        assertEquals("You are a different assistant", differentAgent.getSystemPrompt());
        assertEquals(mockWorkflow, differentAgent.getWorkflow());
    }
}


