package com.springai.agents.yaml;

import com.springai.agents.agent.Agent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Discovers and loads YAML agent definitions from the classpath.
 * <p>
 * By default, scans for {@code *.yaml} and {@code *.yml} files in:
 * <ul>
 *   <li>{@code classpath:agents/} — primary location</li>
 *   <li>{@code classpath:spring-ai-agents/} — alternative location</li>
 * </ul>
 * <p>
 * Custom locations can be configured via {@code spring.ai.agents.yaml.locations}.
 *
 * <h3>Example Usage</h3>
 * <pre>{@code
 * // Place agent definition at: src/main/resources/agents/my-agent.yaml
 * // The loader will automatically discover and build it at startup.
 * }</pre>
 */
@Slf4j
@RequiredArgsConstructor
public class YamlAgentLoader {

    private static final String[] DEFAULT_LOCATIONS = {
            "classpath:agents/*.yaml",
            "classpath:agents/*.yml",
            "classpath:spring-ai-agents/*.yaml",
            "classpath:spring-ai-agents/*.yml"
    };

    private final YamlAgentBuilder agentBuilder;
    private final List<String> customLocations;

    public YamlAgentLoader() {
        this(new YamlAgentBuilder(), List.of());
    }

    public YamlAgentLoader(List<String> customLocations) {
        this(new YamlAgentBuilder(), customLocations);
    }

    /**
     * Load all YAML agent definitions and build Agent instances.
     *
     * @return List of agents built from YAML definitions
     */
    public List<Agent> loadAgents() {
        List<Agent> agents = new ArrayList<>();
        ResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();

        // Combine default and custom locations
        List<String> allLocations = new ArrayList<>(List.of(DEFAULT_LOCATIONS));
        allLocations.addAll(customLocations);

        for (String locationPattern : allLocations) {
            try {
                Resource[] resources = resolver.getResources(locationPattern);
                for (Resource resource : resources) {
                    if (resource.exists() && resource.isReadable()) {
                        try {
                            Agent agent = loadAgent(resource);
                            if (agent != null) {
                                agents.add(agent);
                                log.info("Loaded YAML agent: '{}' from {}", 
                                        agent.getName(), resource.getFilename());
                            }
                        } catch (Exception e) {
                            log.error("Failed to load agent from {}: {}", 
                                    resource.getFilename(), e.getMessage());
                        }
                    }
                }
            } catch (IOException e) {
                log.debug("No resources found at pattern: {}", locationPattern);
            }
        }

        log.info("Loaded {} YAML-defined agents", agents.size());
        return agents;
    }

    /**
     * Load a single agent from a YAML resource.
     *
     * @param resource The YAML resource to load
     * @return The built Agent, or null if loading fails
     */
    public Agent loadAgent(Resource resource) throws IOException {
        try (InputStream is = resource.getInputStream()) {
            LoaderOptions options = new LoaderOptions();
            Yaml yaml = new Yaml(new Constructor(YamlAgentDefinition.class, options));
            YamlAgentDefinition definition = yaml.load(is);
            
            if (definition == null || definition.getName() == null) {
                log.warn("Invalid agent definition in {}: missing required fields", 
                        resource.getFilename());
                return null;
            }
            
            validateDefinition(definition, resource.getFilename());
            return agentBuilder.build(definition);
        }
    }

    /**
     * Load an agent from a YAML string (useful for testing or dynamic creation).
     *
     * @param yamlContent The YAML content as a string
     * @return The built Agent
     */
    public Agent loadAgentFromString(String yamlContent) {
        LoaderOptions options = new LoaderOptions();
        Yaml yaml = new Yaml(new Constructor(YamlAgentDefinition.class, options));
        YamlAgentDefinition definition = yaml.load(yamlContent);
        
        validateDefinition(definition, "<inline>");
        return agentBuilder.build(definition);
    }

    private void validateDefinition(YamlAgentDefinition def, String source) {
        if (def.getName() == null || def.getName().isBlank()) {
            throw new IllegalArgumentException("Agent name is required in " + source);
        }
        if (def.getWorkflows() == null || def.getWorkflows().isEmpty()) {
            throw new IllegalArgumentException("At least one workflow is required in " + source);
        }
        
        for (var workflow : def.getWorkflows()) {
            if (workflow.getName() == null || workflow.getName().isBlank()) {
                throw new IllegalArgumentException("Workflow name is required in " + source);
            }
            if (workflow.getNodes() == null || workflow.getNodes().isEmpty()) {
                throw new IllegalArgumentException(
                        "Workflow '" + workflow.getName() + "' must have at least one node in " + source);
            }
            
            // Validate node types
            boolean hasInput = false, hasOutput = false;
            for (var node : workflow.getNodes()) {
                if (node.getId() == null || node.getType() == null) {
                    throw new IllegalArgumentException(
                            "Node must have id and type in workflow '" + workflow.getName() + "' in " + source);
                }
                if ("input".equalsIgnoreCase(node.getType())) hasInput = true;
                if ("output".equalsIgnoreCase(node.getType())) hasOutput = true;
            }
            
            if (!hasInput) {
                throw new IllegalArgumentException(
                        "Workflow '" + workflow.getName() + "' must have an input node in " + source);
            }
            if (!hasOutput) {
                throw new IllegalArgumentException(
                        "Workflow '" + workflow.getName() + "' must have an output node in " + source);
            }
        }
    }
}
