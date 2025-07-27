package com.springai.agent.config;

import lombok.extern.slf4j.Slf4j;
import com.springai.agent.config.AppProperties.AgentDef;
import com.springai.agent.service.AgentService;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

/**
 * Configuration class that creates Spring AI tools programmatically based on agent configuration.
 * These tools will be automatically exposed by the Spring AI MCP server.
 */
@Slf4j
@Configuration
public class SpringAiToolConfiguration implements BeanDefinitionRegistryPostProcessor, ApplicationContextAware {
    
    private AppProperties appProperties;
    private ApplicationContext applicationContext;
    
    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }
    
    /**
     * Initialize dependencies that might not be available during early bean processing
     */
    private void initializeDependencies() {
        if (appProperties == null && applicationContext != null) {
            try {
                appProperties = applicationContext.getBean(AppProperties.class);
                log.debug("Successfully initialized AppProperties dependency");
            } catch (Exception e) {
                log.debug("AppProperties not yet available: {}", e.getMessage());
            }
        }
    }
    
    @Override
    public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) throws BeansException {
        log.info("Creating Spring AI function tools from agent configuration");
        
        // Initialize dependencies
        initializeDependencies();
        
        if (appProperties == null) {
            log.info("AppProperties not yet available during bean definition registry post-processing, skipping dynamic registration");
            return;
        }
        
        if (appProperties.getAgents() == null) {
            log.info("No agents configured for Spring AI tool creation");
            return;
        }
        
        int toolsCreated = 0;
        for (AgentDef agentDef : appProperties.getAgents()) {
            if (agentDef.getMcpServer() != null && agentDef.getMcpServer().isEnabled()) {
                createAgentToolCallbackBean(registry, agentDef);
                toolsCreated++;
            }
        }
        
        log.info("Created {} Spring AI ToolCallback tools for MCP server exposure", toolsCreated);
    }
    
    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
        // No additional bean factory processing needed
    }
    
    /**
     * Creates a ToolCallback bean for the given agent definition
     * This tool will be auto-discovered by the Spring AI MCP server
     */
    private void createAgentToolCallbackBean(BeanDefinitionRegistry registry, AgentDef agentDef) {
        String toolBeanName = agentDef.getName();
        
        log.debug("Creating Spring AI ToolCallback bean '{}' for agent '{}'", toolBeanName, agentDef.getName());
        
        // Create bean definition for the ToolCallback
        BeanDefinitionBuilder builder = BeanDefinitionBuilder.genericBeanDefinition(ToolCallback.class);
        
        // Set factory method to create the tool callback
        builder.setFactoryMethodOnBean("createAgentToolCallback", "springAiToolConfiguration");
        builder.addConstructorArgValue(agentDef.getName());
        
        // Register the bean with the agent name as the bean name
        registry.registerBeanDefinition(toolBeanName, builder.getBeanDefinition());
        
        log.debug("Registered Spring AI ToolCallback bean '{}' for agent '{}'", toolBeanName, agentDef.getName());
    }
    
    /**
     * Factory method to create ToolCallback for an agent
     * This will be auto-discovered by Spring AI MCP server
     */
    public ToolCallback createAgentToolCallback(String agentName) {
        log.debug("Creating ToolCallback for agent '{}'", agentName);
        
        return new ToolCallback() {
            @Override
            public ToolDefinition getToolDefinition() {
                // Get agent definition to extract metadata
                AgentDef agentDef = getAgentDefinition(agentName);
                String description = (agentDef != null && agentDef.getMcpServer() != null && agentDef.getMcpServer().getDescription() != null) 
                    ? agentDef.getMcpServer().getDescription()
                    : "Execute " + agentName + " agent workflow";
                
                // Create input schema for the tool
                String inputSchema = """
                    {
                        "type": "object",
                        "properties": {
                            "input": {
                                "type": "string",
                                "description": "Input text for the agent"
                            }
                        },
                        "required": ["input"]
                    }
                    """;
                
                return ToolDefinition.builder()
                    .name(agentName)
                    .description(description)
                    .inputSchema(inputSchema)
                    .build();
            }
            
            @Override
            public String call(String toolInput) {
                try {
                    log.debug("Executing Spring AI tool for agent '{}' with input: {}", agentName, toolInput);
                    
                    // Get the agent service from the application context
                    @SuppressWarnings("unchecked")
                    Map<String, AgentService> agentServices = (Map<String, AgentService>) applicationContext.getBean("agentServices");
                    AgentService agentService = agentServices.get(agentName);
                    
                    if (agentService == null) {
                        log.error("Agent service '{}' not found in agentServices map", agentName);
                        return "Error: Agent service '" + agentName + "' not found";
                    }
                    
                    // Create context for the agent invocation
                    Map<String, Object> context = Map.of(
                        "toolCall", true,
                        "mcpServer", true,
                        "timestamp", System.currentTimeMillis()
                    );
                    
                    String result = agentService.invoke(toolInput, context);
                    log.debug("Spring AI tool for agent '{}' completed successfully", agentName);
                    return result;
                    
                } catch (Exception e) {
                    log.error("Error executing Spring AI tool for agent '{}': {}", agentName, e.getMessage(), e);
                    return "Error executing agent " + agentName + ": " + e.getMessage();
                }
            }
        };
    }
    
    /**
     * Helper method to get agent definition by name
     */
    private AgentDef getAgentDefinition(String agentName) {
        if (appProperties != null && appProperties.getAgents() != null) {
            return appProperties.getAgents().stream()
                .filter(agent -> agentName.equals(agent.getName()))
                .findFirst()
                .orElse(null);
        }
        return null;
    }
    
}
