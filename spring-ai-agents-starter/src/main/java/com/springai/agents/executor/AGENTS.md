# Executor Package — `com.springai.agents.executor`

## Purpose
Contains the runtime behavior for each node type. Executors are **stateless** — all
state is passed via `NodeContext`.

## Design Patterns
- **Strategy pattern**: `NodeExecutorRegistry` dispatches to the correct executor based on node type.
- **Dual interfaces**: `NodeExecutor<T>` (sync) and `ReactiveNodeExecutor<T>` (reactive).
  Executors with native reactive support (REST, LLM) implement both. The registry
  auto-wraps sync-only executors in `Mono.fromCallable()` for reactive mode.
- **`@ConditionalOnMissingBean`**: Users can replace any executor by providing their own `@Bean`.

## Key Classes

| Class | Handles | Sync | Reactive | External Dependency |
|-------|---------|------|----------|-------------------|
| `InputExecutor` | `InputNode` | ✅ | wrapped | None |
| `OutputExecutor` | `OutputNode` | ✅ | ✅ | `ChatModel` (optional) |
| `LlmExecutor` | `LlmNode` | ✅ | ✅ | `ChatModel` |
| `RestExecutor` | `RestNode` | ✅ | ✅ (native) | `WebClient` |
| `ContextExecutor` | `ContextNode` | ✅ | wrapped | None |
| `ToolExecutor` | `ToolNode` | ✅ | wrapped | `McpClientToolResolver`, `ChatModel` |
| `PromptInterpolator` | Utility | — | — | None |
| `NodeContext` | Value object | — | — | None |
| `NodeExecutorRegistry` | Dispatch | ✅ | ✅ | None |

## `NodeContext`
Immutable `@Value @Builder` that wraps all executor parameters:
- `resolvedInput` — combined dependency outputs (or raw user input for InputNode)
- `dependencyResults` — `Map<String, Object>` of nodeId → output (typed results, use `getDependencyResult(id, type)` for type-safe access)
- `executionContext` — `Map<String, Object>` shared metadata (agentName, timestamp, etc.)

## `PromptInterpolator`
Static utility replacing `{nodeId}` and `{input}` placeholders in templates. Used by
`LlmExecutor`, `OutputExecutor`, `RestExecutor`, and `ToolExecutor`.

