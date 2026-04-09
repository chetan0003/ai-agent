package com.yourorg.agent.repository;

import com.yourorg.agent.model.OrderEntity;
import com.yourorg.agent.model.enums.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface OrderRepository extends JpaRepository<OrderEntity, Long> {

    // ── Count queries (used by OrderMetricsTool) ──────────────────────────────

    long countByRegionAndCreatedAtBetween(
            String region,
            LocalDateTime start,
            LocalDateTime end);

    long countByRegionAndStatusAndCreatedAtBetween(
            String region,
            OrderStatus status,
            LocalDateTime start,
            LocalDateTime end);

    // ── Aggregate queries ─────────────────────────────────────────────────────

    @Query("""
            SELECT AVG(o.processingTimeMs)
            FROM OrderEntity o
            WHERE o.region = :region
              AND o.createdAt BETWEEN :start AND :end
              AND o.processingTimeMs IS NOT NULL
            """)
    Double avgProcessingTimeMs(
            @Param("region") String region,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);

    @Query("""
            SELECT o.status AS status, COUNT(o) AS cnt
            FROM OrderEntity o
            WHERE o.region = :region
              AND o.createdAt BETWEEN :start AND :end
            GROUP BY o.status
            """)
    List<Object[]> countGroupedByStatus(
            @Param("region") String region,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);

    // ── Stalled order detection ───────────────────────────────────────────────

    @Query("""
            SELECT o FROM OrderEntity o
            WHERE o.region = :region
              AND o.status IN ('PENDING', 'IN_PROGRESS')
              AND o.createdAt < :stalledBefore
            """)
    List<OrderEntity> findStalledOrders(
            @Param("region") String region,
            @Param("stalledBefore") LocalDateTime stalledBefore);

    // ── Failure analysis ──────────────────────────────────────────────────────

    @Query("""
            SELECT o.failureReason AS reason, COUNT(o) AS cnt
            FROM OrderEntity o
            WHERE o.region = :region
              AND o.status = 'FAILED'
              AND o.createdAt BETWEEN :start AND :end
            GROUP BY o.failureReason
            ORDER BY cnt DESC
            """)
    List<Object[]> topFailureReasons(
            @Param("region") String region,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);

    // ── General lookups ───────────────────────────────────────────────────────

    List<OrderEntity> findByRegionAndStatusAndCreatedAtBetween(
            String region,
            OrderStatus status,
            LocalDateTime start,
            LocalDateTime end);

    List<OrderEntity> findByStoreIdAndCreatedAtBetween(
            String storeId,
            LocalDateTime start,
            LocalDateTime end);
}
