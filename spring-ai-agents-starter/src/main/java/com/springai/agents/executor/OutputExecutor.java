package com.springai.agents.executor;

import com.springai.agents.node.OutputNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Mono;

import static reactor.core.scheduler.Schedulers.boundedElastic;

/**
 * Executor for {@link OutputNode}. Returns the final workflow result.
 * <p>
 * Output strategy priority:
 * <ol>
 *   <li>Custom {@code outputHandler} function (if set)</li>
 *   <li>LLM post-processing via {@code postProcessPrompt} (if set)</li>
 *   <li>Pass-through of combined dependency outputs</li>
 * </ol>
 */
@Slf4j
@RequiredArgsConstructor
public class OutputExecutor implements NodeExecutor<OutputNode>, ReactiveNodeExecutor<OutputNode> {

    private final ChatModel chatModel;

    @Override
    public Object execute(OutputNode node, NodeContext context) {
        // 1. Custom output handler takes priority
        if (node.getOutputHandler() != null) {
            log.debug("OutputNode '{}': using custom output handler", node.getId());
            return node.getOutputHandler().apply(context);
        }

        // 2. LLM post-processing
        if (node.getPostProcessPrompt() != null && !node.getPostProcessPrompt().isBlank()) {
            String processedPrompt = PromptInterpolator.interpolate(
                    node.getPostProcessPrompt(), context.getDependencyResults(), context.getExecutionContext());
            log.debug("OutputNode '{}': post-processing with LLM", node.getId());
            return chatModel.call(new Prompt(processedPrompt)).getResult().getOutput().getText();
        }

        // 3. Pass-through
        log.debug("OutputNode '{}': passing through dependency output", node.getId());
        return context.getResolvedInput();
    }

    @Override
    public Mono<Object> executeReactive(OutputNode node, NodeContext context) {
        if (node.getOutputHandler() != null) {
            return Mono.fromCallable(() -> (Object) node.getOutputHandler().apply(context))
                    .subscribeOn(boundedElastic());
        }
        if (node.getPostProcessPrompt() != null && !node.getPostProcessPrompt().isBlank()) {
            return Mono.fromCallable(() -> execute(node, context))
                    .subscribeOn(boundedElastic());
        }
        return Mono.just(context.getResolvedInput());
    }

    @Override
    public Class<OutputNode> getNodeType() {
        return OutputNode.class;
    }
}
