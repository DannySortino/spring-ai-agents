package com.springai.agents.natural;

import com.springai.agents.agent.Agent;
import com.springai.agents.yaml.YamlAgentBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@DisplayName("NaturalLanguageAgentBuilder")
class NaturalLanguageAgentBuilderTest {

    private ChatModel chatModel;
    private NaturalLanguageAgentBuilder builder;

    @BeforeEach
    void setUp() {
        chatModel = mock(ChatModel.class);
        builder = new NaturalLanguageAgentBuilder(chatModel, new YamlAgentBuilder());
    }

    @Test
    @DisplayName("builds agent from natural language description")
    void buildsFromDescription() {
        String generatedYaml = """
            name: test-agent
            description: A test agent built from natural language
            workflows:
              - name: main
                description: Main workflow
                nodes:
                  - id: input
                    type: input
                  - id: process
                    type: llm
                    prompt: "Process the input: {input}"
                    systemPrompt: "You are helpful"
                  - id: output
                    type: output
                edges:
                  - from: input
                    to: process
                  - from: process
                    to: output
            """;

        mockChatResponse(generatedYaml);

        Agent agent = builder.build("test-agent", "Create an agent that processes user input helpfully");

        assertNotNull(agent);
        assertEquals("test-agent", agent.getName());
        verify(chatModel).call(any(Prompt.class));
    }

    @Test
    @DisplayName("cleans markdown code blocks from LLM response")
    void cleansMarkdownCodeBlocks() {
        String responseWithCodeBlock = """
            ```yaml
            name: test-agent
            description: Test
            workflows:
              - name: main
                nodes:
                  - id: input
                    type: input
                  - id: output
                    type: output
                edges:
                  - from: input
                    to: output
            ```
            """;

        mockChatResponse(responseWithCodeBlock);

        Agent agent = builder.build("test-agent", "Simple agent");

        assertNotNull(agent);
        assertEquals("test-agent", agent.getName());
    }

    @Test
    @DisplayName("builds agent with additional context")
    void buildsWithContext() {
        String generatedYaml = """
            name: api-agent
            description: Agent that calls an API
            workflows:
              - name: main
                nodes:
                  - id: input
                    type: input
                  - id: call-api
                    type: rest
                    method: GET
                    url: "https://api.example.com/data"
                  - id: output
                    type: output
                edges:
                  - from: input
                    to: call-api
                  - from: call-api
                    to: output
            """;

        mockChatResponse(generatedYaml);

        Agent agent = builder.buildWithContext(
                "api-agent",
                "Create an agent that fetches data",
                "Available API: https://api.example.com/data (GET)"
        );

        assertNotNull(agent);
        assertEquals("api-agent", agent.getName());
    }

    @Test
    @DisplayName("refines existing agent based on feedback")
    void refinesAgent() {
        // First, create a mock existing agent
        Agent existingAgent = mock(Agent.class);
        when(existingAgent.getName()).thenReturn("my-agent");
        when(existingAgent.getDescription()).thenReturn("Original description");

        String improvedYaml = """
            name: my-agent-v2
            description: Improved agent
            workflows:
              - name: main
                nodes:
                  - id: input
                    type: input
                  - id: enhanced-process
                    type: llm
                    prompt: "Enhanced processing: {input}"
                  - id: output
                    type: output
                edges:
                  - from: input
                    to: enhanced-process
                  - from: enhanced-process
                    to: output
            """;

        mockChatResponse(improvedYaml);

        Agent refined = builder.refine(existingAgent, "Add better error handling");

        assertNotNull(refined);
        assertEquals("my-agent-v2", refined.getName());
    }

    @Test
    @DisplayName("sends proper system prompt to LLM")
    void sendsSystemPrompt() {
        String generatedYaml = """
            name: test
            description: Test
            workflows:
              - name: main
                nodes:
                  - id: input
                    type: input
                  - id: output
                    type: output
                edges:
                  - from: input
                    to: output
            """;

        mockChatResponse(generatedYaml);

        builder.build("test", "Simple agent");

        var captor = org.mockito.ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel).call(captor.capture());

        Prompt prompt = captor.getValue();
        assertEquals(2, prompt.getInstructions().size()); // System + User
        assertTrue(prompt.getInstructions().get(0).getText().contains("expert at designing AI agent workflows"));
    }

    private void mockChatResponse(String content) {
        Generation generation = new Generation(new AssistantMessage(content));
        ChatResponse response = new ChatResponse(List.of(generation));
        when(chatModel.call(any(Prompt.class))).thenReturn(response);
    }
}
