package com.springai.agent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Configuration
public class CustomAppPropertiesConfiguration {

    @Bean
    public AppProperties customAppProperties(Environment env) {
        AppProperties appProperties = new AppProperties();
        List<AppProperties.AgentDef> agents = new ArrayList<>();
        
        // Manually parse agents from environment
        int agentIndex = 0;
        while (env.getProperty("app.agents[" + agentIndex + "].name") != null) {
            AppProperties.AgentDef agent = parseAgent(env, agentIndex);
            agents.add(agent);
            agentIndex++;
        }
        
        appProperties.setAgents(agents);
        return appProperties;
    }
    
    private AppProperties.AgentDef parseAgent(Environment env, int agentIndex) {
        String prefix = "app.agents[" + agentIndex + "]";
        
        AppProperties.AgentDef agent = new AppProperties.AgentDef();
        agent.setName(env.getProperty(prefix + ".name"));
        agent.setModel(env.getProperty(prefix + ".model"));
        agent.setSystemPrompt(env.getProperty(prefix + ".systemPrompt"));
        
        // Parse workflow
        AppProperties.WorkflowDef workflow = parseWorkflow(env, prefix + ".workflow");
        agent.setWorkflow(workflow);
        
        // Parse mcpServer configuration
        AppProperties.McpServerDef mcpServer = parseMcpServer(env, prefix + ".mcpServer");
        agent.setMcpServer(mcpServer);
        
        return agent;
    }
    
    private AppProperties.WorkflowDef parseWorkflow(Environment env, String prefix) {
        String type = env.getProperty(prefix + ".type");
        if (type == null) {
            return null;
        }
        
        AppProperties.WorkflowDef workflow = new AppProperties.WorkflowDef();
        try {
            // Convert string to WorkflowType enum
            WorkflowType workflowType = WorkflowType.valueOf(type.toUpperCase());
            workflow.setType(workflowType);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid workflow type: " + type + ". Valid types are: " + 
                java.util.Arrays.toString(WorkflowType.values()), e);
        }
        workflow.setAggregator(env.getProperty(prefix + ".aggregator"));
        workflow.setManagerPrompt(env.getProperty(prefix + ".managerPrompt"));
        workflow.setSynthesizerPrompt(env.getProperty(prefix + ".synthesizerPrompt"));
        
        // Parse chain
        List<AppProperties.WorkflowStepDef> chain = parseChain(env, prefix + ".chain");
        workflow.setChain(chain);
        
        // Parse tasks (this is the critical part that was failing)
        List<AppProperties.TaskDef> tasks = parseTasks(env, prefix + ".tasks");
        workflow.setTasks(tasks);
        
        // Parse workers
        List<AppProperties.WorkerDef> workers = parseWorkers(env, prefix + ".workers");
        workflow.setWorkers(workers);
        
        // Parse routes
        Map<String, AppProperties.RouteDef> routes = parseRoutes(env, prefix + ".routes");
        workflow.setRoutes(routes);
        
