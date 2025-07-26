package com.springai.agent.config;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

class CustomAppPropertiesConfigurationTest {

    @Test
    void testCustomConfigurationHandlesNestedWorkflows() {
        // Create mock environment with the same structure as our YAML
        Environment env = Mockito.mock(Environment.class);
        
        // Mock the complexAgent configuration
        when(env.getProperty("app.agents[0].name")).thenReturn("complexAgent");
        when(env.getProperty("app.agents[0].model")).thenReturn("openai");
        when(env.getProperty("app.agents[0].systemPrompt")).thenReturn("You are a multifunctional assistant.");
        when(env.getProperty("app.agents[0].workflow.type")).thenReturn("parallel");
        when(env.getProperty("app.agents[0].workflow.aggregator")).thenReturn("Combine results: {results}");
        
        // Mock the nested task configuration - this is the critical part
        when(env.getProperty("app.agents[0].workflow.tasks[0].name")).thenReturn("testTask");
        when(env.getProperty("app.agents[0].workflow.tasks[0].workflow.type")).thenReturn("chain");
        when(env.getProperty("app.agents[0].workflow.tasks[0].workflow.chain[0].prompt")).thenReturn("Simple test prompt: {input}");
        
        // Mock that there are no more agents
        when(env.getProperty("app.agents[1].name")).thenReturn(null);
        
        // Mock that there are no more tasks
        when(env.getProperty("app.agents[0].workflow.tasks[1].name")).thenReturn(null);
        
        // Mock that there are no more chain steps
        when(env.getProperty("app.agents[0].workflow.tasks[0].workflow.chain[1].prompt")).thenReturn(null);
        when(env.getProperty("app.agents[0].workflow.tasks[0].workflow.chain[1].tool")).thenReturn(null);
        
        // Create the custom configuration and test it
        CustomAppPropertiesConfiguration config = new CustomAppPropertiesConfiguration();
        AppProperties appProperties = config.customAppProperties(env);
        
        // Verify basic structure
        assertNotNull(appProperties);
        assertNotNull(appProperties.getAgents());
        assertEquals(1, appProperties.getAgents().size());
        
        AppProperties.AgentDef agent = appProperties.getAgents().get(0);
        assertEquals("complexAgent", agent.getName());
        assertEquals("openai", agent.getModel());
        assertEquals("You are a multifunctional assistant.", agent.getSystemPrompt());
        
        // Verify workflow is not null
        assertNotNull(agent.getWorkflow(), "Agent workflow should not be null");
        assertEquals(WorkflowType.PARALLEL, agent.getWorkflow().getType());
        assertEquals("Combine results: {results}", agent.getWorkflow().getAggregator());
        
        // Verify tasks are not null
        assertNotNull(agent.getWorkflow().getTasks(), "Workflow tasks should not be null");
        assertEquals(1, agent.getWorkflow().getTasks().size());
        
        AppProperties.TaskDef task = agent.getWorkflow().getTasks().get(0);
        assertEquals("testTask", task.getName());
        
        // This is the critical test - verify nested workflow is NOT null (the main fix)
        assertNotNull(task.getWorkflow(), "Task workflow should NOT be null - this was the main issue we fixed!");
        assertEquals(WorkflowType.CHAIN, task.getWorkflow().getType());
        
        // Verify nested chain
        assertNotNull(task.getWorkflow().getChain(), "Task workflow chain should not be null");
        assertEquals(1, task.getWorkflow().getChain().size());
        assertEquals("Simple test prompt: {input}", task.getWorkflow().getChain().get(0).getPrompt());
    }
    
