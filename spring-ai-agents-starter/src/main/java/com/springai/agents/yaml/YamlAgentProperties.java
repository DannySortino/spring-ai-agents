package com.springai.agents.yaml;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Configuration properties for YAML-based agent definitions.
 *
 * <pre>{@code
 * spring:
 *   ai:
 *     agents:
 *       yaml:
 *         enabled: true
 *         locations:
 *           - classpath:my-agents/*.yaml
 *           - file:/etc/agents/*.yaml
 * }</pre>
 */
@Data
@ConfigurationProperties(prefix = "spring.ai.agents.yaml")
public class YamlAgentProperties {

    /**
     * Whether to enable YAML agent auto-discovery.
     * Default: true
     */
    private boolean enabled = true;

    /**
     * Additional locations to scan for YAML agent definitions.
     * Supports classpath: and file: prefixes with wildcard patterns.
     */
    private List<String> locations;
}
