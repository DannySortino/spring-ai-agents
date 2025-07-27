package com.springai.agent.config;

import com.springai.agent.service.GraphVisualizationService;
import com.springai.agent.service.ExecutionStatusService;
import com.springai.agent.controller.VisualizationController;
import com.springai.agent.controller.GraphCreatorController;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.ai.chat.model.ChatModel;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for VisualizationConfiguration.
 * Tests conditional bean registration based on feature flags.
 */
@SpringJUnitConfig({VisualizationConfiguration.class, VisualizationConfigurationTest.TestConfig.class})
class VisualizationConfigurationTest {

    @TestConfiguration
    static class TestConfig {
        @Bean
        @Primary
        public ChatModel mockChatModel() {
            return mock(ChatModel.class);
        }
        
        @Bean
        @Primary
        public AppProperties mockAppProperties() {
            AppProperties appProperties = new AppProperties();
            // Set up basic configuration to avoid null pointer exceptions
            appProperties.setAgents(java.util.Collections.emptyList());
            return appProperties;
        }
        
        @Bean
        @Primary
        public AgentsProperties mockAgentsProperties() {
            AgentsProperties agentsProperties = new AgentsProperties();
            // Set up basic configuration to avoid null pointer exceptions
            agentsProperties.setList(java.util.Collections.emptyList());
            return agentsProperties;
        }
    }

    @Autowired
    private ApplicationContext context;

    @Test
    @DisplayName("Should not create any visualization beans with default configuration")
    void testDefaultConfiguration() {
        assertFalse(context.containsBean("graphVisualizationService"));
        assertFalse(context.containsBean("executionStatusService"));
        assertFalse(context.containsBean("visualizationController"));
        assertFalse(context.containsBean("graphCreatorController"));
    }

    @Test
    @DisplayName("Should still have configuration bean")
    void testConfigurationBeanExists() {
        assertTrue(context.containsBean("visualizationConfiguration"));
    }
}

/**
 * Test with graph structure enabled
 */
@SpringJUnitConfig({VisualizationConfiguration.class, VisualizationConfigurationTest.TestConfig.class})
@TestPropertySource(properties = {
    "visualization.graphStructure=true"
})
class GraphStructureEnabledTest {
    
    @Autowired
    private ApplicationContext context;
    
    @Test
    @DisplayName("Should create GraphVisualizationService when graph-structure is enabled")
    void testGraphVisualizationServiceCreated() {
        assertTrue(context.containsBean("graphVisualizationService"));
        assertNotNull(context.getBean(GraphVisualizationService.class));
    }
    
    @Test
    @DisplayName("Should not create ExecutionStatusService when real-time-status is disabled")
    void testExecutionStatusServiceNotCreated() {
        assertFalse(context.containsBean("executionStatusService"));
    }
    
    @Test
    @DisplayName("Should not create GraphCreatorController when interactive-creator is disabled")
    void testGraphCreatorControllerNotCreated() {
        assertFalse(context.containsBean("graphCreatorController"));
    }
}

/**
 * Test with real-time status enabled
 */
@SpringJUnitConfig({VisualizationConfiguration.class, VisualizationConfigurationTest.TestConfig.class})
@TestPropertySource(properties = {
    "visualization.realTimeStatus=true"
})
class RealTimeStatusEnabledTest {
    
    @Autowired
    private ApplicationContext context;
    
    @Test
    @DisplayName("Should create ExecutionStatusService when real-time-status is enabled")
    void testExecutionStatusServiceCreated() {
        assertTrue(context.containsBean("executionStatusService"));
        assertNotNull(context.getBean(ExecutionStatusService.class));
    }
    
    @Test
    @DisplayName("Should not create GraphVisualizationService when graph-structure is disabled")
    void testGraphVisualizationServiceNotCreated() {
        assertFalse(context.containsBean("graphVisualizationService"));
    }
}

/**
 * Test with interactive creator enabled
 */
@SpringJUnitConfig({VisualizationConfiguration.class, VisualizationConfigurationTest.TestConfig.class})
@TestPropertySource(properties = {
    "visualization.interactiveCreator=true"
})
class InteractiveCreatorEnabledTest {
    
    @Autowired
    private ApplicationContext context;
    
    @Test
    @DisplayName("Should create GraphCreatorController when interactive-creator is enabled")
    void testGraphCreatorControllerCreated() {
        assertTrue(context.containsBean("graphCreatorController"));
        assertNotNull(context.getBean(GraphCreatorController.class));
    }
    
    @Test
    @DisplayName("Should not create other services when only interactive-creator is enabled")
    void testOtherServicesNotCreated() {
        assertFalse(context.containsBean("graphVisualizationService"));
        assertFalse(context.containsBean("executionStatusService"));
    }
}

/**
 * Test with both graph structure and real-time status enabled
 */
