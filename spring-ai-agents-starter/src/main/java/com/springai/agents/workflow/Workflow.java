package com.springai.agents.workflow;

import com.springai.agents.node.Node;
import com.springai.agents.node.InputNode;
import com.springai.agents.node.OutputNode;
import lombok.Getter;

import static java.util.Collections.*;
import static java.util.stream.Collectors.toUnmodifiableSet;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Immutable representation of a workflow as a Directed Acyclic Graph (DAG) of {@link Node}s
 * connected by {@link Edge}s.
 * <p>
 * A Workflow is constructed exclusively via {@link WorkflowBuilder} and validated at build time:
 * <ul>
 *   <li>Must contain at least one {@link InputNode} and one {@link OutputNode}</li>
 *   <li>Must be acyclic (no circular dependencies)</li>
 *   <li>All edge references must point to existing nodes</li>
 *   <li>No duplicate node IDs</li>
 * </ul>
 * <p>
 * Each workflow has a {@code name} and {@code description} used for multi-workflow routing
 * and MCP tool exposure.
 * <p>
 * The workflow itself does not execute — execution is delegated to
 * {@link WorkflowExecutor} or {@link ReactiveWorkflowExecutor}.
 *
 * @see WorkflowBuilder
 * @see WorkflowExecutor
 */
@Getter
public final class Workflow {

    /** Human-readable name for this workflow. Used for routing in multi-workflow agents. */
    private final String name;

    /** Description of what this workflow does. Used for LLM-based routing selection. */
    private final String description;

    /** All nodes in the workflow, keyed by node ID. */
    private final Map<String, Node> nodes;

    /** All edges in the workflow. */
    private final Set<Edge> edges;

    /** Adjacency list: nodeId → set of downstream nodeIds. */
    private final Map<String, Set<String>> adjacencyList;

    /** Dependency map: nodeId → set of upstream (dependency) nodeIds. */
    private final Map<String, Set<String>> dependencyMap;

    /** IDs of all InputNode instances. */
    private final Set<String> inputNodeIds;

    /** IDs of all OutputNode instances. */
    private final Set<String> outputNodeIds;

    /**
     * Package-private constructor — only {@link WorkflowBuilder} creates instances.
     */
    Workflow(String name, String description,
             Map<String, Node> nodes, Set<Edge> edges,
             Map<String, Set<String>> adjacencyList, Map<String, Set<String>> dependencyMap) {
        this.name = name;
        this.description = description;
        this.nodes = unmodifiableMap(new LinkedHashMap<>(nodes));
        this.edges = unmodifiableSet(new LinkedHashSet<>(edges));

        // Deep-copy to immutable
        var adjCopy = new LinkedHashMap<String, Set<String>>();
        adjacencyList.forEach((k, v) -> adjCopy.put(k, unmodifiableSet(new LinkedHashSet<>(v))));
        this.adjacencyList = unmodifiableMap(adjCopy);

        var depCopy = new LinkedHashMap<String, Set<String>>();
        dependencyMap.forEach((k, v) -> depCopy.put(k, unmodifiableSet(new LinkedHashSet<>(v))));
        this.dependencyMap = unmodifiableMap(depCopy);

        // Index input/output nodes
        this.inputNodeIds = nodes.values().stream()
                .filter(InputNode.class::isInstance)
                .map(Node::getId)
                .collect(toUnmodifiableSet());

        this.outputNodeIds = nodes.values().stream()
                .filter(OutputNode.class::isInstance)
                .map(Node::getId)
                .collect(toUnmodifiableSet());
    }

    /** Get the node with the given ID, or {@code null} if not found. */
    public Node getNode(String nodeId) {
        return nodes.get(nodeId);
    }

    /** Get the set of upstream dependency IDs for a given node. */
    public Set<String> getDependencies(String nodeId) {
        return dependencyMap.getOrDefault(nodeId, Set.of());
    }

    /** Get the set of downstream node IDs for a given node. */
    public Set<String> getDownstream(String nodeId) {
        return adjacencyList.getOrDefault(nodeId, Set.of());
    }

    /** Total number of nodes. */
    public int size() {
        return nodes.size();
    }

    /**
     * Compute topological sort order using Kahn's algorithm.
     * Nodes are returned in an order where every node appears after all its dependencies.
     *
     * @throws IllegalStateException if the graph has undetected cycles.
     */
    public List<String> getTopologicalOrder() {
        Map<String, Integer> inDegree = new HashMap<>();
        nodes.keySet().forEach(id -> inDegree.put(id, dependencyMap.getOrDefault(id, Set.of()).size()));

        Queue<String> queue = new LinkedList<>();
        inDegree.entrySet().stream()
                .filter(e -> e.getValue() == 0)
                .map(Map.Entry::getKey)
                .forEach(queue::offer);

        List<String> result = new ArrayList<>();
        while (!queue.isEmpty()) {
            String current = queue.poll();
            result.add(current);
            for (String downstream : adjacencyList.getOrDefault(current, Set.of())) {
                int newDegree = inDegree.get(downstream) - 1;
                inDegree.put(downstream, newDegree);
                if (newDegree == 0) queue.offer(downstream);
            }
        }

        if (result.size() != nodes.size()) {
            throw new IllegalStateException("Topological sort failed — graph has undetected cycles");
        }
        return unmodifiableList(result);
    }

    /**
     * Group nodes by execution level for parallel execution.
     * Nodes at the same level have no mutual dependencies and can run concurrently.
     *
     * @return Ordered map of level → list of node IDs at that level.
     */
    public Map<Integer, List<String>> getLevelGroups() {
        List<String> order = getTopologicalOrder();
        Map<String, Integer> nodeLevel = new HashMap<>();
        Map<Integer, List<String>> levelGroups = new TreeMap<>();

        for (String nodeId : order) {
            int level = 0;
            for (String dep : dependencyMap.getOrDefault(nodeId, Set.of())) {
                level = Math.max(level, nodeLevel.get(dep) + 1);
            }
            nodeLevel.put(nodeId, level);
            levelGroups.computeIfAbsent(level, k -> new ArrayList<>()).add(nodeId);
        }
        return unmodifiableMap(levelGroups);
    }
}

