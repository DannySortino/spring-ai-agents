package com.springai.agents.node;

import lombok.EqualsAndHashCode;
import lombok.NonNull;
import lombok.Value;
import lombok.experimental.SuperBuilder;

/**
 * Entry point node for a workflow. Receives and passes through raw user input.
 * <p>
 * Every workflow must have at least one {@code InputNode}. The user defines its
 * ID freely — there is no fixed naming convention.
 *
 * <pre>{@code
 * InputNode.builder()
 *     .id("user-request")
 *     .build();
 * }</pre>
 */
@Value
@SuperBuilder
@EqualsAndHashCode(callSuper = false)
public class InputNode extends Node {

    /** Unique identifier for this input node. */
    @NonNull
    String id;
}
