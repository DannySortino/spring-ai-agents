# AGENTS.md — Spring AI Agents Framework

## Architecture Overview

Programmatic AI agent framework built on **Spring Boot 3.2.5 / Java 21 / Spring AI 1.0.0**. Users implement the `Agent` interface to define workflows as DAGs of typed nodes. The framework auto-discovers agents, builds their workflows, and optionally exposes them as MCP server tools.

```
User implements Agent interface (@Component)
    └── buildWorkflows() → List<Workflow> (each a validated DAG with name/description)
                                            ↓
              AgentsAutoConfiguration auto-discovers Agent beans
                                            ↓
              WorkflowRouter selects best workflow per request (LLM or custom)
                                            ↓
              Wraps each in AgentRuntime (sync) or ReactiveAgentRuntime (reactive)
                                            ↓
              Registers in AgentRegistry
                                            ↓
              AgentToolCallbackProvider exposes as MCP tools (if enabled)
                                            ↓
              WorkflowExecutor dispatches nodes via NodeExecutorRegistry
                  ↓                                    ↓
              Parallel execution               Level-by-level ordering
              (CompletableFuture)             (topological sort)
                  ↓                                    ↓
              NodeHooks fire               ErrorStrategy handles failures
              (beforeExecute/afterExecute) (FAIL_FAST/CONTINUE/SKIP)
```

## Multi-Module Structure

| Module | Purpose |
|--------|---------|
| `spring-ai-agents-starter` | Core framework — nodes, executors, workflow engine, auto-config |
| `spring-ai-agents-visualization` | Web UI for visualizing workflow DAGs and execution status |
| `spring-ai-agents-sample` | Sample application demonstrating framework usage |

## Quick Start

### 1. Add Dependency
```xml
<dependency>
    <groupId>com.springai</groupId>
    <artifactId>spring-ai-agents-starter</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>
```

### 2. Implement an Agent

**Single-workflow agent:**
```java
@Component
public class MyAgent implements Agent {

    @Override
    public String getName() { return "my-agent"; }

    @Override
    public String getDescription() { return "A helpful assistant"; }

    @Override
    public Workflow buildWorkflow(WorkflowBuilder builder) {
        var input = InputNode.builder().id("input").build();
        var process = LlmNode.builder()
                .id("process")
                .promptTemplate("Help the user with: {input}")
                .systemPrompt("You are a helpful assistant.")
                .build();
        var output = OutputNode.builder().id("output").build();

        return builder
            .name("assist")
            .description("Answers user questions")
            .nodes(input, process, output)
            .edge(input, process)
            .edge(process, output)
            .build();
    }
}
```

**Multi-workflow agent:**
```java
@Component
public class SmartAgent implements Agent {

    @Override
    public String getName() { return "smart-agent"; }
    @Override
    public String getDescription() { return "Routes to best workflow"; }

    @Override
    public List<Workflow> buildWorkflows() {
        return List.of(
            buildAnalyzeWorkflow(),
            buildSummarizeWorkflow()
        );
    }

    private Workflow buildAnalyzeWorkflow() {
        var input = InputNode.builder().id("input").build();
        var analyze = LlmNode.builder().id("analyze").promptTemplate("...").build();
        var output = OutputNode.builder().id("output").build();

        return WorkflowBuilder.create()
            .name("analyze").description("Analyzes data")
            .nodes(input, analyze, output)
            .edge(input, analyze)
            .edge(analyze, output)
            .build();
    }

    private Workflow buildSummarizeWorkflow() {
        var input = InputNode.builder().id("input").build();
        var summarize = LlmNode.builder().id("summarize").promptTemplate("...").build();
        var output = OutputNode.builder().id("output").build();

        return WorkflowBuilder.create()
            .name("summarize").description("Summarizes text")
            .nodes(input, summarize, output)
            .edge(input, summarize)
            .edge(summarize, output)
            .build();
    }
}
```

### 3. Configure
```yaml
spring:
  ai:
    openai:
      api-key: ${OPENAI_API_KEY}
    agents:
      reactive: false           # true for reactive mode
      mcp-server:
        enabled: true           # expose agents as MCP tools
```

### 4. Build & Run
```bash
mvn clean install
mvn spring-boot:run -pl spring-ai-agents-sample
```

## Node Types

