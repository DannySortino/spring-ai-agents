package com.springai.agents.natural;

import com.springai.agents.agent.Agent;
import com.springai.agents.yaml.YamlAgentBuilder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Lazy;

import java.util.List;

/**
 * Auto-configuration for natural language agent definitions.
 * <p>
 * Automatically discovers requirement documents ({@code .md}, {@code .txt} files)
 * and uses an LLM to generate agent configurations from them.
 * <p>
 * Enable with {@code spring.ai.agents.natural.enabled=true}.
 *
 * <h3>Configuration</h3>
 * <pre>{@code
 * spring:
 *   ai:
 *     agents:
 *       natural:
 *         enabled: true                    # Enable natural language agent building
 *         locations:                       # Additional locations to scan
 *           - classpath:specs/*.md
 *           - file:/etc/agent-specs/*.md
 * }</pre>
 *
 * <h3>Usage</h3>
 * <p>
 * Place a markdown or text file in {@code src/main/resources/agents/}:
 *
 * <pre>
 * # Order Status Agent
 *
 * This agent helps customers check their order status.
 *
 * ## Workflow
 * 1. Extract the order number from the customer's message
 * 2. Look up the order in our system (simulate with a REST call)
 * 3. Format a friendly response with the order status
 *
 * ## Tone
 * Be helpful and concise. If no order number is found, ask for it politely.
 * </pre>
 */
@Slf4j
@AutoConfiguration
@EnableConfigurationProperties(NaturalLanguageAgentProperties.class)
@ConditionalOnProperty(name = "spring.ai.agents.natural.enabled", havingValue = "true")
@ConditionalOnBean(ChatModel.class)
public class NaturalLanguageAgentAutoConfiguration {

    @Bean
    public NaturalLanguageAgentBuilder naturalLanguageAgentBuilder(
            @Lazy ChatModel chatModel) {
        // Instantiate YamlAgentBuilder directly to avoid dependency on yaml.enabled
        return new NaturalLanguageAgentBuilder(chatModel, new YamlAgentBuilder());
    }

    @Bean
    public NaturalLanguageAgentLoader naturalLanguageAgentLoader(
            NaturalLanguageAgentBuilder builder,
            NaturalLanguageAgentProperties properties) {
        List<String> customLocations = properties.getLocations() != null 
                ? properties.getLocations() 
                : List.of();
        return new NaturalLanguageAgentLoader(builder, customLocations);
    }

    /**
     * Load all natural language agent specifications and build agents.
     * These agents will be automatically discovered by {@code AgentRegistry}.
     */
    @Bean
    public List<Agent> naturalLanguageAgents(NaturalLanguageAgentLoader loader) {
        List<Agent> agents = loader.loadAgents();
        if (!agents.isEmpty()) {
            log.info("Built {} agents from natural language specs: {}", 
                    agents.size(),
                    agents.stream().map(Agent::getName).toList());
        }
        return agents;
    }
}
