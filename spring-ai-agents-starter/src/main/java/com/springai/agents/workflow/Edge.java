package com.springai.agents.workflow;

import lombok.NonNull;

/**
 * Represents a directed edge between two nodes in a workflow DAG.
 * <p>
 * An edge defines a data dependency: the {@code to} node cannot execute until the
 * {@code from} node has completed, and the output of {@code from} is available to
 * {@code to} via {@code {from}} placeholder interpolation.
 *
 * @param from Source node ID — this node must complete first.
 * @param to   Target node ID — this node depends on the source.
 */
public record Edge(@NonNull String from, @NonNull String to) {

    /**
     * Factory method for readable edge construction.
     *
     * <pre>{@code
     * Edge.from("input").to("process");
     * }</pre>
     */
    public static EdgeBuilder from(String from) {
        return new EdgeBuilder(from);
    }

    /**
     * Fluent builder for creating edges with readable syntax.
     */
    public record EdgeBuilder(String from) {
        public Edge to(String to) {
            return new Edge(from, to);
        }
    }
}

