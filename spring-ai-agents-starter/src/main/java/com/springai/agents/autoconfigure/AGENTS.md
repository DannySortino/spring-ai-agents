# Auto-Configuration Package — `com.springai.agents.autoconfigure`

## Purpose
Spring Boot auto-configuration that wires the entire framework together.
Activated when `ChatModel` is on the classpath.

## What Gets Auto-Configured

| Bean | Type | Condition |
|------|------|-----------|
| `inputExecutor` | `InputExecutor` | `@ConditionalOnMissingBean` |
| `outputExecutor` | `OutputExecutor` | `@ConditionalOnMissingBean` |
| `llmExecutor` | `LlmExecutor` | `@ConditionalOnMissingBean` |
| `restExecutor` | `RestExecutor` | `@ConditionalOnMissingBean` |
| `contextExecutor` | `ContextExecutor` | `@ConditionalOnMissingBean` |
| `toolExecutor` | `ToolExecutor` | `@ConditionalOnMissingBean` |
| `nodeExecutorRegistry` | `NodeExecutorRegistry` | `@ConditionalOnMissingBean` |
| `workflowExecutor` | `WorkflowExecutor` | `reactive=false` (default) |
| `reactiveWorkflowExecutor` | `ReactiveWorkflowExecutor` | `reactive=true` |
| `agentRegistry` | `AgentRegistry` | `@ConditionalOnMissingBean` |
| `agentToolCallbackProvider` | `AgentToolCallbackProvider` | `mcp-server.enabled=true` |
| `mcpClientToolResolver` | `McpClientToolResolver` | `@ConditionalOnMissingBean` |
| `retryService` | `RetryService` | `@ConditionalOnMissingBean` |

## Configuration Properties
Prefix: `spring.ai.agents`

| Property | Default | Description |
|----------|---------|-------------|
| `reactive` | `false` | Use reactive execution mode |
| `mcp-server.enabled` | `false` | Expose agents as MCP tools |
| `parallel-threads` | `0` (cached) | Thread pool size for parallel nodes |

## Overriding Defaults
Users can replace any executor by defining their own `@Bean` of that type.
The `@ConditionalOnMissingBean` annotation ensures the framework's default
is skipped if the user provides their own.

## Registration
Auto-configuration is registered via:
`META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`

