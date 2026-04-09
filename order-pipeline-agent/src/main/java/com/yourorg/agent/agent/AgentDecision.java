package com.yourorg.agent.agent;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class AgentDecision {

    @JsonProperty("anomalyDetected")
    private boolean anomalyDetected;

    @JsonProperty("anomalyType")
    private String anomalyType;

    @JsonProperty("severity")
    private String severity;

    @JsonProperty("reasoning")
    private String reasoning;

    @JsonProperty("recommendedAction")
    private String recommendedAction;

    @JsonProperty("toolsToCall")
    private List<String> toolsToCall;
}
