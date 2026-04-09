package com.yourorg.agent.tool;

import java.util.Map;

public interface AgentTool {

    /**
     * Unique name used by the LLM to reference this tool.
     * Must match values returned in AgentDecision.toolsToCall.
     */
    String getName();

    /**
     * Human-readable description of what this tool does.
     * Included in prompts so the LLM knows when to invoke it.
     */
    String getDescription();

    /**
     * Execute the tool with the provided parameters.
     *
     * @param params key-value map of inputs; structure depends on each tool implementation
     * @return result object — callers should cast to the expected type
     */
    Object execute(Map<String, Object> params);
}
