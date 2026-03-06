# Spring AI Agents Framework

A programmatic framework for building AI agent workflows as validated DAGs (Directed Acyclic Graphs), built on **Spring Boot 3.2.5**, **Java 21**, and **Spring AI 1.0.0**.

Users implement the `Agent` interface to define workflows of typed nodes (LLM, REST, Tool, Context). The framework auto-discovers agents, builds their DAGs, executes them with parallel fan-out, and optionally exposes them as MCP server tools.

```
┌──────────────────────────────────────────────────────────────────┐
│  You implement Agent (@Component)                                │
│      └── buildWorkflow(builder) → Validated DAG                  │
│                                                                  │
│  Framework auto-discovers agents                                 │
│      ├── Wraps in AgentRuntime (sync) or ReactiveAgentRuntime    │
│      ├── Registers in AgentRegistry                              │
│      ├── Exposes as MCP tools (optional)                         │
│      └── Visualization UI at /agents-ui (optional)               │
│                                                                  │
│  WorkflowExecutor runs nodes level-by-level                      │
│      ├── Parallel execution via CompletableFuture                │
│      ├── NodeHooks fire beforeExecute / afterExecute             │
│      └── ErrorStrategy handles failures per-node                 │
└──────────────────────────────────────────────────────────────────┘
```

## Multi-Module Structure

| Module | Artifact | Purpose |
|--------|----------|---------|
| `spring-ai-agents-starter` | `spring-ai-agents-starter` | Core framework — nodes, executors, workflow engine, auto-config |
| `spring-ai-agents-visualization` | `spring-ai-agents-visualization` | Web UI for visualizing workflow DAGs and real-time execution monitoring |
| `spring-ai-agents-sample` | `spring-ai-agents-sample` | Working sample application with example agents |

---

## Quick Start

### Prerequisites

- **Java 21** or higher
- **Maven 3.8+**
- An LLM provider (OpenAI, or a local server like LM Studio at `http://localhost:1234`)

### 1. Build the Project

```bash
git clone <repository-url>
cd spring-ai-agents
mvn clean install
```

### 2. Add Dependencies

For a new Spring Boot application, add the starter to your `pom.xml`:

```xml
<dependency>
    <groupId>com.springai</groupId>
    <artifactId>spring-ai-agents-starter</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>
```

To also get the visualization dashboard, add:

```xml
<dependency>
    <groupId>com.springai</groupId>
    <artifactId>spring-ai-agents-visualization</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>
```

### 3. Implement an Agent

Create a `@Component` that implements `Agent`:

```java
@Component
public class GreetingAgent implements Agent {

    @Override
    public String getName() { return "greeting-agent"; }

    @Override
    public String getDescription() { return "Greets the user warmly"; }

    @Override
    public Workflow buildWorkflow(WorkflowBuilder builder) {
        var input = InputNode.builder().id("input").build();

        var greet = LlmNode.builder()
                .id("greet")
                .promptTemplate("Generate a warm, personalized greeting for: {input}")
                .systemPrompt("You are a friendly greeter.")
                .build();

        var output = OutputNode.builder().id("output").build();

        return builder
                .name("greet")
                .description("Generates a personalized greeting")
                .nodes(input, greet, output)
                .edge(input, greet)
                .edge(greet, output)
                .build();
    }
}
```

### 3b. Or Describe Agents in Plain English (Zero Code!)

The ultimate no-code experience — just write what you want in a markdown file:

**`src/main/resources/agent-specs/customer-support.md`**:

```markdown
# Customer Support Agent

This agent handles customer inquiries for our e-commerce platform.

## What it should do

1. First, analyze the customer's message to understand:
   - Their sentiment (happy, frustrated, neutral)
   - The type of inquiry (order status, refund, product question)

2. Based on the analysis:
   - For order status: Look up the order and provide status
   - For refunds: Check eligibility and process or escalate
   - For product questions: Provide helpful information

3. Always:
   - Be polite and professional
   - Apologize if the customer is frustrated
   - Offer to escalate to a human if needed

## Tone
Friendly but professional. Use the customer's name if available.
```

Enable in `application.yml`:
```yaml
spring:
  ai:
    agents:
      natural:
        enabled: true
```

The framework uses an LLM to automatically generate the agent workflow from your requirements!

### 3c. Or Define Agents with YAML (Low Code)

For simple to moderately complex agents, you can define them entirely in YAML — no Java code required:

