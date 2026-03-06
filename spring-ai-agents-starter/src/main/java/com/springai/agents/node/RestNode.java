package com.springai.agents.node;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.NonNull;
import lombok.Singular;
import lombok.Value;
import lombok.experimental.SuperBuilder;
import org.springframework.http.HttpMethod;

import java.time.Duration;
import java.util.Map;
import java.util.function.Function;

/**
 * A node that executes an HTTP REST API call and returns the response body as a string.
 * <p>
 * URL, body template, and header values all support {@code {nodeId}} placeholder interpolation
 * from dependency node outputs.
 * <p>
 * Supports three body strategies in priority order:
 * <ol>
 *   <li>{@code bodyProvider} — a function receiving typed dependency results, returns an Object serialized as JSON</li>
 *   <li>{@code bodyTemplate} — a string template with placeholder interpolation</li>
 *   <li>Resolved input (combined dependency outputs) as fallback</li>
 * </ol>
 *
 * <pre>{@code
 * // Simple GET with URL interpolation
 * RestNode.builder()
 *     .id("fetch-data")
 *     .url("https://api.example.com/search?q={user-input}")
 *     .method(HttpMethod.GET)
 *     .header("Authorization", "Bearer my-token")
 *     .timeout(Duration.ofSeconds(10))
 *     .build();
 *
 * // POST with typed body provider
 * RestNode.builder()
 *     .id("submit")
 *     .url("https://api.example.com/submit")
 *     .method(HttpMethod.POST)
 *     .bodyProvider(deps -> {
 *         MyData data = (MyData) deps.get("extract");
 *         return new SubmitRequest(data.getId(), data.getName());
 *     })
 *     .build();
 * }</pre>
 */
@Value
@SuperBuilder
@EqualsAndHashCode(callSuper = false)
public class RestNode extends Node {

    /** Unique identifier for this REST node. */
    @NonNull
    String id;

    /** URL template for the REST call. Supports {@code {nodeId}} placeholders. */
    @NonNull
    String url;

    /** HTTP method to use. Defaults to GET. */
    @NonNull
    @Builder.Default
    HttpMethod method = HttpMethod.GET;

    /** Optional request body template. Supports {@code {nodeId}} placeholders. */
    String bodyTemplate;

    /**
     * Optional body provider function that receives typed dependency results and returns
     * an object to be serialized as the request body (via Jackson).
     * <p>
     * Takes priority over {@code bodyTemplate} when both are set.
     */
    @Builder.Default
    transient Function<Map<String, Object>, Object> bodyProvider = null;

    /** HTTP headers to include. Values support {@code {nodeId}} placeholders. */
    @Singular
    Map<String, String> headers;

    /** Request timeout. Defaults to 30 seconds. */
    @NonNull
    @Builder.Default
    Duration timeout = Duration.ofSeconds(30);
}
