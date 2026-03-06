package com.springai.agents.agent;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

/**
 * Central registry holding all agent runtime instances.
 * <p>
 * Populated by auto-configuration — one entry per {@link Agent} bean discovered in
 * the application context. Supports both synchronous ({@link AgentRuntime}) and
 * reactive ({@link ReactiveAgentRuntime}) runtimes depending on configuration.
 */
@Slf4j
public class AgentRegistry {

    @Getter
    private final Map<String, AgentRuntime> syncAgents;

    @Getter
    private final Map<String, ReactiveAgentRuntime> reactiveAgents;

    /** Create a registry with synchronous agent runtimes. */
    public static AgentRegistry ofSync(Map<String, AgentRuntime> agents) {
        return new AgentRegistry(Collections.unmodifiableMap(new LinkedHashMap<>(agents)), Map.of());
    }

    /** Create a registry with reactive agent runtimes. */
    public static AgentRegistry ofReactive(Map<String, ReactiveAgentRuntime> agents) {
        return new AgentRegistry(Map.of(), Collections.unmodifiableMap(new LinkedHashMap<>(agents)));
    }

    /** Create a registry with both sync and reactive runtimes. */
    public AgentRegistry(Map<String, AgentRuntime> syncAgents, Map<String, ReactiveAgentRuntime> reactiveAgents) {
        this.syncAgents = syncAgents;
        this.reactiveAgents = reactiveAgents;
        log.info("AgentRegistry initialized: {} sync agents, {} reactive agents",
                syncAgents.size(), reactiveAgents.size());
    }

    /** Get a synchronous agent runtime by name. */
    public AgentRuntime getSyncAgent(String name) {
        return syncAgents.get(name);
    }

    /** Get a reactive agent runtime by name. */
    public ReactiveAgentRuntime getReactiveAgent(String name) {
        return reactiveAgents.get(name);
    }

    /** Get all agent names (union of sync and reactive). */
    public Set<String> getAgentNames() {
        Set<String> names = new LinkedHashSet<>();
        names.addAll(syncAgents.keySet());
        names.addAll(reactiveAgents.keySet());
        return Collections.unmodifiableSet(names);
    }

    /** Check if an agent with the given name exists (sync or reactive). */
    public boolean hasAgent(String name) {
        return syncAgents.containsKey(name) || reactiveAgents.containsKey(name);
    }

    /** Total number of registered agents. */
    public int size() {
        return getAgentNames().size();
    }

    /** Whether this registry operates in reactive mode. */
    public boolean isReactive() {
        return !reactiveAgents.isEmpty();
    }
}

