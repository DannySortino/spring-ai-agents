package com.springai.agent.config;

import com.springai.agent.config.McpClientConfiguration.McpClientService;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class McpClientConfigurationTest {

    @TestConfiguration
    @Import(McpClientConfiguration.class)
    static class TestConfig {
        // Minimal test configuration - only import what we need to test
    }

    @Nested
    @SpringJUnitConfig(TestConfig.class)
    @TestPropertySource(properties = {
        "spring.ai.mcp.client.enabled=true"
    })
    class McpClientEnabledTest {

        @Autowired
        private ApplicationContext applicationContext;

        @Test
        void testMcpClientServiceCreatedWhenEnabled() {
            // When MCP client is enabled, McpClientService should be created
            assertTrue(applicationContext.containsBean("mcpClientService"));
            
            McpClientService mcpClientService = applicationContext.getBean(McpClientService.class);
            assertNotNull(mcpClientService);
        }

        @Test
        void testWebClientBuilderAlwaysCreated() {
            // WebClient builder should always be created regardless of MCP client toggle
            assertTrue(applicationContext.containsBean("mcpWebClientBuilder"));
        }
    }

    @Nested
    @SpringJUnitConfig(TestConfig.class)
    @TestPropertySource(properties = {
        "spring.ai.mcp.client.enabled=false"
    })
    class McpClientDisabledTest {

        @Autowired
        private ApplicationContext applicationContext;

        @Test
        void testMcpClientServiceNotCreatedWhenDisabled() {
            // When MCP client is disabled, McpClientService should not be created
            assertFalse(applicationContext.containsBean("mcpClientService"));
        }

        @Test
        void testWebClientBuilderStillCreatedWhenDisabled() {
            // WebClient builder should still be created even when MCP client is disabled
            assertTrue(applicationContext.containsBean("mcpWebClientBuilder"));
        }
    }

    @Nested
    @SpringJUnitConfig(TestConfig.class)
    class McpClientDefaultTest {

        @Autowired
        private ApplicationContext applicationContext;

        @Test
        void testMcpClientServiceNotCreatedByDefault() {
            // When no property is set, McpClientService should not be created (matchIfMissing = false)
            assertFalse(applicationContext.containsBean("mcpClientService"));
        }
    }

    // Unit tests for McpClientService functionality
    @Nested
    class McpClientServiceUnitTest {

        @Test
        void testConnectToServer() {
            McpClientService service = new McpClientService(null);
            
            boolean result = service.connectToServer("testServer", "http://localhost:8080");
            
            assertTrue(result);
            assertTrue(service.isServerConnected("testServer"));
            assertEquals("http://localhost:8080", service.getRegisteredServers().get("testServer"));
        }

        @Test
        void testDisconnectFromServer() {
            McpClientService service = new McpClientService(null);
            service.connectToServer("testServer", "http://localhost:8080");
            
            boolean result = service.disconnectFromServer("testServer");
            
            assertTrue(result);
            assertFalse(service.isServerConnected("testServer"));
            assertFalse(service.getRegisteredServers().containsKey("testServer"));
        }

        @Test
        void testDisconnectNonExistentServer() {
            McpClientService service = new McpClientService(null);
            
            boolean result = service.disconnectFromServer("nonExistentServer");
            
            assertFalse(result);
        }

        @Test
        void testCallExternalToolWithRegisteredServer() {
            McpClientService service = new McpClientService(null);
            service.connectToServer("testServer", "http://localhost:8080");
            
            String result = service.callExternalTool("testServer", "testTool", "test input", Map.of());
            
            assertNotNull(result);
            // When SyncMcpToolCallbackProvider is null, it returns error message
            assertTrue(result.contains("Error: MCP client not properly configured"));
        }

        @Test
        void testCallExternalToolWithUnregisteredServer() {
            McpClientService service = new McpClientService(null);
            
            String result = service.callExternalTool("unregisteredServer", "testTool", "test input", Map.of());
            
            assertNotNull(result);
            assertTrue(result.startsWith("Error: MCP server 'unregisteredServer' not registered"));
        }

        @Test
        void testGetRegisteredServersReturnsImmutableCopy() {
            McpClientService service = new McpClientService(null);
            service.connectToServer("server1", "http://localhost:8080");
            service.connectToServer("server2", "http://localhost:8081");
            
            Map<String, String> servers = service.getRegisteredServers();
            
            assertEquals(2, servers.size());
            
            // Verify it's an immutable copy by trying to modify it
            assertThrows(UnsupportedOperationException.class, () -> {
                servers.put("server3", "http://localhost:8082");
            });
        }

        @Test
        void testCallExternalToolHandlesInterruption() {
            McpClientService service = new McpClientService(null);
            service.connectToServer("testServer", "http://localhost:8080");
            
            // Interrupt the current thread before calling
            Thread.currentThread().interrupt();
            
            String result = service.callExternalTool("testServer", "testTool", "test input", Map.of());
            
            assertNotNull(result);
            assertTrue(result.contains("Error: MCP client not properly configured"));
            
            // Clear the interrupt flag
            Thread.interrupted();
        }
    }
}