    @Test
    void testCustomConfigurationHandlesSimpleAgent() {
        Environment env = Mockito.mock(Environment.class);
        
        // Mock simpleAgent configuration
        when(env.getProperty("app.agents[0].name")).thenReturn("simpleAgent");
        when(env.getProperty("app.agents[0].model")).thenReturn("openai");
        when(env.getProperty("app.agents[0].systemPrompt")).thenReturn("You are a helpful assistant.");
        when(env.getProperty("app.agents[0].workflow.type")).thenReturn("chain");
        when(env.getProperty("app.agents[0].workflow.chain[0].prompt")).thenReturn("Answer the question: {input}");
        
        // Mock no more agents or chain steps
        when(env.getProperty("app.agents[1].name")).thenReturn(null);
        when(env.getProperty("app.agents[0].workflow.chain[1].prompt")).thenReturn(null);
        when(env.getProperty("app.agents[0].workflow.chain[1].tool")).thenReturn(null);
        
        CustomAppPropertiesConfiguration config = new CustomAppPropertiesConfiguration();
        AppProperties appProperties = config.customAppProperties(env);
        
        assertNotNull(appProperties);
        assertEquals(1, appProperties.getAgents().size());
        
        AppProperties.AgentDef agent = appProperties.getAgents().get(0);
        assertEquals("simpleAgent", agent.getName());
        assertNotNull(agent.getWorkflow());
        assertEquals(WorkflowType.CHAIN, agent.getWorkflow().getType());
        assertNotNull(agent.getWorkflow().getChain());
        assertEquals(1, agent.getWorkflow().getChain().size());
        assertEquals("Answer the question: {input}", agent.getWorkflow().getChain().get(0).getPrompt());
    }
    
    @Test
    void testDynamicRouteDiscoveryWithStandardRoutes() {
        // Test the dynamic route discovery with standard billing routes
        ConfigurableEnvironment env = Mockito.mock(ConfigurableEnvironment.class);
        @SuppressWarnings("rawtypes")
        MutablePropertySources propertySources = Mockito.mock(MutablePropertySources.class);
        @SuppressWarnings("rawtypes")
        PropertySource propertySource = Mockito.mock(PropertySource.class);
        
        // Create property map with route properties
        Map<String, Object> properties = new HashMap<>();
        properties.put("app.agents[0].workflow.routes.invoice.prompt", "Handle invoice: {input}");
        properties.put("app.agents[0].workflow.routes.invoice.tool", "invoiceTool");
        properties.put("app.agents[0].workflow.routes.dispute.prompt", "Handle dispute: {input}");
        properties.put("app.agents[0].workflow.routes.dispute.tool", "disputeTool");
        properties.put("app.agents[0].workflow.routes.payment.prompt", "Handle payment: {input}");
        properties.put("app.agents[0].workflow.routes.payment.tool", "paymentTool");
        
        // Mock the environment setup
        when(env.getPropertySources()).thenReturn(propertySources);
        Mockito.doReturn(java.util.List.of(propertySource).iterator()).when(propertySources).iterator();
        when(propertySource.getSource()).thenReturn(properties);
        
        // Mock agent configuration
        when(env.getProperty("app.agents[0].name")).thenReturn("billingAgent");
        when(env.getProperty("app.agents[0].model")).thenReturn("openai");
        when(env.getProperty("app.agents[0].systemPrompt")).thenReturn("You are a billing expert.");
        when(env.getProperty("app.agents[0].workflow.type")).thenReturn("routing");
        
        // Mock route properties - these will be discovered dynamically
        when(env.getProperty("app.agents[0].workflow.routes.invoice.prompt")).thenReturn("Handle invoice: {input}");
        when(env.getProperty("app.agents[0].workflow.routes.invoice.tool")).thenReturn("invoiceTool");
        when(env.getProperty("app.agents[0].workflow.routes.dispute.prompt")).thenReturn("Handle dispute: {input}");
        when(env.getProperty("app.agents[0].workflow.routes.dispute.tool")).thenReturn("disputeTool");
        when(env.getProperty("app.agents[0].workflow.routes.payment.prompt")).thenReturn("Handle payment: {input}");
        when(env.getProperty("app.agents[0].workflow.routes.payment.tool")).thenReturn("paymentTool");
        
        // Mock no more agents
        when(env.getProperty("app.agents[1].name")).thenReturn(null);
        
        CustomAppPropertiesConfiguration config = new CustomAppPropertiesConfiguration();
        AppProperties appProperties = config.customAppProperties(env);
        
        // Verify the agent was created with routes
        assertNotNull(appProperties);
        assertEquals(1, appProperties.getAgents().size());
        
        AppProperties.AgentDef agent = appProperties.getAgents().get(0);
        assertEquals("billingAgent", agent.getName());
        assertEquals(WorkflowType.ROUTING, agent.getWorkflow().getType());
        
        // Verify routes were discovered dynamically
        Map<String, AppProperties.RouteDef> routes = agent.getWorkflow().getRoutes();
        assertNotNull(routes, "Routes should not be null");
        assertEquals(3, routes.size(), "Should discover 3 routes");
        
        // Verify specific routes
        assertTrue(routes.containsKey("invoice"), "Should contain invoice route");
        assertTrue(routes.containsKey("dispute"), "Should contain dispute route");
        assertTrue(routes.containsKey("payment"), "Should contain payment route");
        
        // Verify route details
        assertEquals("Handle invoice: {input}", routes.get("invoice").getPrompt());
        assertEquals("invoiceTool", routes.get("invoice").getTool());
        assertEquals("Handle dispute: {input}", routes.get("dispute").getPrompt());
        assertEquals("disputeTool", routes.get("dispute").getTool());
        assertEquals("Handle payment: {input}", routes.get("payment").getPrompt());
        assertEquals("paymentTool", routes.get("payment").getTool());
    }
    
