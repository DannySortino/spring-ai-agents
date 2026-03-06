package com.springai.agents.natural;

import com.springai.agents.agent.Agent;
import com.springai.agents.yaml.YamlAgentDefinition;
import com.springai.agents.yaml.YamlAgentBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;

import java.util.List;

/**
 * Builds agents from natural language descriptions.
 * <p>
 * Users describe what they want in plain English, and an LLM generates
 * the agent configuration automatically.
 *
 * <h3>Example Usage</h3>
 * <pre>{@code
 * // From a requirements document
 * String requirements = """
 *     I need an agent that helps with customer support.
 *     It should:
 *     1. Analyze the customer's message to understand their sentiment
 *     2. If negative, escalate to a human
 *     3. If positive or neutral, generate a helpful response
 *     4. Always be polite and professional
 *     """;
 *
 * Agent agent = naturalLanguageBuilder.build("customer-support", requirements);
 * }</pre>
 *
 * <h3>From a File</h3>
 * Place a {@code .md} or {@code .txt} file in {@code classpath:agents/}:
 * <pre>
 * # Customer Support Agent
 *
 * This agent handles customer inquiries.
 *
 * ## Behavior
 * - Analyze sentiment first
 * - Respond appropriately based on sentiment
 * - Be empathetic with frustrated customers
 * </pre>
 */
@Slf4j
@RequiredArgsConstructor
public class NaturalLanguageAgentBuilder {

    private final ChatModel chatModel;
    private final YamlAgentBuilder yamlBuilder;

    private static final String SYSTEM_PROMPT = """
        You are an expert at designing AI agent workflows.
        
        Given a natural language description of what an agent should do,
        generate a YAML configuration that defines the agent's workflow.
        
        Rules:
        1. Every workflow MUST have exactly one 'input' node and one 'output' node
        2. Use 'llm' nodes for any AI/language processing tasks
        3. Use 'rest' nodes for API calls
        4. Use 'context' nodes to store intermediate values
        5. Connect nodes with edges to form a DAG (no cycles)
        6. Nodes can run in parallel if they don't depend on each other
        7. Use clear, descriptive node IDs
        8. Write effective prompts that accomplish each step
        
        Output ONLY valid YAML, no explanation or markdown code blocks.
        
        Schema:
        ```yaml
        name: agent-name
        description: What the agent does
        workflows:
          - name: workflow-name
            description: Workflow purpose
            nodes:
              - id: input
                type: input
              - id: node-id
                type: llm|rest|tool|context
                prompt: "For LLM nodes - supports {nodeId} placeholders"
                systemPrompt: "Optional system prompt for LLM nodes"
                method: "GET|POST for REST nodes"
                url: "URL template for REST nodes"
                contextKey: "For context nodes"
                contextValue: "For context nodes"
                errorStrategy: "FAIL_FAST|CONTINUE_WITH_DEFAULT|SKIP"
                defaultValue: "Fallback value"
              - id: output
                type: output
            edges:
              - from: source-node
                to: target-node
        ```
        """;

    /**
     * Build an agent from a natural language description.
     *
     * @param name        The desired agent name
     * @param description Natural language description of what the agent should do
     * @return A fully configured Agent
     */
    public Agent build(String name, String description) {
        log.info("Building agent '{}' from natural language description", name);
        
        String userPrompt = String.format("""
            Create an agent with the following requirements:
            
            Agent Name: %s
            
            Requirements:
            %s
            
            Generate the YAML configuration:
            """, name, description);

        Prompt prompt = new Prompt(List.of(
                new SystemMessage(SYSTEM_PROMPT),
                new UserMessage(userPrompt)
        ));

        String yamlResponse = chatModel.call(prompt).getResult().getOutput().getText();
        
        // Clean up response (remove markdown code blocks if present)
        yamlResponse = cleanYamlResponse(yamlResponse);
        
        log.debug("Generated YAML for agent '{}':\n{}", name, yamlResponse);

        // Parse YAML and build agent
        LoaderOptions options = new LoaderOptions();
        Yaml yaml = new Yaml(new Constructor(YamlAgentDefinition.class, options));
        YamlAgentDefinition definition = yaml.load(yamlResponse);
        
        // Override name to ensure it matches requested name
        definition.setName(name);
        
        return yamlBuilder.build(definition);
    }

    /**
     * Build an agent from a requirements document with additional context.
     *
     * @param name         The desired agent name
     * @param requirements Natural language requirements
     * @param context      Additional context (existing tools, APIs, constraints)
     * @return A fully configured Agent
     */
    public Agent buildWithContext(String name, String requirements, String context) {
        String fullDescription = String.format("""
            %s
            
            Additional Context:
            %s
            """, requirements, context);
        
        return build(name, fullDescription);
    }

    /**
     * Refine an existing agent based on feedback.
     *
     * @param existingAgent The current agent
     * @param feedback      What to improve or change
     * @return An updated Agent
     */
    public Agent refine(Agent existingAgent, String feedback) {
        String refinementPrompt = String.format("""
            Current agent: %s
            Description: %s
            
            Feedback/Changes requested:
            %s
            
            Generate an improved version of this agent.
            """, 
            existingAgent.getName(),
            existingAgent.getDescription(),
            feedback);
        
        return build(existingAgent.getName() + "-v2", refinementPrompt);
    }

    private String cleanYamlResponse(String response) {
        // Remove markdown code blocks
        response = response.replaceAll("```yaml\\s*", "");
        response = response.replaceAll("```yml\\s*", "");
        response = response.replaceAll("```\\s*", "");
        return response.trim();
    }
}
