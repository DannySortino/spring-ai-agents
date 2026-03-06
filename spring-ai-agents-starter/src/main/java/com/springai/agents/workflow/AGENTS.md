# Workflow Package — `com.springai.agents.workflow`

## Purpose
Core DAG-based workflow engine. Handles graph construction, validation,
topological sorting, parallel execution, and result propagation.

## Key Classes

| Class | Role |
|-------|------|
| `Edge` | Record representing a directed edge (from → to) between two nodes. |
| `Workflow` | Immutable DAG. Holds nodes, edges, adjacency/dependency maps. Provides topological order and level groups. |
| `WorkflowBuilder` | Fluent builder — `.node()` and `.edge()` separately. Validates at `build()`. |
| `WorkflowExecutor` | Sync orchestrator — executes nodes level-by-level, parallel when possible via `CompletableFuture`. |
| `ReactiveWorkflowExecutor` | Reactive orchestrator — uses `Flux.flatMap` for concurrent nodes, `Mono` chaining for levels. |
| `WorkflowResult` | `@Value @Builder` result — output string + all node results + timing. |

## Edge Definition
Nodes and edges are defined separately in the builder. **Prefer node-reference edges** to prevent typos:
```java
var input = InputNode.builder().id("input").build();
var step1 = LlmNode.builder().id("step1").promptTemplate("...").build();
var output = OutputNode.builder().id("output").build();

WorkflowBuilder.create()
    .nodes(input, step1, output)   // batch add via nodes()
    .edge(input, step1)            // type-safe: uses node.getId()
    .edge(step1, output)
    .build();
```

Alternative syntaxes (all equivalent):
```java
.edge("input", "step1")              // string IDs (legacy, more error-prone)
.edge(Edge.from("input").to("step1")) // Edge record
.edges(                                // batch edges
    Edge.from("input").to("step1"),
    Edge.from("step1").to("output")
)
```

## Execution Model
1. `WorkflowBuilder.build()` validates and freezes the DAG.
2. `WorkflowExecutor.execute()` (or `ReactiveWorkflowExecutor`) computes level groups.
3. Nodes at the same level run in parallel (no mutual dependencies).
4. Each node's executor receives its dependency results for `{nodeId}` interpolation.
5. Results stored in `ConcurrentHashMap`, available to downstream nodes.
6. The first output node's result is returned as the workflow output.

## Validation Rules (enforced at `build()` time)
- At least one `InputNode` and one `OutputNode` must exist
- No duplicate node IDs
- All edge references must resolve to existing nodes
- No cycles (DFS-based detection)