    @Test
    void testDynamicRouteDiscoveryWithCustomRoutes() {
        // Test the dynamic route discovery with completely custom route names
        ConfigurableEnvironment env = Mockito.mock(ConfigurableEnvironment.class);
        @SuppressWarnings("rawtypes")
        MutablePropertySources propertySources = Mockito.mock(MutablePropertySources.class);
        @SuppressWarnings("rawtypes")
        PropertySource propertySource = Mockito.mock(PropertySource.class);
        
        // Create property map with custom route properties
        Map<String, Object> properties = new HashMap<>();
        properties.put("app.agents[0].workflow.routes.customerService.prompt", "Provide customer service: {input}");
        properties.put("app.agents[0].workflow.routes.customerService.tool", "customerServiceTool");
        properties.put("app.agents[0].workflow.routes.technicalSupport.prompt", "Provide technical support: {input}");
        properties.put("app.agents[0].workflow.routes.technicalSupport.tool", "techSupportTool");
        properties.put("app.agents[0].workflow.routes.salesInquiry.prompt", "Handle sales inquiry: {input}");
        properties.put("app.agents[0].workflow.routes.productInfo.prompt", "Provide product info: {input}");
        
        // Mock the environment setup
        when(env.getPropertySources()).thenReturn(propertySources);
        Mockito.doReturn(java.util.List.of(propertySource).iterator()).when(propertySources).iterator();
        when(propertySource.getSource()).thenReturn(properties);
        
        // Mock agent configuration
        when(env.getProperty("app.agents[0].name")).thenReturn("customAgent");
        when(env.getProperty("app.agents[0].model")).thenReturn("openai");
        when(env.getProperty("app.agents[0].systemPrompt")).thenReturn("You are a custom agent.");
        when(env.getProperty("app.agents[0].workflow.type")).thenReturn("routing");
        
        // Mock custom route properties - these demonstrate dynamic discovery
        when(env.getProperty("app.agents[0].workflow.routes.customerService.prompt")).thenReturn("Provide customer service: {input}");
        when(env.getProperty("app.agents[0].workflow.routes.customerService.tool")).thenReturn("customerServiceTool");
        when(env.getProperty("app.agents[0].workflow.routes.technicalSupport.prompt")).thenReturn("Provide technical support: {input}");
        when(env.getProperty("app.agents[0].workflow.routes.technicalSupport.tool")).thenReturn("techSupportTool");
        when(env.getProperty("app.agents[0].workflow.routes.salesInquiry.prompt")).thenReturn("Handle sales inquiry: {input}");
        when(env.getProperty("app.agents[0].workflow.routes.productInfo.prompt")).thenReturn("Provide product info: {input}");
        
        // Mock no more agents
        when(env.getProperty("app.agents[1].name")).thenReturn(null);
        
        CustomAppPropertiesConfiguration config = new CustomAppPropertiesConfiguration();
        AppProperties appProperties = config.customAppProperties(env);
        
        // Verify the agent was created with custom routes
        assertNotNull(appProperties);
        assertEquals(1, appProperties.getAgents().size());
        
        AppProperties.AgentDef agent = appProperties.getAgents().get(0);
        assertEquals("customAgent", agent.getName());
        assertEquals(WorkflowType.ROUTING, agent.getWorkflow().getType());
        
        // Verify custom routes were discovered dynamically
        Map<String, AppProperties.RouteDef> routes = agent.getWorkflow().getRoutes();
        assertNotNull(routes, "Routes should not be null");
        assertEquals(4, routes.size(), "Should discover 4 custom routes");
        
        // Verify specific custom routes
        assertTrue(routes.containsKey("customerService"), "Should contain customerService route");
        assertTrue(routes.containsKey("technicalSupport"), "Should contain technicalSupport route");
        assertTrue(routes.containsKey("salesInquiry"), "Should contain salesInquiry route");
        assertTrue(routes.containsKey("productInfo"), "Should contain productInfo route");
        
        // Verify route details
        assertEquals("Provide customer service: {input}", routes.get("customerService").getPrompt());
        assertEquals("customerServiceTool", routes.get("customerService").getTool());
        assertEquals("Provide technical support: {input}", routes.get("technicalSupport").getPrompt());
        assertEquals("techSupportTool", routes.get("technicalSupport").getTool());
        assertEquals("Handle sales inquiry: {input}", routes.get("salesInquiry").getPrompt());
        assertNull(routes.get("salesInquiry").getTool(), "salesInquiry should not have a tool");
        assertEquals("Provide product info: {input}", routes.get("productInfo").getPrompt());
        assertNull(routes.get("productInfo").getTool(), "productInfo should not have a tool");
    }
    
