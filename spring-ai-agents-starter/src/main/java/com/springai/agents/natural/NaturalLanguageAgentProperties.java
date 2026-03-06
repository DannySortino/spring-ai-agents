package com.springai.agents.natural;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Configuration properties for natural language agent definitions.
 *
 * <pre>{@code
 * spring:
 *   ai:
 *     agents:
 *       natural:
 *         enabled: true
 *         locations:
 *           - classpath:specs/*.md
 *           - file:/etc/agent-specs/*.txt
 * }</pre>
 */
@Data
@ConfigurationProperties(prefix = "spring.ai.agents.natural")
public class NaturalLanguageAgentProperties {

    /**
     * Whether to enable natural language agent auto-discovery.
     * When enabled, .md and .txt files in agents/ directories are
     * processed by an LLM to generate agent configurations.
     * Default: false (requires explicit opt-in)
     */
    private boolean enabled = false;

    /**
     * Additional locations to scan for requirement documents.
     * Supports classpath: and file: prefixes with wildcard patterns.
     */
    private List<String> locations;
}
