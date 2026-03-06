# Agent Package — `com.springai.agents.agent`

## Purpose
Defines the user-facing `Agent` interface and the framework's runtime wrappers.

## How Users Create Agents
1. Implement the `Agent` interface
2. Annotate with `@Component` (or any Spring stereotype)
3. Return a built `Workflow` from `buildWorkflow(WorkflowBuilder)`
4. The framework auto-discovers, wraps, and registers the agent

## Class Responsibilities

| Class | Role |
|-------|------|
| `Agent` | User-implemented interface: `getName()`, `getDescription()`, `buildWorkflow()` |
| `AgentRuntime` | Sync wrapper: execution state, persistent context, `invoke()` → `String` |
| `ReactiveAgentRuntime` | Reactive wrapper: `invoke()` → `Mono<String>` |
| `AgentRegistry` | Central lookup: name → runtime, holds both sync and reactive runtimes |

## Lifecycle
1. Spring discovers `Agent` beans in the application context
2. `AgentsAutoConfiguration` calls `agent.buildWorkflow(WorkflowBuilder.create())` for each
3. Depending on `spring.ai.agents.reactive`, each agent is wrapped in:
   - `AgentRuntime` (sync, default) or `ReactiveAgentRuntime` (reactive)
4. All runtimes are registered in `AgentRegistry`
5. `AgentToolCallbackProvider` exposes them as MCP tools (if `spring.ai.agents.mcp-server.enabled=true`)

## Configuration
```yaml
spring:
  ai:
    agents:
      reactive: false       # true for reactive mode
      mcp-server:
        enabled: true        # expose agents as MCP tools
```

