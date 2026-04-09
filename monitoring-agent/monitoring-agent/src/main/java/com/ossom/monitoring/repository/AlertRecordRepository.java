package com.ossom.monitoring.repository;

import com.ossom.monitoring.model.AlertRecord;
import com.ossom.monitoring.model.Severity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface AlertRecordRepository extends JpaRepository<AlertRecord, Long> {

    List<AlertRecord> findByHostIdOrderByFiredAtDesc(String hostId);

    List<AlertRecord> findBySeverityOrderByFiredAtDesc(Severity severity);

    List<AlertRecord> findByFiredAtBetweenOrderByFiredAtDesc(Instant from, Instant to);

    long countBySeverity(Severity severity);
}
