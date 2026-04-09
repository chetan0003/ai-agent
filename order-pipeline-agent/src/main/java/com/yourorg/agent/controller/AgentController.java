package com.yourorg.agent.controller;

import com.yourorg.agent.agent.OrderPipelineAgent;
import com.yourorg.agent.model.AgentDecisionEntity;
import com.yourorg.agent.repository.AgentDecisionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/agent")
@Slf4j
@RequiredArgsConstructor
public class AgentController {

    private final OrderPipelineAgent agent;
    private final AgentDecisionRepository decisionRepository;

    /**
     * Manually trigger a full agent cycle across all regions.
     * Useful for on-demand runs without waiting for the cron schedule.
     */
    @PostMapping("/trigger")
    public ResponseEntity<Map<String, String>> triggerAll() {
        log.info("Manual full agent cycle triggered via REST");
        agent.runAgentCycle();
        return ResponseEntity.ok(Map.of("status", "triggered", "scope", "all regions"));
    }

    /**
     * Trigger agent analysis for a specific region only.
     */
    @PostMapping("/trigger/{region}")
    public ResponseEntity<Map<String, String>> triggerRegion(@PathVariable String region) {
        log.info("Manual agent cycle triggered for region: {}", region);
        agent.analyzeRegion(region.toUpperCase());
        return ResponseEntity.ok(Map.of("status", "triggered", "region", region));
    }

    /**
     * Retrieve all stored anomaly decisions (audit log).
     */
    @GetMapping("/decisions")
    public ResponseEntity<List<AgentDecisionEntity>> getAllDecisions() {
        return ResponseEntity.ok(decisionRepository.findAll());
    }

    /**
     * Retrieve anomaly decisions for a specific region.
     */
    @GetMapping("/decisions/{region}")
    public ResponseEntity<List<AgentDecisionEntity>> getDecisionsByRegion(
            @PathVariable String region) {
        return ResponseEntity.ok(
                decisionRepository.findByRegionOrderByDecidedAtDesc(region.toUpperCase()));
    }

    /**
     * Retrieve only decisions where an anomaly was detected.
     */
    @GetMapping("/decisions/anomalies")
    public ResponseEntity<List<AgentDecisionEntity>> getAnomaliesOnly() {
        return ResponseEntity.ok(
                decisionRepository.findByAnomalyDetectedTrueOrderByDecidedAtDesc());
    }

    /**
     * Health probe for the agent service itself.
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of("status", "UP", "service", "order-pipeline-agent"));
    }
}