    @Test
    void testDynamicRouteDiscoveryWithNoRoutes() {
        // Test the dynamic route discovery with no routes configured
        Environment env = Mockito.mock(Environment.class);
        
        // Mock agent configuration without any routes
        when(env.getProperty("app.agents[0].name")).thenReturn("noRoutesAgent");
        when(env.getProperty("app.agents[0].model")).thenReturn("openai");
        when(env.getProperty("app.agents[0].systemPrompt")).thenReturn("You have no routes.");
        when(env.getProperty("app.agents[0].workflow.type")).thenReturn("routing");
        
        // Mock no more agents
        when(env.getProperty("app.agents[1].name")).thenReturn(null);
        
        CustomAppPropertiesConfiguration config = new CustomAppPropertiesConfiguration();
        AppProperties appProperties = config.customAppProperties(env);
        
        // Verify the agent was created without routes
        assertNotNull(appProperties);
        assertEquals(1, appProperties.getAgents().size());
        
        AppProperties.AgentDef agent = appProperties.getAgents().get(0);
        assertEquals("noRoutesAgent", agent.getName());
        assertEquals(WorkflowType.ROUTING, agent.getWorkflow().getType());
        
        // Verify no routes were discovered
        Map<String, AppProperties.RouteDef> routes = agent.getWorkflow().getRoutes();
        assertNull(routes, "Routes should be null when no routes are configured");
    }
    
    
    @Test
    @SuppressWarnings("unchecked")
    void testDynamicRouteDiscoveryWithComplexRouteNames() {
        // Test the dynamic route discovery with complex route names (underscores, numbers, etc.)
        ConfigurableEnvironment env = Mockito.mock(ConfigurableEnvironment.class);
        MutablePropertySources propertySources = Mockito.mock(MutablePropertySources.class);
        PropertySource<Object> propertySource = Mockito.mock(PropertySource.class);
        
        // Create property map with complex route names
        Map<String, Object> properties = new HashMap<>();
        properties.put("app.agents[0].workflow.routes.user_account_management.prompt", "Manage user account: {input}");
        properties.put("app.agents[0].workflow.routes.user_account_management.tool", "accountTool");
        properties.put("app.agents[0].workflow.routes.api_v2_integration.prompt", "Handle API v2: {input}");
        properties.put("app.agents[0].workflow.routes.api_v2_integration.tool", "apiTool");
        properties.put("app.agents[0].workflow.routes.real_time_monitoring.tool", "monitoringTool");
        
        // Mock the environment setup
        when(env.getPropertySources()).thenReturn(propertySources);
        when(propertySources.iterator()).thenReturn((java.util.Iterator) java.util.Arrays.asList(propertySource).iterator());
        when(propertySource.getSource()).thenReturn(properties);
        
        // Mock agent configuration
        when(env.getProperty("app.agents[0].name")).thenReturn("complexAgent");
        when(env.getProperty("app.agents[0].model")).thenReturn("openai");
        when(env.getProperty("app.agents[0].systemPrompt")).thenReturn("You handle complex scenarios.");
        when(env.getProperty("app.agents[0].workflow.type")).thenReturn("routing");
        
        // Mock route properties
        when(env.getProperty("app.agents[0].workflow.routes.user_account_management.prompt")).thenReturn("Manage user account: {input}");
        when(env.getProperty("app.agents[0].workflow.routes.user_account_management.tool")).thenReturn("accountTool");
        when(env.getProperty("app.agents[0].workflow.routes.api_v2_integration.prompt")).thenReturn("Handle API v2: {input}");
        when(env.getProperty("app.agents[0].workflow.routes.api_v2_integration.tool")).thenReturn("apiTool");
        when(env.getProperty("app.agents[0].workflow.routes.real_time_monitoring.tool")).thenReturn("monitoringTool");
        
        // Mock no more agents
        when(env.getProperty("app.agents[1].name")).thenReturn(null);
        
        CustomAppPropertiesConfiguration config = new CustomAppPropertiesConfiguration();
        AppProperties appProperties = config.customAppProperties(env);
        
        // Verify the agent was created with complex routes
        assertNotNull(appProperties);
        assertEquals(1, appProperties.getAgents().size());
        
        AppProperties.AgentDef agent = appProperties.getAgents().get(0);
        assertEquals("complexAgent", agent.getName());
        assertEquals(WorkflowType.ROUTING, agent.getWorkflow().getType());
        
        // Verify complex routes were discovered dynamically
        Map<String, AppProperties.RouteDef> routes = agent.getWorkflow().getRoutes();
        assertNotNull(routes, "Routes should not be null");
        assertEquals(3, routes.size(), "Should discover 3 complex routes");
        
        // Verify specific complex routes
        assertTrue(routes.containsKey("user_account_management"), "Should contain user_account_management route");
        assertTrue(routes.containsKey("api_v2_integration"), "Should contain api_v2_integration route");
        assertTrue(routes.containsKey("real_time_monitoring"), "Should contain real_time_monitoring route");
        
        // Verify route details
        assertEquals("Manage user account: {input}", routes.get("user_account_management").getPrompt());
        assertEquals("accountTool", routes.get("user_account_management").getTool());
        assertEquals("Handle API v2: {input}", routes.get("api_v2_integration").getPrompt());
        assertEquals("apiTool", routes.get("api_v2_integration").getTool());
        assertNull(routes.get("real_time_monitoring").getPrompt(), "real_time_monitoring should not have a prompt");
        assertEquals("monitoringTool", routes.get("real_time_monitoring").getTool());
    }
    