**`src/main/resources/agents/customer-support.yaml`**:

```yaml
name: customer-support
description: Handles customer inquiries with sentiment analysis

workflows:
  - name: support-flow
    description: Analyze sentiment and generate response
    
    nodes:
      - id: input
        type: input
        
      - id: analyze-sentiment
        type: llm
        prompt: |
          Analyze the sentiment of: {input}
          Respond with: positive, negative, or neutral
        systemPrompt: You are a sentiment analyzer.
        
      - id: generate-response
        type: llm
        prompt: |
          Sentiment: {analyze-sentiment}
          Customer message: {input}
          Generate a helpful response.
        systemPrompt: You are a friendly support agent.
        
      - id: output
        type: output
        
    edges:
      - from: input
        to: analyze-sentiment
      - from: input
        to: generate-response
      - from: analyze-sentiment
        to: generate-response
      - from: generate-response
        to: output
```

YAML agents are automatically discovered from `classpath:agents/*.yaml` at startup.

**Supported node types:**
- `input` — Entry point for user input
- `output` — Final output node
- `llm` — LLM call with `prompt` and optional `systemPrompt`
- `rest` — HTTP call with `method`, `url`, `body`, `headers`
- `tool` — Tool/function call with `toolName` and `toolArgs`
- `context` — Set context variables with `contextKey` and `contextValue`

**Error handling:**
```yaml
- id: risky-node
  type: llm
  prompt: "..."
  errorStrategy: CONTINUE_WITH_DEFAULT  # or FAIL_FAST, SKIP
  defaultValue: "Fallback response"
```

### 4. Configure

`application.yml`:

```yaml
spring:
  ai:
    openai:
      api-key: ${OPENAI_API_KEY}
      # For local models (LM Studio, Ollama, etc.):
      # base-url: http://localhost:1234/v1
      # chat:
      #   options:
      #     model: local-model
    agents:
      reactive: false           # true for reactive (Mono/Flux) mode
      mcp-server:
        enabled: false          # true to expose agents as MCP tools

server:
  port: 8086

logging:
  level:
    com.springai.agents: DEBUG
```

### 5. Run

```bash
mvn spring-boot:run -pl spring-ai-agents-sample
```

That's it — the framework auto-discovers your `@Component` agent, builds and validates the DAG, and registers it. If the visualization module is on the classpath, browse to `http://localhost:8086/agents-ui/` to see the dashboard.

---

## Node Types

All nodes extend the `Node` base class (which provides `hooks` and `config`) and are built with Lombok `@Value @SuperBuilder`.

| Type | Purpose | Key Fields |
|------|---------|------------|
| `InputNode` | Entry point — receives raw user input | `id` |
| `OutputNode` | Exit point — returns final result | `id`, `postProcessPrompt`, `outputHandler` |
| `LlmNode` | Sends interpolated prompt to LLM | `id`, `promptTemplate`, `systemPrompt` |
| `RestNode` | Executes HTTP REST call | `id`, `url`, `method`, `bodyTemplate`, `bodyProvider`, `headers`, `timeout` |
| `ContextNode` | Injects static text into the workflow | `id`, `contextText` |
| `ToolNode` | Calls an MCP tool by name | `id`, `toolName`, `guidance` |

### Placeholder Interpolation

Templates use `{nodeId}` to reference the output of an upstream dependency node, and `{input}` for the raw user input:

```java
LlmNode.builder()
    .id("summarize")
    .promptTemplate("Summarize the following analysis:\n\n{analyze}")
    .build();
```

### Builder Examples

```java
// Input — every workflow needs at least one
InputNode.builder().id("input").build();

// LLM call with system prompt
LlmNode.builder()
    .id("analyze")
    .promptTemplate("Analyze: {input}")
    .systemPrompt("You are a data analyst.")
    .build();

// HTTP REST call with headers and timeout
RestNode.builder()
    .id("fetch")
    .url("https://api.example.com/data?q={input}")
    .method(HttpMethod.GET)
    .header("Authorization", "Bearer my-token")
    .timeout(Duration.ofSeconds(10))
    .build();

// Static context injection
ContextNode.builder()
    .id("rules")
    .contextText("Always be concise. Cite sources.")
    .build();

// MCP tool call
ToolNode.builder()
    .id("search")
    .toolName("web_search")
    .guidance("Search for information about the user's query")
    .build();

// Output — pass-through
OutputNode.builder().id("output").build();

// Output — with LLM post-processing
OutputNode.builder()
    .id("output")
    .postProcessPrompt("Format this as a markdown report: {analyze}")
    .build();

// Output — with custom handler
OutputNode.builder()
    .id("output")
    .outputHandler(ctx -> {
        String data = ctx.getDependencyResult("analyze", String.class);
        return "## Report\n\n" + data.toUpperCase();
    })
    .build();
```

