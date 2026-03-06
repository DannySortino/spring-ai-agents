package com.springai.agents.workflow;

import com.springai.agents.node.InputNode;
import com.springai.agents.node.Node;
import com.springai.agents.node.OutputNode;

import java.util.*;

/**
 * Fluent builder for constructing a validated {@link Workflow} DAG.
 * <p>
 * Nodes and edges are defined separately, giving full control over the graph structure.
 * All validation happens at {@link #build()} time.
 * <p>
 * Edges can reference nodes by their object (type-safe) or by string ID:
 *
 * <pre>{@code
 * var input = InputNode.builder().id("input").build();
 * var process = LlmNode.builder().id("process").promptTemplate("Do: {input}").build();
 * var output = OutputNode.builder().id("output").build();
 *
 * Workflow workflow = WorkflowBuilder.create()
 *     .name("my-workflow")
 *     .description("Processes user input")
 *     .nodes(input, process, output)
 *     .edge(input, process)
 *     .edge(process, output)
 *     .build();
 * }</pre>
 *
 * <b>Validation rules enforced at build time:</b>
 * <ul>
 *   <li>At least one {@link InputNode} must exist</li>
 *   <li>At least one {@link OutputNode} must exist</li>
 *   <li>No duplicate node IDs</li>
 *   <li>All edge references must point to existing nodes</li>
 *   <li>No cycles in the dependency graph (DFS-based detection)</li>
 * </ul>
 */
public final class WorkflowBuilder {

    private final Map<String, Node> nodes = new LinkedHashMap<>();
    private final Set<Edge> edges = new LinkedHashSet<>();
    private String name = "default";
    private String description = "";

    private WorkflowBuilder() {}

    /** Create a new empty WorkflowBuilder. */
    public static WorkflowBuilder create() {
        return new WorkflowBuilder();
    }

    /**
     * Set the workflow name. Used for multi-workflow routing.
     * Defaults to {@code "default"}.
     */
    public WorkflowBuilder name(String name) {
        this.name = name;
        return this;
    }

    /**
     * Set the workflow description. Used for LLM-based routing selection.
     * Defaults to empty string.
     */
    public WorkflowBuilder description(String description) {
        this.description = description;
        return this;
    }

    // ── Node Registration ───────────────────────────────────────────────

    /**
     * Add a single node to the workflow.
     *
     * @throws IllegalArgumentException if a node with the same ID already exists.
     */
    public WorkflowBuilder node(Node node) {
        if (nodes.containsKey(node.getId())) {
            throw new IllegalArgumentException("Duplicate node ID: '" + node.getId() + "'");
        }
        nodes.put(node.getId(), node);
        return this;
    }

    /**
     * Add multiple nodes to the workflow at once.
     *
     * <pre>{@code
     * builder.nodes(inputNode, processNode, outputNode);
     * }</pre>
     *
     * @throws IllegalArgumentException if any node ID is duplicated.
     */
    public WorkflowBuilder nodes(Node... nodes) {
        for (Node n : nodes) {
            node(n);
        }
        return this;
    }

    // ── Edge Registration ───────────────────────────────────────────────

    /**
     * Add a directed edge between two nodes by their string IDs.
     *
     * @param from Source node ID — must complete before target can start.
     * @param to   Target node ID — depends on the source node's output.
     */
    public WorkflowBuilder edge(String from, String to) {
        edges.add(new Edge(from, to));
        return this;
    }

    /**
     * Add a directed edge between two nodes using their {@link Node} references.
     * This is the preferred approach — it prevents typos by using the node objects directly.
     *
     * <pre>{@code
     * var input = InputNode.builder().id("input").build();
     * var process = LlmNode.builder().id("process").promptTemplate("...").build();
     * builder.edge(input, process);
     * }</pre>
     *
     * @param from Source node — must complete before target can start.
     * @param to   Target node — depends on the source node's output.
     */
    public WorkflowBuilder edge(Node from, Node to) {
        edges.add(new Edge(from.getId(), to.getId()));
        return this;
    }

    /**
     * Add a directed edge using the {@link Edge} record directly.
     */
    public WorkflowBuilder edge(Edge edge) {
        edges.add(edge);
        return this;
    }

    /**
     * Add multiple edges at once using {@link Edge} records.
     *
     * <pre>{@code
     * builder.edges(
     *     Edge.from("input").to("process"),
     *     Edge.from("process").to("output")
     * );
     * }</pre>
     */
    public WorkflowBuilder edges(Edge... edges) {
        for (Edge e : edges) {
            edge(e);
        }
        return this;
    }

    // ── Build & Validate ────────────────────────────────────────────────

    /**
     * Build and validate the workflow.
     *
     * @return An immutable, validated {@link Workflow}.
     * @throws IllegalArgumentException if validation fails.
     */
    public Workflow build() {
        validateRequiredNodes();
        validateEdgeReferences();

        // Build adjacency list and dependency map from edges
        Map<String, Set<String>> adjacencyList = new HashMap<>();
        Map<String, Set<String>> dependencyMap = new HashMap<>();
        nodes.keySet().forEach(id -> {
            adjacencyList.put(id, new LinkedHashSet<>());
            dependencyMap.put(id, new LinkedHashSet<>());
        });

        for (Edge edge : edges) {
            adjacencyList.get(edge.from()).add(edge.to());
            dependencyMap.get(edge.to()).add(edge.from());
        }

        // Validate no cycles
        if (hasCycle(adjacencyList)) {
            throw new IllegalArgumentException("Workflow graph contains cycles — this would create infinite loops");
        }

        return new Workflow(name, description, nodes, edges, adjacencyList, dependencyMap);
    }

    // ── Validation ──────────────────────────────────────────────────────

    private void validateRequiredNodes() {
        boolean hasInput = nodes.values().stream().anyMatch(InputNode.class::isInstance);
        boolean hasOutput = nodes.values().stream().anyMatch(OutputNode.class::isInstance);

        if (!hasInput) {
            throw new IllegalArgumentException(
                    "Workflow must contain at least one InputNode (use InputNode.builder().id(\"...\").build())");
        }
        if (!hasOutput) {
            throw new IllegalArgumentException(
                    "Workflow must contain at least one OutputNode (use OutputNode.builder().id(\"...\").build())");
        }
    }

    private void validateEdgeReferences() {
        for (Edge edge : edges) {
            if (!nodes.containsKey(edge.from())) {
                throw new IllegalArgumentException(
                        "Edge references non-existent source node: '" + edge.from() + "'");
            }
            if (!nodes.containsKey(edge.to())) {
                throw new IllegalArgumentException(
                        "Edge references non-existent target node: '" + edge.to() + "'");
            }
        }
    }

    /** DFS-based cycle detection. */
    private boolean hasCycle(Map<String, Set<String>> adjacencyList) {
        Set<String> visited = new HashSet<>();
        Set<String> recursionStack = new HashSet<>();
        for (String node : nodes.keySet()) {
            if (hasCycleDFS(node, adjacencyList, visited, recursionStack)) return true;
        }
        return false;
    }

    private boolean hasCycleDFS(String node, Map<String, Set<String>> adj,
                                Set<String> visited, Set<String> stack) {
        if (stack.contains(node)) return true;
        if (visited.contains(node)) return false;
        visited.add(node);
        stack.add(node);
        for (String neighbor : adj.getOrDefault(node, Set.of())) {
            if (hasCycleDFS(neighbor, adj, visited, stack)) return true;
        }
        stack.remove(node);
        return false;
    }
}