    @Test
    void testDynamicRouteDiscoveryWithEmptyRoutes() {
        // Test the dynamic route discovery with no routes
        ConfigurableEnvironment env = Mockito.mock(ConfigurableEnvironment.class);
        @SuppressWarnings("rawtypes")
        MutablePropertySources propertySources = Mockito.mock(MutablePropertySources.class);
        @SuppressWarnings("rawtypes")
        PropertySource propertySource = Mockito.mock(PropertySource.class);
        
        // Create empty property map
        Map<String, Object> properties = new HashMap<>();
        
        // Mock the environment setup
        when(env.getPropertySources()).thenReturn(propertySources);
        Mockito.doReturn(java.util.List.of(propertySource).iterator()).when(propertySources).iterator();
        when(propertySource.getSource()).thenReturn(properties);
        
        // Mock agent configuration without routes
        when(env.getProperty("app.agents[0].name")).thenReturn("noRoutesAgent");
        when(env.getProperty("app.agents[0].model")).thenReturn("openai");
        when(env.getProperty("app.agents[0].systemPrompt")).thenReturn("You have no routes.");
        when(env.getProperty("app.agents[0].workflow.type")).thenReturn("routing");
        
        // Mock no more agents
        when(env.getProperty("app.agents[1].name")).thenReturn(null);
        
        CustomAppPropertiesConfiguration config = new CustomAppPropertiesConfiguration();
        AppProperties appProperties = config.customAppProperties(env);
        
        // Verify the agent was created without routes
        assertNotNull(appProperties);
        assertEquals(1, appProperties.getAgents().size());
        
        AppProperties.AgentDef agent = appProperties.getAgents().get(0);
        assertEquals("noRoutesAgent", agent.getName());
        assertEquals(WorkflowType.ROUTING, agent.getWorkflow().getType());
        
        // Verify no routes were discovered
        Map<String, AppProperties.RouteDef> routes = agent.getWorkflow().getRoutes();
        assertNull(routes, "Routes should be null when no routes are configured");
    }
    