---

## Workflow Patterns

Nodes and edges are defined separately in the builder. Use node-reference edges (type-safe) to prevent typos:

### Sequential

```
input → A → B → output
```

```java
var input = InputNode.builder().id("input").build();
var a = LlmNode.builder().id("A").promptTemplate("Step 1: {input}").build();
var b = LlmNode.builder().id("B").promptTemplate("Step 2: {A}").build();
var output = OutputNode.builder().id("output").build();

builder.nodes(input, a, b, output)
       .edge(input, a).edge(a, b).edge(b, output)
       .build();
```

### Parallel Fan-Out / Fan-In

Nodes `A` and `B` run concurrently, both feed into `output`:

```
         ┌── A ──┐
input ───┤       ├─── output
         └── B ──┘
```

```java
builder.nodes(input, a, b, output)
       .edge(input, a).edge(input, b)
       .edge(a, output).edge(b, output)
       .build();
```

### Diamond

```
         ┌── A ──┐
input ───┤       ├── C ── output
         └── B ──┘
```

```java
builder.nodes(input, a, b, c, output)
       .edge(input, a).edge(input, b)
       .edge(a, c).edge(b, c)
       .edge(c, output)
       .build();
```

### Multi-Workflow Agent

Override `buildWorkflows()` to return multiple workflows. The framework uses a `WorkflowRouter` (LLM-based by default) to select the best workflow for each incoming request:

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
            buildExtractionWorkflow(),
            buildAnalysisWorkflow()
        );
    }

    private Workflow buildExtractionWorkflow() {
        var input = InputNode.builder().id("input").build();
        var extract = LlmNode.builder().id("extract")
                .promptTemplate("Extract key data points from: {input}").build();
        var output = OutputNode.builder().id("output").build();

        return WorkflowBuilder.create()
                .name("extract")
                .description("Extracts key data points and facts from text")
                .nodes(input, extract, output)
                .edge(input, extract).edge(extract, output)
                .build();
    }

    private Workflow buildAnalysisWorkflow() {
        // ... similar pattern
    }
}
```

---

## Node Hooks & Error Handling

### Lifecycle Hooks

All nodes support optional lifecycle hooks via `NodeHooks`:

```java
NodeHooks hooks = NodeHooks.builder()
    .beforeExecute(ctx -> log.info("Starting with: {}", ctx.getResolvedInput()))
    .afterExecute((ctx, result) -> log.info("Produced: {}", result))
    .build();

LlmNode.builder().id("analyze").promptTemplate("...").hooks(hooks).build();
```

### Per-Node Error Handling

All nodes support optional error handling via `NodeConfig`:

```java
NodeConfig config = NodeConfig.builder()
    .errorStrategy(ErrorStrategy.CONTINUE_WITH_DEFAULT)
    .defaultValue("fallback value")
    .build();

LlmNode.builder()
    .id("analyze")
    .promptTemplate("...")
    .hooks(hooks)      // lifecycle hooks (optional)
    .config(config)    // error handling (optional)
    .build();
```

| Strategy | Behavior |
|----------|----------|
| `FAIL_FAST` (default) | Exception propagates immediately |
| `CONTINUE_WITH_DEFAULT` | Stores the `defaultValue` and continues |
| `SKIP` | Skips the node entirely; downstream sees absent result |

---

## Typed Node Results

Node results are stored as `Object`, not just `String`. Access typed results:

```java
// In an outputHandler:
Integer count = ctx.getDependencyResult("counter", Integer.class);
MyPojo data = ctx.getDependencyResult("fetch", MyPojo.class);

