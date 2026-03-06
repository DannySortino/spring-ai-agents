package com.springai.agents.executor;

import com.springai.agents.node.ContextNode;
import lombok.extern.slf4j.Slf4j;

/**
 * Executor for {@link ContextNode}. Returns the static context text unchanged.
 * No external calls are made.
 */
@Slf4j
public class ContextExecutor implements NodeExecutor<ContextNode> {

    @Override
    public Object execute(ContextNode node, NodeContext context) {
        log.debug("ContextNode '{}': injecting context text (length={})",
                node.getId(), node.getContextText().length());
        return node.getContextText();
    }

    @Override
    public Class<ContextNode> getNodeType() {
        return ContextNode.class;
    }
}