| Type | Purpose | Key Fields |
|------|---------|------------|
| `InputNode` | Entry point — receives raw user input | `id` |
| `OutputNode` | Exit point — returns final result | `id`, `postProcessPrompt`, `outputHandler` |
| `LlmNode` | Sends prompt to LLM | `id`, `promptTemplate`, `systemPrompt` |
| `RestNode` | Executes HTTP REST call | `id`, `url`, `method`, `bodyTemplate`, `bodyProvider`, `headers`, `timeout` |
| `ContextNode` | Injects static text | `id`, `contextText` |
| `ToolNode` | Calls MCP tool | `id`, `toolName`, `guidance` |

All nodes extend the `Node` base class (which provides `hooks` and `config`) and are built with Lombok `@SuperBuilder`:
```java
LlmNode.builder().id("analyze").promptTemplate("Analyze: {input}").build()
```

## Node Hooks (beforeExecute / afterExecute)

All nodes support optional lifecycle hooks:
```java
NodeHooks hooks = NodeHooks.builder()
    .beforeExecute(ctx -> log.info("Starting node with: {}", ctx.getResolvedInput()))
    .afterExecute((ctx, result) -> log.info("Node produced: {}", result))
    .build();

LlmNode.builder().id("analyze").promptTemplate("...").hooks(hooks).build();
```

## Node Configuration (Error Handling)

All nodes support optional execution configuration via `NodeConfig`:
```java
NodeConfig config = NodeConfig.builder()
    .errorStrategy(ErrorStrategy.CONTINUE_WITH_DEFAULT)
    .defaultValue("fallback")
    .build();

LlmNode.builder().id("analyze").promptTemplate("...").config(config).build();
```

## Output Strategies

OutputNode supports three strategies (priority order):
1. **Custom handler** — full control via `Function<NodeContext, String>`
2. **LLM post-processing** — via `postProcessPrompt`
3. **Pass-through** — returns combined dependency outputs

```java
OutputNode.builder()
    .id("output")
    .outputHandler(ctx -> {
        String data = ctx.getDependencyResult("analyze", String.class);
        return "## Report\n\n" + data.toUpperCase();
    })
    .build();
```

## Typed Node Results

Node results are stored as `Object` (not just `String`). Access typed results:
```java
// In an outputHandler or custom executor:
Integer count = ctx.getDependencyResult("counter", Integer.class);
MyPojo data = ctx.getDependencyResult("fetch", MyPojo.class);

// In WorkflowResult:
WorkflowResult result = runtime.invokeWithResult("input");
Integer calc = result.getNodeResult("calc", Integer.class);
```

## REST Node Body Provider

Send typed POJOs as REST request bodies:
```java
RestNode.builder()
    .id("submit")
    .url("https://api.example.com/submit")
    .method(HttpMethod.POST)
    .bodyProvider(deps -> {
        MyData data = (MyData) deps.get("extract");
        return new SubmitRequest(data.getId(), data.getName());
    })
    .build();
```

## Error Handling

Per-node error strategies via `NodeConfig`:

| Strategy | Behavior |
|----------|----------|
| `FAIL_FAST` (default) | Propagate exception immediately |
| `CONTINUE_WITH_DEFAULT` | Store default value and continue |
| `SKIP` | Skip node entirely, downstream sees absent result |

## Multi-Workflow Routing

Agents with multiple workflows use a `WorkflowRouter` to select the best one:

| Router | Strategy |
|--------|----------|
| `LlmWorkflowRouter` | LLM picks best workflow from names/descriptions (default) |
| `DefaultWorkflowRouter` | Always picks first workflow (fallback) |
| Custom `WorkflowRouter` bean | User-defined routing logic |

## Workflow Lifecycle Events

Subscribe to workflow events with `@EventListener`:
```java
@EventListener
public void onNodeCompleted(NodeCompletedEvent event) {
    log.info("Node '{}' completed in {}ms", event.getNodeId(), event.getDurationMs());
}
```

Events: `WorkflowStartedEvent`, `NodeStartedEvent`, `NodeCompletedEvent`, `WorkflowCompletedEvent`

## Workflow Patterns

**Sequential:** `input → A → B → output`
```java
var input = InputNode.builder().id("input").build();
var a = LlmNode.builder().id("A").promptTemplate("...").build();
var b = LlmNode.builder().id("B").promptTemplate("...").build();
var output = OutputNode.builder().id("output").build();

builder.nodes(input, a, b, output)
       .edge(input, a).edge(a, b).edge(b, output)
```

**Parallel fan-out/fan-in:** nodes `A` and `B` run concurrently
```java
builder.nodes(input, a, b, output)
       .edge(input, a).edge(input, b)
       .edge(a, output).edge(b, output)
```

