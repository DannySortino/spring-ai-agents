package com.springai.agents.executor;

import com.springai.agents.node.LlmNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Mono;

import static reactor.core.scheduler.Schedulers.boundedElastic;

/**
 * Executor for {@link LlmNode}. Sends an interpolated prompt to the LLM and returns the response.
 * <p>
 * The prompt template is interpolated with {@code {nodeId}} placeholders replaced by
 * dependency outputs and {@code {input}} replaced by the raw user input. If the node
 * has a {@code systemPrompt}, it is prepended to the final prompt.
 */
@Slf4j
@RequiredArgsConstructor
public class LlmExecutor implements NodeExecutor<LlmNode>, ReactiveNodeExecutor<LlmNode> {

    private final ChatModel chatModel;

    @Override
    public Object execute(LlmNode node, NodeContext context) {
        String processedPrompt = buildPrompt(node, context);
        log.debug("LlmNode '{}': calling LLM with prompt length={}", node.getId(), processedPrompt.length());
        return chatModel.call(new Prompt(processedPrompt)).getResult().getOutput().getText();
    }

    @Override
    public Mono<Object> executeReactive(LlmNode node, NodeContext context) {
        return Mono.fromCallable(() -> execute(node, context))
                .subscribeOn(boundedElastic());
    }

    @Override
    public Class<LlmNode> getNodeType() {
        return LlmNode.class;
    }

    private String buildPrompt(LlmNode node, NodeContext context) {
        String prompt = PromptInterpolator.interpolate(
                node.getPromptTemplate(), context.getDependencyResults(), context.getExecutionContext());

        if (node.getSystemPrompt() != null && !node.getSystemPrompt().isBlank()) {
            prompt = node.getSystemPrompt() + "\n\n" + prompt;
        }
        return prompt;
    }
}