    @Test
    void testDynamicRouteDiscoveryWithSingleRoute() {
        // Test the dynamic route discovery with only one route (edge case)
        ConfigurableEnvironment env = Mockito.mock(ConfigurableEnvironment.class);
        @SuppressWarnings("rawtypes")
        MutablePropertySources propertySources = Mockito.mock(MutablePropertySources.class);
        @SuppressWarnings("rawtypes")
        PropertySource propertySource = Mockito.mock(PropertySource.class);
        
        // Create property map with single route
        Map<String, Object> properties = new HashMap<>();
        properties.put("app.agents[0].workflow.routes.specializedTask.prompt", "Handle specialized task: {input}");
        properties.put("app.agents[0].workflow.routes.specializedTask.tool", "specializedTool");
        
        // Mock the environment setup
        when(env.getPropertySources()).thenReturn(propertySources);
        Mockito.doReturn(java.util.List.of(propertySource).iterator()).when(propertySources).iterator();
        when(propertySource.getSource()).thenReturn(properties);
        
        // Mock agent configuration
        when(env.getProperty("app.agents[0].name")).thenReturn("singleRouteAgent");
        when(env.getProperty("app.agents[0].model")).thenReturn("openai");
        when(env.getProperty("app.agents[0].systemPrompt")).thenReturn("You handle one task.");
        when(env.getProperty("app.agents[0].workflow.type")).thenReturn("routing");
        
        // Mock route properties
        when(env.getProperty("app.agents[0].workflow.routes.specializedTask.prompt")).thenReturn("Handle specialized task: {input}");
        when(env.getProperty("app.agents[0].workflow.routes.specializedTask.tool")).thenReturn("specializedTool");
        
        // Mock no more agents
        when(env.getProperty("app.agents[1].name")).thenReturn(null);
        
        CustomAppPropertiesConfiguration config = new CustomAppPropertiesConfiguration();
        AppProperties appProperties = config.customAppProperties(env);
        
        // Verify the agent was created with single route
        assertNotNull(appProperties);
        assertEquals(1, appProperties.getAgents().size());
        
        AppProperties.AgentDef agent = appProperties.getAgents().get(0);
        assertEquals("singleRouteAgent", agent.getName());
        assertEquals(WorkflowType.ROUTING, agent.getWorkflow().getType());
        
        // Verify single route was discovered
        Map<String, AppProperties.RouteDef> routes = agent.getWorkflow().getRoutes();
        assertNotNull(routes, "Routes should not be null");
        assertEquals(1, routes.size(), "Should discover exactly 1 route");
        
        // Verify the single route
        assertTrue(routes.containsKey("specializedTask"), "Should contain specializedTask route");
        assertEquals("Handle specialized task: {input}", routes.get("specializedTask").getPrompt());
        assertEquals("specializedTool", routes.get("specializedTask").getTool());
    }
    
