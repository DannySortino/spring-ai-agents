package com.springai.agents.yaml;

import com.springai.agents.agent.Agent;
import com.springai.agents.workflow.Workflow;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("YamlAgentLoader")
class YamlAgentLoaderTest {

    @Test
    @DisplayName("loads agent from YAML string")
    void loadsFromString() {
        String yaml = """
            name: test-agent
            description: A test agent
            workflows:
              - name: test-flow
                description: Test workflow
                nodes:
                  - id: input
                    type: input
                  - id: process
                    type: llm
                    prompt: "Process: {input}"
                    systemPrompt: "You are helpful"
                  - id: output
                    type: output
                edges:
                  - from: input
                    to: process
                  - from: process
                    to: output
            """;

        YamlAgentLoader loader = new YamlAgentLoader();
        Agent agent = loader.loadAgentFromString(yaml);

        assertEquals("test-agent", agent.getName());
        assertEquals("A test agent", agent.getDescription());

        List<Workflow> workflows = agent.buildWorkflows();
        assertEquals(1, workflows.size());

        Workflow workflow = workflows.get(0);
        assertEquals("test-flow", workflow.getName());
        assertEquals(3, workflow.size());
    }

    @Test
    @DisplayName("validates required fields")
    void validatesRequiredFields() {
        String yamlNoName = """
            description: Missing name
            workflows:
              - name: flow
                nodes:
                  - id: input
                    type: input
                  - id: output
                    type: output
                edges:
                  - from: input
                    to: output
            """;

        YamlAgentLoader loader = new YamlAgentLoader();
        assertThrows(IllegalArgumentException.class, () -> 
            loader.loadAgentFromString(yamlNoName));
    }

    @Test
    @DisplayName("validates workflow has input and output nodes")
    void validatesInputOutputNodes() {
        String yamlNoInput = """
            name: test
            description: Missing input node
            workflows:
              - name: flow
                nodes:
                  - id: process
                    type: llm
                    prompt: test
                  - id: output
                    type: output
                edges:
                  - from: process
                    to: output
            """;

        YamlAgentLoader loader = new YamlAgentLoader();
        assertThrows(IllegalArgumentException.class, () -> 
            loader.loadAgentFromString(yamlNoInput));
    }

    @Test
    @DisplayName("supports all node types")
    void supportsAllNodeTypes() {
        String yaml = """
            name: multi-node-agent
            description: Agent with all node types
            workflows:
              - name: flow
                nodes:
                  - id: input
                    type: input
                  - id: llm-node
                    type: llm
                    prompt: "Analyze: {input}"
                    systemPrompt: "Be helpful"
                  - id: rest-node
                    type: rest
                    method: GET
                    url: "https://api.example.com/{input}"
                  - id: context-node
                    type: context
                    contextKey: user_id
                    contextValue: "12345"
                  - id: output
                    type: output
                edges:
                  - from: input
                    to: llm-node
                  - from: input
                    to: rest-node
                  - from: input
                    to: context-node
                  - from: llm-node
                    to: output
                  - from: rest-node
                    to: output
                  - from: context-node
                    to: output
            """;

        YamlAgentLoader loader = new YamlAgentLoader();
        Agent agent = loader.loadAgentFromString(yaml);
        
        List<Workflow> workflows = agent.buildWorkflows();
        assertEquals(1, workflows.size());
        assertEquals(5, workflows.get(0).size());
    }

    @Test
    @DisplayName("supports error strategy configuration")
    void supportsErrorStrategy() {
        String yaml = """
            name: error-handling-agent
            description: Agent with error handling
            workflows:
              - name: flow
                nodes:
                  - id: input
                    type: input
                  - id: risky-node
                    type: llm
                    prompt: "Process: {input}"
                    errorStrategy: CONTINUE_WITH_DEFAULT
                    defaultValue: "fallback value"
                  - id: output
                    type: output
                edges:
                  - from: input
                    to: risky-node
                  - from: risky-node
                    to: output
            """;

        YamlAgentLoader loader = new YamlAgentLoader();
        Agent agent = loader.loadAgentFromString(yaml);
        
        assertNotNull(agent);
        assertEquals("error-handling-agent", agent.getName());
    }

    @Test
    @DisplayName("supports multiple workflows")
    void supportsMultipleWorkflows() {
        String yaml = """
            name: multi-workflow-agent
            description: Agent with multiple workflows
            workflows:
              - name: flow-one
                description: First workflow
                nodes:
                  - id: input
                    type: input
                  - id: output
                    type: output
                edges:
                  - from: input
                    to: output
              - name: flow-two
                description: Second workflow
                nodes:
                  - id: input
                    type: input
                  - id: process
                    type: llm
                    prompt: "Do something"
                  - id: output
                    type: output
                edges:
                  - from: input
                    to: process
                  - from: process
                    to: output
            """;

        YamlAgentLoader loader = new YamlAgentLoader();
        Agent agent = loader.loadAgentFromString(yaml);
        
        List<Workflow> workflows = agent.buildWorkflows();
        assertEquals(2, workflows.size());
        assertEquals("flow-one", workflows.get(0).getName());
        assertEquals("flow-two", workflows.get(1).getName());
    }
}
