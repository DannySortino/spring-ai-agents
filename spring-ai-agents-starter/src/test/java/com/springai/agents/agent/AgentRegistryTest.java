package com.springai.agents.agent;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("AgentRegistry")
class AgentRegistryTest {

    @Test
    @DisplayName("ofSync creates sync-only registry")
    void ofSync() {
        Map<String, AgentRuntime> syncAgents = new LinkedHashMap<>();
        syncAgents.put("agent-1", null); // Just testing registry structure
        syncAgents.put("agent-2", null);

        AgentRegistry registry = AgentRegistry.ofSync(syncAgents);

        assertEquals(2, registry.size());
        assertEquals(Set.of("agent-1", "agent-2"), registry.getAgentNames());
        assertTrue(registry.hasAgent("agent-1"));
        assertFalse(registry.hasAgent("nonexistent"));
        assertFalse(registry.isReactive());
    }

    @Test
    @DisplayName("ofReactive creates reactive-only registry")
    void ofReactive() {
        Map<String, ReactiveAgentRuntime> reactiveAgents = new LinkedHashMap<>();
        reactiveAgents.put("reactive-1", null);

        AgentRegistry registry = AgentRegistry.ofReactive(reactiveAgents);

        assertEquals(1, registry.size());
        assertTrue(registry.hasAgent("reactive-1"));
        assertTrue(registry.isReactive());
    }

    @Test
    @DisplayName("getAgentNames returns union of sync and reactive")
    void agentNames() {
        Map<String, AgentRuntime> syncAgents = new LinkedHashMap<>();
        syncAgents.put("sync", null);
        Map<String, ReactiveAgentRuntime> reactiveAgents = new LinkedHashMap<>();
        reactiveAgents.put("reactive", null);

        AgentRegistry registry = new AgentRegistry(syncAgents, reactiveAgents);

        assertEquals(Set.of("sync", "reactive"), registry.getAgentNames());
        assertEquals(2, registry.size());
    }

    @Test
    @DisplayName("empty registry has size 0")
    void emptyRegistry() {
        AgentRegistry registry = AgentRegistry.ofSync(Map.of());
        assertEquals(0, registry.size());
        assertFalse(registry.hasAgent("anything"));
    }
}

