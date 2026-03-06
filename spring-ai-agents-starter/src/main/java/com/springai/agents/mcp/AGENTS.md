# MCP Package — `com.springai.agents.mcp`

## Purpose
Integration with Spring AI's Model Context Protocol (MCP) for both
server-side exposure and client-side tool resolution.

## MCP Server — Exposing Agents as Tools
`AgentToolCallbackProvider` implements Spring AI's `ToolCallbackProvider` and is
auto-discovered by the MCP Server Boot Starter. Each registered agent becomes
an invocable MCP tool.

**Activation:** `spring.ai.agents.mcp-server.enabled=true`

Spring AI auto-discovers `ToolCallbackProvider` beans and registers their tools.
No manual registration needed — the starter handles everything.

### Tool Schema
Each agent tool uses a simple JSON input schema:
```json
{
    "type": "object",
    "properties": {
        "input": { "type": "string", "description": "Input text for the agent" }
    },
    "required": ["input"]
}
```

## MCP Client — Calling External Tools
`McpClientToolResolver` aggregates tools from all `ToolCallbackProvider` beans in the
application context, including those from external MCP servers (configured via
`spring.ai.mcp.client`).

Used by `ToolExecutor` to resolve and call tools by name at runtime.

## Key Classes

| Class | Role |
|-------|------|
| `AgentToolCallbackProvider` | Exposes agents as MCP tools via `ToolCallbackProvider` |
| `McpClientToolResolver` | Resolves MCP tools by name for `ToolExecutor` |