    @Test
    void testDynamicRouteDiscoveryIgnoresInvalidProperties() {
        // Test that the dynamic route discovery ignores invalid properties
        ConfigurableEnvironment env = Mockito.mock(ConfigurableEnvironment.class);
        @SuppressWarnings("rawtypes")
        MutablePropertySources propertySources = Mockito.mock(MutablePropertySources.class);
        @SuppressWarnings("rawtypes")
        PropertySource propertySource = Mockito.mock(PropertySource.class);
        
        // Create property map with valid and invalid properties
        Map<String, Object> properties = new HashMap<>();
        // Valid properties
        properties.put("app.agents[0].workflow.routes.validRoute.prompt", "Valid route: {input}");
        properties.put("app.agents[0].workflow.routes.validRoute.tool", "validTool");
        // Invalid properties (should be ignored)
        properties.put("app.agents[0].workflow.routes.invalidRoute.description", "This should be ignored");
        properties.put("app.agents[0].workflow.routes.invalidRoute.config", "This should also be ignored");
        properties.put("app.agents[0].workflow.routes.anotherRoute", "Missing .prompt or .tool");
        properties.put("app.agents[0].workflow.routes", "Not a route property");
        properties.put("app.agents[0].workflow.other.property", "Not a route");
        
        // Mock the environment setup
        when(env.getPropertySources()).thenReturn(propertySources);
        Mockito.doReturn(java.util.List.of(propertySource).iterator()).when(propertySources).iterator();
        when(propertySource.getSource()).thenReturn(properties);
        
        // Mock agent configuration
        when(env.getProperty("app.agents[0].name")).thenReturn("validationAgent");
        when(env.getProperty("app.agents[0].model")).thenReturn("openai");
        when(env.getProperty("app.agents[0].systemPrompt")).thenReturn("You validate routes.");
        when(env.getProperty("app.agents[0].workflow.type")).thenReturn("routing");
        
        // Mock only valid route properties
        when(env.getProperty("app.agents[0].workflow.routes.validRoute.prompt")).thenReturn("Valid route: {input}");
        when(env.getProperty("app.agents[0].workflow.routes.validRoute.tool")).thenReturn("validTool");
        
        // Mock no more agents
        when(env.getProperty("app.agents[1].name")).thenReturn(null);
        
        CustomAppPropertiesConfiguration config = new CustomAppPropertiesConfiguration();
        AppProperties appProperties = config.customAppProperties(env);
        
        // Verify the agent was created with only valid routes
        assertNotNull(appProperties);
        assertEquals(1, appProperties.getAgents().size());
        
        AppProperties.AgentDef agent = appProperties.getAgents().get(0);
        assertEquals("validationAgent", agent.getName());
        assertEquals(WorkflowType.ROUTING, agent.getWorkflow().getType());
        
        // Verify only valid routes were discovered
        Map<String, AppProperties.RouteDef> routes = agent.getWorkflow().getRoutes();
        assertNotNull(routes, "Routes should not be null");
        assertEquals(1, routes.size(), "Should discover only 1 valid route");
        
        // Verify the valid route
        assertTrue(routes.containsKey("validRoute"), "Should contain validRoute");
        assertFalse(routes.containsKey("invalidRoute"), "Should not contain invalidRoute");
        assertFalse(routes.containsKey("anotherRoute"), "Should not contain anotherRoute");
        
        assertEquals("Valid route: {input}", routes.get("validRoute").getPrompt());
        assertEquals("validTool", routes.get("validRoute").getTool());
    }
}
