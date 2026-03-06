package com.springai.agents.executor;

import com.springai.agents.node.LlmNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;

import static reactor.core.scheduler.Schedulers.boundedElastic;

/**
 * Executor for {@link LlmNode}. Sends an interpolated prompt to the LLM and returns the response.
 * <p>
 * The prompt template is interpolated with {@code {nodeId}} placeholders replaced by
 * dependency outputs and {@code {input}} replaced by the raw user input. If the node
 * has a {@code systemPrompt}, it is sent as a proper system message to the LLM.
 * <p>
 * Usage metadata (input/output tokens) is logged when available.
 */
@Slf4j
@RequiredArgsConstructor
public class LlmExecutor implements NodeExecutor<LlmNode>, ReactiveNodeExecutor<LlmNode> {

    private final ChatModel chatModel;

    @Override
    public Object execute(LlmNode node, NodeContext context) {
        Prompt prompt = buildPrompt(node, context);
        log.debug("LlmNode '{}': calling LLM with {} message(s)", node.getId(), prompt.getInstructions().size());
        
        ChatResponse response = chatModel.call(prompt);
        
        // Log usage metadata if available
        var metadata = response.getMetadata();
        if (metadata != null && metadata.getUsage() != null) {
            var usage = metadata.getUsage();
            log.debug("LlmNode '{}': tokens used — input={}, output={}, total={}",
                    node.getId(),
                    usage.getPromptTokens(),
                    usage.getCompletionTokens(),
                    usage.getTotalTokens());
            
            // Store usage in execution context for downstream access, if possible
            try {
                context.getExecutionContext().put(node.getId() + "_usage", usage);
            } catch (UnsupportedOperationException ex) {
                // Execution context may be backed by an unmodifiable map (e.g. Map.of()); skip storing usage
                log.trace("LlmNode '{}': execution context is unmodifiable; skipping usage storage", node.getId());
            } catch (Exception ex) {
                // Avoid failing node execution due to unexpected context issues
                log.warn("LlmNode '{}': failed to store usage metadata in execution context", node.getId(), ex);
            }
        }
        
        return response.getResult().getOutput().getText();
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

    /**
     * Build a proper Prompt with system and user messages.
     * Uses Spring AI's message types instead of string concatenation.
     */
    private Prompt buildPrompt(LlmNode node, NodeContext context) {
        String userPrompt = PromptInterpolator.interpolate(
                node.getPromptTemplate(), context.getDependencyResults(), context.getExecutionContext());

        List<org.springframework.ai.chat.messages.Message> messages = new ArrayList<>();
        
        // Add system message if present
        if (node.getSystemPrompt() != null && !node.getSystemPrompt().isBlank()) {
            messages.add(new SystemMessage(node.getSystemPrompt()));
        }
        
        // Add user message
        messages.add(new UserMessage(userPrompt));
        
        return new Prompt(messages);
    }
}
