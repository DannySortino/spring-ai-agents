package com.springai.agents.natural;

import com.springai.agents.agent.Agent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Discovers and loads agents from natural language requirement documents.
 * <p>
 * Scans for {@code .md} and {@code .txt} files in:
 * <ul>
 *   <li>{@code classpath:agents/}</li>
 *   <li>{@code classpath:agent-specs/}</li>
 * </ul>
 * <p>
 * Each file represents one agent. The filename (without extension) becomes
 * the agent name.
 *
 * <h3>Example: src/main/resources/agents/customer-support.md</h3>
 * <pre>
 * # Customer Support Agent
 *
 * This agent handles customer inquiries for our e-commerce platform.
 *
 * ## What it should do
 *
 * 1. First, analyze the customer's message to understand:
 *    - Their sentiment (happy, frustrated, neutral)
 *    - The type of inquiry (order status, refund, product question)
 *
 * 2. Based on the analysis:
 *    - For order status: Look up the order and provide status
 *    - For refunds: Check eligibility and process or escalate
 *    - For product questions: Provide helpful information
 *
 * 3. Always:
 *    - Be polite and professional
 *    - Apologize if the customer is frustrated
 *    - Offer to escalate to a human if needed
 *
 * ## Tone
 * Friendly but professional. Use the customer's name if available.
 * </pre>
 */
@Slf4j
@RequiredArgsConstructor
public class NaturalLanguageAgentLoader {

    private static final String[] DEFAULT_LOCATIONS = {
            "classpath:agents/*.md",
            "classpath:agents/*.txt",
            "classpath:agent-specs/*.md",
            "classpath:agent-specs/*.txt"
    };

    private static final Pattern NAME_PATTERN = Pattern.compile(
            "^#\\s+(.+?)(?:\\s+Agent)?\\s*$", Pattern.MULTILINE | Pattern.CASE_INSENSITIVE);

    private final NaturalLanguageAgentBuilder builder;
    private final List<String> customLocations;

    public NaturalLanguageAgentLoader(NaturalLanguageAgentBuilder builder) {
        this(builder, List.of());
    }

    /**
     * Load all natural language agent definitions and build agents.
     *
     * @return List of agents built from requirement documents
     */
    public List<Agent> loadAgents() {
        List<Agent> agents = new ArrayList<>();
        ResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();

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
                                log.info("Built agent '{}' from natural language spec: {}", 
                                        agent.getName(), resource.getFilename());
                            }
                        } catch (Exception e) {
                            log.error("Failed to build agent from {}: {}", 
                                    resource.getFilename(), e.getMessage());
                        }
                    }
                }
            } catch (IOException e) {
                log.debug("No resources found at pattern: {}", locationPattern);
            }
        }

        log.info("Built {} agents from natural language specifications", agents.size());
        return agents;
    }

    /**
     * Load a single agent from a requirement document.
     *
     * @param resource The markdown/text resource
     * @return The built Agent
     */
    public Agent loadAgent(Resource resource) throws IOException {
        String content = resource.getContentAsString(StandardCharsets.UTF_8);
        String filename = resource.getFilename();
        
        // Extract agent name from filename or first heading
        String name = extractName(content, filename);
        
        log.debug("Building agent '{}' from requirements:\n{}", name, 
                content.substring(0, Math.min(500, content.length())) + "...");
        
        return builder.build(name, content);
    }

    /**
     * Build an agent directly from a requirements string.
     *
     * @param name         Agent name
     * @param requirements Natural language requirements
     * @return The built Agent
     */
    public Agent buildFromRequirements(String name, String requirements) {
        return builder.build(name, requirements);
    }

    private String extractName(String content, String filename) {
        // Try to extract from first markdown heading
        Matcher matcher = NAME_PATTERN.matcher(content);
        if (matcher.find()) {
            String name = matcher.group(1).trim().toLowerCase()
                    .replaceAll("[^a-z0-9]+", "-")
                    .replaceAll("^-|-$", "");
            if (!name.isEmpty()) {
                return name;
            }
        }
        
        // Fall back to filename
        if (filename != null) {
            return filename.replaceAll("\\.(md|txt)$", "")
                    .toLowerCase()
                    .replaceAll("[^a-z0-9]+", "-");
        }
        
        return "unnamed-agent";
    }
}