        return workflow;
    }
    
    private List<AppProperties.WorkflowStepDef> parseChain(Environment env, String prefix) {
        List<AppProperties.WorkflowStepDef> chain = new ArrayList<>();
        int stepIndex = 0;
        
        while (env.getProperty(prefix + "[" + stepIndex + "].prompt") != null || 
               env.getProperty(prefix + "[" + stepIndex + "].tool") != null) {
            
            AppProperties.WorkflowStepDef step = new AppProperties.WorkflowStepDef();
            step.setPrompt(env.getProperty(prefix + "[" + stepIndex + "].prompt"));
            step.setTool(env.getProperty(prefix + "[" + stepIndex + "].tool"));
            
            chain.add(step);
            stepIndex++;
        }
        
        return chain.isEmpty() ? null : chain;
    }
    
    private List<AppProperties.TaskDef> parseTasks(Environment env, String prefix) {
        List<AppProperties.TaskDef> tasks = new ArrayList<>();
        int taskIndex = 0;
        
        while (env.getProperty(prefix + "[" + taskIndex + "].name") != null) {
            AppProperties.TaskDef task = new AppProperties.TaskDef();
            task.setName(env.getProperty(prefix + "[" + taskIndex + "].name"));
            
            // This is the critical fix - manually parse nested workflow
            AppProperties.WorkflowDef nestedWorkflow = parseWorkflow(env, prefix + "[" + taskIndex + "].workflow");
            task.setWorkflow(nestedWorkflow);
            
            tasks.add(task);
            taskIndex++;
        }
        
        return tasks.isEmpty() ? null : tasks;
    }
    
    private List<AppProperties.WorkerDef> parseWorkers(Environment env, String prefix) {
        List<AppProperties.WorkerDef> workers = new ArrayList<>();
        int workerIndex = 0;
        
        while (env.getProperty(prefix + "[" + workerIndex + "].name") != null) {
            AppProperties.WorkerDef worker = new AppProperties.WorkerDef();
            worker.setName(env.getProperty(prefix + "[" + workerIndex + "].name"));
            
            // Manually parse nested workflow
            AppProperties.WorkflowDef nestedWorkflow = parseWorkflow(env, prefix + "[" + workerIndex + "].workflow");
            worker.setWorkflow(nestedWorkflow);
            
            workers.add(worker);
            workerIndex++;
        }
        
        return workers.isEmpty() ? null : workers;
    }
    
    private Map<String, AppProperties.RouteDef> parseRoutes(Environment env, String prefix) {
        Map<String, AppProperties.RouteDef> routes = new HashMap<>();
        
        // Dynamically discover all route names from the configuration
        Set<String> routeNames = discoverRouteNames(env, prefix);
        
        for (String routeName : routeNames) {
            String routePrefix = prefix + "." + routeName;
            String prompt = env.getProperty(routePrefix + ".prompt");
            String tool = env.getProperty(routePrefix + ".tool");
            
            if (prompt != null || tool != null) {
                AppProperties.RouteDef route = new AppProperties.RouteDef();
                route.setPrompt(prompt);
                route.setTool(tool);
                routes.put(routeName, route);
            }
        }
        
        return routes.isEmpty() ? null : routes;
    }
    
    /**
     * Dynamically discover all route names from the configuration by examining property sources.
     * This method finds all properties that match the pattern: {prefix}.{routeName}.prompt or {prefix}.{routeName}.tool
     * and extracts the route names from them.
     */
    private Set<String> discoverRouteNames(Environment env, String prefix) {
        Set<String> routeNames = new HashSet<>();
        
        if (env instanceof ConfigurableEnvironment) {
            ConfigurableEnvironment configurableEnv = (ConfigurableEnvironment) env;
            MutablePropertySources propertySources = configurableEnv.getPropertySources();
            
            String routePrefix = prefix + ".";
            
            for (PropertySource<?> propertySource : propertySources) {
                if (propertySource.getSource() instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> source = (Map<String, Object>) propertySource.getSource();
                    
                    for (String propertyName : source.keySet()) {
                        if (propertyName.startsWith(routePrefix)) {
                            // Extract route name from property like: app.agents[0].workflow.routes.invoice.prompt
                            String remainder = propertyName.substring(routePrefix.length());
                            
                            // Find the first dot to get the route name
                            int dotIndex = remainder.indexOf('.');
                            if (dotIndex > 0) {
                                String routeName = remainder.substring(0, dotIndex);
                                String propertyType = remainder.substring(dotIndex + 1);
                                
                                // Only consider properties that end with .prompt or .tool
                                if ("prompt".equals(propertyType) || "tool".equals(propertyType)) {
                                    routeNames.add(routeName);
                                }
                            }
                        }
                    }
                }
            }
        }
        
        return routeNames;
    }
    
    private AppProperties.McpServerDef parseMcpServer(Environment env, String prefix) {
        String enabled = env.getProperty(prefix + ".enabled");
        if (enabled == null) {
            return null;
        }
        
        AppProperties.McpServerDef mcpServer = new AppProperties.McpServerDef();
        mcpServer.setEnabled(Boolean.parseBoolean(enabled));
        
        String port = env.getProperty(prefix + ".port");
        if (port != null) {
            mcpServer.setPort(Integer.parseInt(port));
        }
        
        mcpServer.setBaseUrl(env.getProperty(prefix + ".baseUrl"));
        mcpServer.setDescription(env.getProperty(prefix + ".description"));
        mcpServer.setVersion(env.getProperty(prefix + ".version", "1.0.0"));
        mcpServer.setSseEndpoint(env.getProperty(prefix + ".sseEndpoint", "/sse"));
        mcpServer.setSseMessageEndpoint(env.getProperty(prefix + ".sseMessageEndpoint", "/message"));
        
        return mcpServer;
    }
}
