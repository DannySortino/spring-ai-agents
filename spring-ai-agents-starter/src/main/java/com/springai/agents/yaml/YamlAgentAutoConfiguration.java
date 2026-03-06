package com.springai.agents.yaml;

import com.springai.agents.agent.Agent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import java.util.List;

/**
 * Auto-configuration for YAML-based agent definitions.
 * <p>
 * Automatically discovers and registers agents defined in YAML files when
 * {@code spring.ai.agents.yaml.enabled=true} (default: true).
 * <p>
 * YAML files are loaded from:
 * <ul>
 *   <li>{@code classpath:agents/*.yaml}</li>
 *   <li>{@code classpath:agents/*.yml}</li>
 *   <li>{@code classpath:spring-ai-agents/*.yaml}</li>
 *   <li>{@code classpath:spring-ai-agents/*.yml}</li>
 *   <li>Any paths specified in {@code spring.ai.agents.yaml.locations}</li>
 * </ul>
 *
 * <h3>Configuration</h3>
 * <pre>{@code
 * spring:
 *   ai:
 *     agents:
 *       yaml:
 *         enabled: true                    # Enable YAML agent loading (default: true)
 *         locations:                       # Additional locations to scan
 *           - classpath:custom-agents/*.yaml
 *           - file:/etc/agents/*.yaml
 * }</pre>
 */
@Slf4j
@AutoConfiguration
@EnableConfigurationProperties(YamlAgentProperties.class)
@ConditionalOnProperty(name = "spring.ai.agents.yaml.enabled", havingValue = "true", matchIfMissing = true)
public class YamlAgentAutoConfiguration {

    @Bean
    public YamlAgentBuilder yamlAgentBuilder() {
        return new YamlAgentBuilder();
    }

    @Bean
    public YamlAgentLoader yamlAgentLoader(YamlAgentBuilder builder, YamlAgentProperties properties) {
        List<String> customLocations = properties.getLocations() != null 
                ? properties.getLocations() 
                : List.of();
        return new YamlAgentLoader(builder, customLocations);
    }

    /**
     * Load all YAML-defined agents and register them as Spring beans.
     * These agents will be automatically discovered by {@code AgentRegistry}.
     */
    @Bean
    public List<Agent> yamlAgents(YamlAgentLoader loader) {
        List<Agent> agents = loader.loadAgents();
        if (!agents.isEmpty()) {
            log.info("Registered {} YAML-defined agents: {}", 
                    agents.size(),
                    agents.stream().map(Agent::getName).toList());
        }
        return agents;
    }
}
