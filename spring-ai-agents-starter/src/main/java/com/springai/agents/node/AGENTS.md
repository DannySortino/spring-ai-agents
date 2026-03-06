# Node Package — `com.springai.agents.node`

## Purpose
Defines the immutable data model for all workflow node types. Nodes describe
**WHAT** to do, not **HOW** — behavior lives in the `executor` package.

## Design
- **Abstract class**: `Node` defines abstract `getId()`, and holds `hooks` and `config`
  fields inherited by all node types via `@SuperBuilder`.
- **Lombok `@Value @SuperBuilder`**: All node types are immutable with fluent builders
  that inherit `hooks()` and `config()` from the `Node` base class.
- **Validation**: `@NonNull` fields are enforced by Lombok at construction time.
- **Hooks vs Config**: `NodeHooks` handles lifecycle callbacks (before/after execute).
  `NodeConfig` handles execution behavior (error strategy, default value). Both are optional
  and available on every node type through the base class.
- **Edges are separate**: Nodes do NOT declare their dependencies. Edges are defined
  separately via `WorkflowBuilder.edge(from, to)`.

## Node Types

| Type | Purpose | Has External Call? | Key Fields |
|------|---------|-------------------|------------|
| `InputNode` | Entry point — receives raw user input | No | `id` |
| `OutputNode` | Exit point — returns final result | Optional (LLM post-processing) | `id`, `postProcessPrompt` |
| `LlmNode` | Sends prompt to LLM, returns response | Yes | `id`, `promptTemplate`, `systemPrompt` |
| `RestNode` | Executes HTTP REST API call | Yes | `id`, `url`, `method`, `bodyTemplate`, `headers`, `timeout` |
| `ContextNode` | Injects static text context | No | `id`, `contextText` |
| `ToolNode` | Calls MCP tool | Yes | `id`, `toolName`, `guidance` |

## Placeholder Interpolation
Templates use `{nodeId}` syntax to reference dependency outputs.
Use `{input}` for the raw user input.

## Builder Examples
```java
InputNode.builder().id("start").build();

LlmNode.builder()
    .id("analyze")
    .promptTemplate("Analyze: {start}")
    .systemPrompt("You are an expert analyst.")
    .build();

RestNode.builder()
    .id("fetch")
    .url("https://api.example.com/data?q={start}")
    .method(HttpMethod.GET)
    .header("Authorization", "Bearer token")
    .timeout(Duration.ofSeconds(10))
    .build();

OutputNode.builder()
    .id("result")
    .postProcessPrompt("Summarize: {analyze}")
    .build();
```

