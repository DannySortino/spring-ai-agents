package com.springai.agents.executor;

import com.springai.agents.node.RestNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import static com.springai.agents.executor.PromptInterpolator.interpolate;

/**
 * Executor for {@link RestNode}. Executes HTTP REST API calls using Spring {@link WebClient}.
 * <p>
 * URL, body, and header values are interpolated with {@code {nodeId}} placeholders
 * from dependency outputs before the HTTP call is made.
 * <p>
 * Supports three body strategies in priority order:
 * <ol>
 *   <li>{@code bodyProvider} — a function receiving typed dependency results, returns an Object serialized as JSON</li>
 *   <li>{@code bodyTemplate} — a string template with placeholder interpolation</li>
 *   <li>Resolved input (combined dependency outputs) as fallback</li>
 * </ol>
 * <p>
 * Implements both sync and reactive interfaces — the reactive variant returns the
 * native {@link Mono} from WebClient without blocking.
 */
@Slf4j
public class RestExecutor implements NodeExecutor<RestNode>, ReactiveNodeExecutor<RestNode> {

    private final WebClient.Builder webClientBuilder;
    private final ObjectMapper objectMapper;

    public RestExecutor(WebClient.Builder webClientBuilder) {
        this(webClientBuilder, new ObjectMapper());
    }

    public RestExecutor(WebClient.Builder webClientBuilder, ObjectMapper objectMapper) {
        this.webClientBuilder = webClientBuilder;
        this.objectMapper = objectMapper;
    }

    @Override
    public Object execute(RestNode node, NodeContext context) {
        return executeReactive(node, context).block();
    }

    @Override
    public Mono<Object> executeReactive(RestNode node, NodeContext context) {
        var depResults = context.getDependencyResults();
        var execContext = context.getExecutionContext();
        String resolvedUrl = interpolate(node.getUrl(), depResults, execContext);

        log.debug("RestNode '{}': {} {}", node.getId(), node.getMethod(), resolvedUrl);

        WebClient webClient = webClientBuilder.build();

        WebClient.RequestHeadersSpec<?> requestSpec = buildRequest(webClient, node, resolvedUrl, context);

        // Apply headers via headers consumer
        return requestSpec
                .headers(headers -> node.getHeaders().forEach((key, value) ->
                        headers.set(key, interpolate(value, depResults, execContext))))
                .retrieve()
                .bodyToMono(String.class)
                .timeout(node.getTimeout())
                .map(body -> (Object) body)
                .doOnSuccess(body -> log.debug("RestNode '{}': received response length={}", node.getId(),
                        body != null ? body.toString().length() : 0));
    }

    @Override
    public Class<RestNode> getNodeType() {
        return RestNode.class;
    }

    private WebClient.RequestHeadersSpec<?> buildRequest(WebClient client, RestNode node,
                                                         String url, NodeContext context) {
        var depResults = context.getDependencyResults();
        HttpMethod method = node.getMethod();

        if (HttpMethod.POST.equals(method) || HttpMethod.PUT.equals(method) || HttpMethod.PATCH.equals(method)) {
            Object body = resolveBody(node, context);
            if (body instanceof String stringBody) {
                return client.method(method).uri(url).bodyValue(stringBody);
            }
            // Serialize POJO to JSON
            try {
                String jsonBody = objectMapper.writeValueAsString(body);
                return client.method(method).uri(url)
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(jsonBody);
            } catch (Exception e) {
                throw new IllegalStateException("Failed to serialize body for RestNode '%s': %s"
                        .formatted(node.getId(), e.getMessage()), e);
            }
        }

        return client.method(method).uri(url);
    }

    /**
     * Resolve the request body using the priority: bodyProvider → bodyTemplate → resolvedInput.
     */
    private Object resolveBody(RestNode node, NodeContext context) {
        // 1. Body provider function (returns typed object)
        if (node.getBodyProvider() != null) {
            log.debug("RestNode '{}': using bodyProvider function", node.getId());
            return node.getBodyProvider().apply(context.getDependencyResults());
        }
        // 2. Body template (string interpolation)
        if (node.getBodyTemplate() != null) {
            return interpolate(node.getBodyTemplate(), context.getDependencyResults(), context.getExecutionContext());
        }
        // 3. Fallback to resolved input
        return context.getResolvedInput();
    }
}
