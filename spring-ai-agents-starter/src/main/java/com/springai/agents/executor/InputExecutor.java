package com.springai.agents.executor;

import com.springai.agents.node.InputNode;
import lombok.extern.slf4j.Slf4j;

/**
 * Executor for {@link InputNode}. Passes through the raw user input unchanged.
 */
@Slf4j
public class InputExecutor implements NodeExecutor<InputNode> {

    @Override
    public Object execute(InputNode node, NodeContext context) {
        log.debug("InputNode '{}': passing through raw input (length={})",
                node.getId(), context.getResolvedInput().length());
        return context.getResolvedInput();
    }

    @Override
    public Class<InputNode> getNodeType() {
        return InputNode.class;
    }
}