**Diamond:** `input → A → C`, `input → B → C`, `C → output`
```java
builder.nodes(input, a, b, c, output)
       .edge(input, a).edge(input, b)
       .edge(a, c).edge(b, c)
       .edge(c, output)
```

## Custom Node Types

Create custom node types by:
1. Extending the `Node` abstract class
2. Creating a matching `NodeExecutor<T>` as a Spring `@Bean`
3. The framework auto-registers custom executors

```java
@Value @SuperBuilder @EqualsAndHashCode(callSuper = false)
public class MyCustomNode extends Node {
    @NonNull String id;
    String customField;
}

@Component
public class MyCustomExecutor implements NodeExecutor<MyCustomNode> {
    public Object execute(MyCustomNode node, NodeContext ctx) { ... }
    public Class<MyCustomNode> getNodeType() { return MyCustomNode.class; }
}
```

## Reactive vs Non-Reactive

| Setting | Executor | Runtime | Returns |
|---------|----------|---------|---------|
| `reactive: false` (default) | `WorkflowExecutor` | `AgentRuntime` | `String` |
| `reactive: true` | `ReactiveWorkflowExecutor` | `ReactiveAgentRuntime` | `Mono<String>` |

## Configuration Reference

| Property | Default | Description |
|----------|---------|-------------|
| `spring.ai.agents.reactive` | `false` | Reactive execution mode |
| `spring.ai.agents.mcp-server.enabled` | `false` | Expose agents as MCP tools |
| `spring.ai.agents.parallel-threads` | `0` | Thread pool size (0 = cached) |

## MCP Integration

- **MCP Server**: Set `spring.ai.agents.mcp-server.enabled=true`. Each agent is auto-exposed as a callable MCP tool via `AgentToolCallbackProvider`.
- **MCP Client**: Configure `spring.ai.mcp.client` to connect to external MCP servers. Use `ToolNode` in workflows to call external tools.

## Key Files

| File | Purpose |
|------|---------|
| `node/Node.java` | Abstract base class for all node types (holds hooks, config) |
| `node/NodeHooks.java` | Before/after lifecycle hooks |
| `node/NodeConfig.java` | Per-node execution config (error strategy, default value) |
| `node/ErrorStrategy.java` | FAIL_FAST, CONTINUE_WITH_DEFAULT, SKIP |
| `executor/NodeExecutor.java` | Sync executor interface (returns Object) |
| `executor/ReactiveNodeExecutor.java` | Reactive executor interface |
| `executor/NodeExecutorRegistry.java` | Maps node types to executors |
| `executor/NodeContext.java` | Typed dependency results + execution context |
| `executor/PromptInterpolator.java` | `{nodeId}` / `{input}` placeholder interpolation |
| `workflow/Edge.java` | Directed edge record (from → to) with fluent builder |
| `workflow/WorkflowBuilder.java` | Fluent builder for workflow DAGs |
| `workflow/Workflow.java` | Immutable validated DAG with name/description |
| `workflow/WorkflowResult.java` | Execution result with typed node results + timing |
| `workflow/WorkflowExecutor.java` | Sync DAG execution engine with events |
| `workflow/ReactiveWorkflowExecutor.java` | Reactive DAG execution engine |
| `workflow/WorkflowRouter.java` | Interface for workflow selection |
| `workflow/LlmWorkflowRouter.java` | LLM-based workflow routing |
| `workflow/DefaultWorkflowRouter.java` | Fallback router (first workflow) |
| `workflow/event/*.java` | Workflow lifecycle events |
| `agent/Agent.java` | User-facing agent interface (single + multi-workflow) |
| `agent/AgentRuntime.java` | Sync agent wrapper with multi-workflow support |
| `agent/ReactiveAgentRuntime.java` | Reactive agent wrapper |
| `agent/AgentRegistry.java` | Central agent lookup |
| `mcp/AgentToolCallbackProvider.java` | Exposes agents as MCP tools |
| `mcp/McpClientToolResolver.java` | Resolves MCP tools by name for ToolExecutor |
| `retry/RetryService.java` | Configurable retry execution with backoff |
| `retry/RetryConfig.java` | Retry configuration (strategy, attempts, delays) |
| `retry/RetryStrategy.java` | Backoff strategy enum |
| `autoconfigure/AgentsAutoConfiguration.java` | Spring Boot auto-configuration |
| `autoconfigure/AgentsProperties.java` | Configuration properties binding |