// From WorkflowResult:
WorkflowResult result = runtime.invokeWithResult("input text");
Integer calc = result.getNodeResult("calc", Integer.class);
```

---

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

---

## Workflow Events

Subscribe to lifecycle events with `@EventListener`:

```java
@EventListener
public void onNodeCompleted(NodeCompletedEvent event) {
    log.info("Node '{}' completed in {}ms in workflow '{}'",
            event.getNodeId(), event.getDurationMs(), event.getWorkflowName());
}
```

| Event | Published When |
|-------|---------------|
| `WorkflowStartedEvent` | Workflow execution begins |
| `NodeStartedEvent` | Individual node starts |
| `NodeCompletedEvent` | Individual node finishes |
| `WorkflowCompletedEvent` | Entire workflow finishes |

---

## Custom Node Types

Create custom nodes by extending `Node` + a matching `NodeExecutor<T>`:

```java
// 1. Define the node (immutable data)
@Value
@SuperBuilder
@EqualsAndHashCode(callSuper = false)
public class MyCustomNode extends Node {
    @NonNull String id;
    String customField;
}

// 2. Create the executor (Spring bean — auto-registered)
@Component
public class MyCustomExecutor implements NodeExecutor<MyCustomNode> {
    @Override
    public Object execute(MyCustomNode node, NodeContext ctx) {
        return "Custom result for: " + node.getCustomField();
    }

    @Override
    public Class<MyCustomNode> getNodeType() {
        return MyCustomNode.class;
    }
}
```

---

## Retry Support

Built-in retry with configurable backoff strategies:

```java
RetryConfig config = RetryConfig.builder()
    .strategy(RetryStrategy.EXPONENTIAL_RANDOM)
    .maxAttempts(5)
    .initialDelayMs(500)
    .maxDelayMs(30000)
    .multiplier(3.0)
    .build();
```

| Strategy | Description |
|----------|-------------|
| `NONE` | No retries |
| `FIXED_DELAY` | Same delay between each retry |
| `LINEAR` | Delay increases linearly |
| `EXPONENTIAL` | Delay doubles each retry |
| `EXPONENTIAL_RANDOM` | Exponential with random jitter |
| `RANDOM` | Random delay between min/max |

Presets: `RetryConfig.DEFAULT` (3 attempts, exponential, 1s→10s) and `RetryConfig.NONE`.

---

## MCP Integration

### MCP Server — Expose Agents as Tools

Set `spring.ai.agents.mcp-server.enabled=true`. Each agent is auto-exposed as a callable MCP tool via `AgentToolCallbackProvider`. No manual registration needed.

### MCP Client — Call External Tools

Configure `spring.ai.mcp.client` to connect to external MCP servers. Use `ToolNode` in workflows to call external tools by name.

---

## Configuration Reference

| Property | Default | Description |
|----------|---------|-------------|
| `spring.ai.agents.reactive` | `false` | Use reactive execution mode (`Mono<String>`) |
| `spring.ai.agents.parallel-threads` | `0` | Thread pool size for parallel nodes (0 = cached) |
| `spring.ai.agents.mcp-server.enabled` | `false` | Expose agents as MCP tools |
| `spring.ai.agents.visualization.enabled` | `true` | Enable visualization dashboard (requires viz module) |

All executor beans use `@ConditionalOnMissingBean` — provide your own `@Bean` of any executor type to override the default.

---

## Running the Sample Application

The `spring-ai-agents-sample` module includes two example agents:

| Agent | Type | Workflow Pattern |
|-------|------|-----------------|
| `research-agent` | Single-workflow | Parallel fan-out (factual + analysis + guidelines → output) |
| `data-processor` | Multi-workflow | Two sequential workflows (extract / analyze) with LLM routing |

```bash
# Build everything
mvn clean install

# Run the sample
mvn spring-boot:run -pl spring-ai-agents-sample

# Or with a specific LLM provider
OPENAI_API_KEY=sk-xxx mvn spring-boot:run -pl spring-ai-agents-sample
```

The sample runs on `http://localhost:8086`.

---

## Visualization Dashboard

See **[spring-ai-agents-visualization/README.md](spring-ai-agents-visualization/README.md)** for the full visualization guide, including setup, features, and configuration options.

**Quick start:** Add the `spring-ai-agents-visualization` dependency and browse to `/agents-ui/`.

---

## Testing

```bash
# Run all tests
mvn test

# Run only starter tests
mvn test -pl spring-ai-agents-starter

# Run specific test class
mvn test -pl spring-ai-agents-starter -Dtest=WorkflowExecutorTest
```

---

## License

This project is licensed under the MIT License — see the [LICENSE](LICENSE) file for details.

