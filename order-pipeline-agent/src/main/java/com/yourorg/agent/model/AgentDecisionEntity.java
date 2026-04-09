package com.yourorg.agent.model;

import com.yourorg.agent.model.enums.AnomalyType;
import com.yourorg.agent.model.enums.Severity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "agent_decisions")
public class AgentDecisionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String region;

    private boolean anomalyDetected;

    @Enumerated(EnumType.STRING)
    private AnomalyType anomalyType;

    @Enumerated(EnumType.STRING)
    private Severity severity;

    @Column(length = 1000)
    private String reasoning;

    @Column(length = 500)
    private String recommendedAction;

    private LocalDateTime decidedAt;
}
