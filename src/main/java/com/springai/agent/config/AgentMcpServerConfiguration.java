package com.springai.agent.config;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import com.springai.agent.config.AppProperties.AgentDef;
import com.springai.agent.config.AppProperties.McpServerDef;
import com.springai.agent.service.AgentService;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ApplicationListener;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Configuration that exposes agents as MCP tools through Spring AI MCP server auto-configuration.
 * This replaces the custom REST controller approach with proper Spring AI MCP server integration.
 * Each agent with MCP server configuration enabled will be exposed as an MCP tool through the
 * Spring AI MCP server auto-configuration.
 * <p>
 * Uses BeanDefinitionRegistryPostProcessor to dynamically register function beans based on
 * agent configuration instead of hardcoded @Bean methods.
 */
// DISABLED: Replaced by SpringAiToolConfiguration which uses Spring AI's built-in MCP server
// @Configuration
@Slf4j
public class AgentMcpServerConfiguration implements ApplicationListener<ApplicationReadyEvent>, BeanDefinitionRegistryPostProcessor, ApplicationContextAware {
    
    private ApplicationContext applicationContext;
    private AppProperties appProperties;
    private Map<String, AgentService> agentServices;
    
    // Store agent MCP tool information
    private final Map<String, AgentMcpToolInfo> agentMcpTools = new ConcurrentHashMap<>();
    
    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }
    
    /**
     * Initialize dependencies programmatically when they become available
     */
    private void initializeDependencies() {
        if (appProperties == null && applicationContext != null) {
            try {
                appProperties = applicationContext.getBean(AppProperties.class);
            } catch (Exception e) {
                log.debug("AppProperties not yet available: {}", e.getMessage());
            }
        }
        
        if (agentServices == null && applicationContext != null) {
            try {
                @SuppressWarnings("unchecked")
                Map<String, AgentService> agentServicesBean = (Map<String, AgentService>) applicationContext.getBean("agentServices");
                agentServices = agentServicesBean;
            } catch (Exception e) {
                log.debug("AgentServices not yet available: {}", e.getMessage());
            }
        }
    }
    
    
    /**
     * Information about an agent exposed as an MCP tool
     */
    public static class AgentMcpToolInfo {
        @Getter
        private final String agentName;
        private final AgentService agentService;
        @Getter
        private final McpServerDef serverConfig;
        @Getter
        private final Function<String, String> agentFunction;
        
        public AgentMcpToolInfo(String agentName, AgentService agentService, McpServerDef serverConfig) {
            this.agentName = agentName;
            this.agentService = agentService;
            this.serverConfig = serverConfig;
            this.agentFunction = createAgentFunction();
        }
        
        private Function<String, String> createAgentFunction() {
            return input -> {
                try {
                    log.debug("Executing Spring AI MCP tool '{}' with input: {}", agentName, input);
                    
                    // Use empty context for now - Spring AI MCP server will handle context differently
                    Map<String, Object> context = Map.of();
                    String result = agentService.invoke(input, context);
                    
                    log.debug("Spring AI MCP tool '{}' completed successfully", agentName);
                    return result;
                    
                } catch (Exception e) {
                    log.error("Error executing Spring AI MCP tool '{}': {}", agentName, e.getMessage(), e);
                    return "Error executing agent " + agentName + ": " + e.getMessage();
                }
            };
        }

        /**
         * Get the description for this agent MCP tool
         */
        public String getDescription() {
            if (serverConfig != null && serverConfig.getDescription() != null) {
                return serverConfig.getDescription();
            }
            return "Execute " + agentName + " agent workflow";
        }
    }
    
    @Override
    public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) throws BeansException {
        log.info("Dynamically registering Spring AI MCP function beans based on agent configuration");
        
        // Try to initialize dependencies - they might not be available yet during early initialization
        initializeDependencies();
        
        if (appProperties == null) {
            log.info("AppProperties not yet available during bean definition registry post-processing, skipping dynamic registration");
            return;
        }
        
        if (appProperties.getAgents() == null) {
            log.info("No agents configured for dynamic MCP function bean registration");
            return;
        }
        
        int registeredBeans = 0;
        for (AgentDef agentDef : appProperties.getAgents()) {
            if (agentDef.getMcpServer() != null && agentDef.getMcpServer().isEnabled()) {
                registerAgentFunctionBean(registry, agentDef);
                registeredBeans++;
            }
        }
        
        log.info("Dynamically registered {} Spring AI MCP function beans", registeredBeans);
    }
    
    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
        // No additional bean factory processing needed
        log.debug("Bean factory post-processing completed for AgentMcpServerConfiguration");
    }
    
    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        log.info("Processing agent configurations for Spring AI MCP tool exposure");
        
        // Ensure dependencies are initialized
        initializeDependencies();
        
        if (appProperties == null) {
            log.warn("AppProperties not available during application ready event");
            return;
        }
        
        if (appProperties.getAgents() == null) {
            log.info("No agents configured");
            return;
        }
        
        int mcpToolCount = 0;
        for (AgentDef agentDef : appProperties.getAgents()) {
            if (agentDef.getMcpServer() != null && agentDef.getMcpServer().isEnabled()) {
                createAgentMcpTool(agentDef);
                mcpToolCount++;
            }
        }
        
        log.info("Exposed {} agents as Spring AI MCP tools", mcpToolCount);
        
        // Log all exposed MCP tools
        agentMcpTools.forEach((agentName, toolInfo) -> {
            log.info("Agent '{}' exposed as Spring AI MCP tool: {}", 
                agentName, toolInfo.getDescription());
        });
    }
    
    /**
     * Register a function bean for an agent dynamically
     */
    private void registerAgentFunctionBean(BeanDefinitionRegistry registry, AgentDef agentDef) {
        String agentName = agentDef.getName();
        String beanName = agentName + "Tool";
        
        log.info("Registering dynamic function bean '{}' for agent '{}'", beanName, agentName);
        
        // Create bean definition for the agent function
        BeanDefinitionBuilder beanDefinitionBuilder = BeanDefinitionBuilder
            .genericBeanDefinition(Function.class)
            .setFactoryMethodOnBean("createAgentFunction", "agentMcpServerConfiguration")
            .addConstructorArgValue(agentName);
        
        // Register the bean definition
        registry.registerBeanDefinition(beanName, beanDefinitionBuilder.getBeanDefinition());
        
        log.info("Successfully registered dynamic function bean '{}' for agent '{}'", beanName, agentName);
    }
    
    /**
     * Factory method to create agent functions - called by dynamically registered beans
     * Uses standard Function<String, String> signature for Spring AI MCP server compatibility
     */
    public Function<String, String> createAgentFunction(String agentName) {
        // Ensure dependencies are initialized
        initializeDependencies();
        
        if (agentServices == null) {
            log.warn("Agent services not available, creating no-op function for '{}'", agentName);
            return input -> "Error: Agent services not available: " + agentName;
        }
        
        AgentService agentService = agentServices.get(agentName);
        if (agentService == null) {
            log.warn("Agent service '{}' not found, creating no-op function", agentName);
            return input -> "Error: Agent not found: " + agentName;
        }
        
        // Check if agent has MCP server configuration enabled
        if (appProperties != null && appProperties.getAgents() != null) {
            boolean hasMcpConfig = appProperties.getAgents().stream()
                .anyMatch(agent -> agentName.equals(agent.getName()) && 
                         agent.getMcpServer() != null && 
                         agent.getMcpServer().isEnabled());
            
            if (!hasMcpConfig) {
                log.debug("Agent '{}' does not have MCP server configuration enabled, creating no-op function", agentName);
                return input -> "Error: Agent MCP server not enabled: " + agentName;
            }
        }
        
        return input -> {
            try {
                log.debug("Executing Spring AI MCP tool '{}' with input: {}", agentName, input);
                
                // Use empty context for now - Spring AI MCP server will handle context differently
                Map<String, Object> context = Map.of();
                String result = agentService.invoke(input, context);
                
                log.debug("Spring AI MCP tool '{}' completed successfully", agentName);
                return result;
                
            } catch (Exception e) {
                log.error("Error executing Spring AI MCP tool '{}': {}", agentName, e.getMessage(), e);
                return "Error executing agent " + agentName + ": " + e.getMessage();
            }
        };
    }
    
    /**
     * Create an MCP tool for an agent
     */
    private void createAgentMcpTool(AgentDef agentDef) {
        String agentName = agentDef.getName();
        AgentService agentService = agentServices.get(agentName);
        
        if (agentService == null) {
            log.warn("Agent service '{}' not found, skipping MCP tool creation", agentName);
            return;
        }
        
        McpServerDef serverConfig = agentDef.getMcpServer();
        
        log.info("Creating Spring AI MCP tool for agent '{}'", agentName);
        
        try {
            AgentMcpToolInfo toolInfo = new AgentMcpToolInfo(agentName, agentService, serverConfig);
            agentMcpTools.put(agentName, toolInfo);
            
            log.info("Successfully created Spring AI MCP tool for agent '{}'", agentName);
            
        } catch (Exception e) {
            log.error("Failed to create Spring AI MCP tool for agent '{}': {}", agentName, e.getMessage(), e);
        }
    }
}