@SpringJUnitConfig({VisualizationConfiguration.class, VisualizationConfigurationTest.TestConfig.class})
@TestPropertySource(properties = {
    "visualization.graphStructure=true",
    "visualization.realTimeStatus=true"
})
class BothGraphAndStatusEnabledTest {
    
    @Autowired
    private ApplicationContext context;
    
    @Test
    @DisplayName("Should create both services when both features are enabled")
    void testBothServicesCreated() {
        assertTrue(context.containsBean("graphVisualizationService"));
        assertTrue(context.containsBean("executionStatusService"));
        assertNotNull(context.getBean(GraphVisualizationService.class));
        assertNotNull(context.getBean(ExecutionStatusService.class));
    }
    
    @Test
    @DisplayName("Should create VisualizationController when visualization features are enabled")
    void testVisualizationControllerCreated() {
        assertTrue(context.containsBean("visualizationController"));
        assertNotNull(context.getBean(VisualizationController.class));
    }
}

/**
 * Test with all features enabled
 */
@SpringJUnitConfig({VisualizationConfiguration.class, VisualizationConfigurationTest.TestConfig.class})
@TestPropertySource(properties = {
    "visualization.graphStructure=true",
    "visualization.realTimeStatus=true",
    "visualization.interactiveCreator=true"
})
class AllFeaturesEnabledTest {
    
    @Autowired
    private ApplicationContext context;
    
    @Test
    @DisplayName("Should create all beans when all features are enabled")
    void testAllBeansCreated() {
        assertTrue(context.containsBean("graphVisualizationService"));
        assertTrue(context.containsBean("executionStatusService"));
        assertTrue(context.containsBean("visualizationController"));
        assertTrue(context.containsBean("graphCreatorController"));
        
        assertNotNull(context.getBean(GraphVisualizationService.class));
        assertNotNull(context.getBean(ExecutionStatusService.class));
        assertNotNull(context.getBean(VisualizationController.class));
        assertNotNull(context.getBean(GraphCreatorController.class));
    }
    
    @Test
    @DisplayName("Should wire dependencies correctly")
    void testDependencyWiring() {
        VisualizationController controller = context.getBean(VisualizationController.class);
        assertNotNull(controller);
        
        GraphCreatorController creatorController = context.getBean(GraphCreatorController.class);
        assertNotNull(creatorController);
    }
}

/**
 * Test with all features explicitly disabled
 */
@SpringJUnitConfig({VisualizationConfiguration.class, VisualizationConfigurationTest.TestConfig.class})
@TestPropertySource(properties = {
    "visualization.graphStructure=false",
    "visualization.realTimeStatus=false",
    "visualization.interactiveCreator=false"
})
class AllFeaturesDisabledTest {
    
    @Autowired
    private ApplicationContext context;
    
    @Test
    @DisplayName("Should not create any visualization beans when all features are disabled")
    void testNoBeanCreated() {
        assertFalse(context.containsBean("graphVisualizationService"));
        assertFalse(context.containsBean("executionStatusService"));
        assertFalse(context.containsBean("visualizationController"));
        assertFalse(context.containsBean("graphCreatorController"));
    }
    
    @Test
    @DisplayName("Should still have configuration bean")
    void testConfigurationBeanExists() {
        assertTrue(context.containsBean("visualizationConfiguration"));
    }
}

/**
 * Test with case insensitive property values
 */
@SpringJUnitConfig({VisualizationConfiguration.class, VisualizationConfigurationTest.TestConfig.class})
@TestPropertySource(properties = {
    "visualization.graphStructure=TRUE", // Case insensitive
    "visualization.realTimeStatus=True"
})
class CaseInsensitivePropertiesTest {
    
    @Autowired
    private ApplicationContext context;
    
    @Test
    @DisplayName("Should handle case insensitive property values")
    void testCaseInsensitiveProperties() {
        assertTrue(context.containsBean("graphVisualizationService"));
        assertTrue(context.containsBean("executionStatusService"));
        assertTrue(context.containsBean("visualizationController"));
    }
}

/**
 * Test with partial visualization controller creation
 */
@SpringJUnitConfig({VisualizationConfiguration.class, VisualizationConfigurationTest.TestConfig.class})
@TestPropertySource(properties = {
    "visualization.graphStructure=true",
    "visualization.realTimeStatus=false" // Only one enabled
})
class PartialVisualizationControllerTest {
    
    @Autowired
    private ApplicationContext context;
    
    @Test
    @DisplayName("Should create VisualizationController when at least one visualization feature is enabled")
    void testPartialVisualizationController() {
        assertTrue(context.containsBean("graphVisualizationService"));
        assertFalse(context.containsBean("executionStatusService"));
        
        // VisualizationController should still be created because graph-structure is enabled
        assertTrue(context.containsBean("visualizationController"));
    }
}

