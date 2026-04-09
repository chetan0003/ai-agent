package com.yourorg.agent.repository;

import com.yourorg.agent.model.AgentDecisionEntity;
import com.yourorg.agent.model.enums.AnomalyType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface AgentDecisionRepository extends JpaRepository<AgentDecisionEntity, Long> {

    List<AgentDecisionEntity> findByRegionOrderByDecidedAtDesc(String region);

    List<AgentDecisionEntity> findByAnomalyDetectedTrueOrderByDecidedAtDesc();

    Optional<AgentDecisionEntity> findTopByRegionAndAnomalyTypeAndDecidedAtAfter(
            String region,
            AnomalyType anomalyType,
            LocalDateTime after);

    List<AgentDecisionEntity> findByRegionAndDecidedAtBetween(
            String region,
            LocalDateTime start,
            LocalDateTime end);
}
