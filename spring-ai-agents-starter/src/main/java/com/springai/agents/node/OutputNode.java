package com.springai.agents.node;

import com.springai.agents.executor.NodeContext;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.NonNull;
import lombok.Value;
import lombok.experimental.SuperBuilder;

import java.util.function.Function;

/**
 * Terminal node for a workflow. Returns the final result to the caller.
 * <p>
 * Every workflow must have at least one {@code OutputNode}. Supports three
 * output strategies in priority order:
 * <ol>
 *   <li>{@code outputHandler} — a custom function for full control over formatting</li>
 *   <li>{@code postProcessPrompt} — LLM-based post-processing/summarization</li>
 *   <li>Pass-through of combined dependency outputs</li>
 * </ol>
 *
 * <pre>{@code
 * // Simple pass-through output
 * OutputNode.builder().id("result").build();
 *
 * // Output with LLM post-processing
 * OutputNode.builder()
 *     .id("result")
 *     .postProcessPrompt("Summarize the following: {analyze}")
 *     .build();
 *
 * // Output with custom handler
 * OutputNode.builder()
 *     .id("result")
 *     .outputHandler(ctx -> {
 *         String analysis = ctx.getDependencyResult("analyze", String.class);
 *         return "## Report\n\n" + analysis.toUpperCase();
 *     })
 *     .build();
 * }</pre>
 */
@Value
@SuperBuilder
@EqualsAndHashCode(callSuper = false)
public class OutputNode extends Node {

    /** Unique identifier for this output node. */
    @NonNull
    String id;

    /**
     * Optional LLM prompt for post-processing the final result.
     * Supports {@code {nodeId}} placeholders replaced with dependency outputs.
     * Ignored if {@code outputHandler} is set.
     */
    String postProcessPrompt;

    /**
     * Optional custom output handler function for full control over result formatting.
     * Receives the {@link NodeContext} with all dependency results available.
     * Takes priority over {@code postProcessPrompt} when both are set.
     */
    @Builder.Default
    transient Function<NodeContext, String> outputHandler = null;
}
