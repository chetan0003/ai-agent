package com.yourorg.agent.model;

import com.yourorg.agent.model.enums.OrderStatus;
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
@Table(name = "orders", indexes = {
        @Index(name = "idx_orders_region", columnList = "region"),
        @Index(name = "idx_orders_status", columnList = "status"),
        @Index(name = "idx_orders_created_at", columnList = "created_at"),
        @Index(name = "idx_orders_region_status", columnList = "region, status")
})
public class OrderEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_ref", nullable = false, unique = true, length = 64)
    private String orderRef;                  // External order reference (e.g. OLO order ID)

    @Column(name = "region", nullable = false, length = 32)
    private String region;                    // SRI_LANKA, BANGLADESH, POPEYES

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private OrderStatus status;               // PENDING, PLACED, IN_PROGRESS, COMPLETED, FAILED, CANCELLED

    @Column(name = "store_id", length = 64)
    private String storeId;                   // Store identifier

    @Column(name = "store_name", length = 128)
    private String storeName;

    @Column(name = "customer_id", length = 64)
    private String customerId;

    @Column(name = "total_amount")
    private Double totalAmount;

    @Column(name = "currency", length = 8)
    private String currency;

    @Column(name = "channel", length = 32)
    private String channel;                   // APP, WEB, KIOSK, POS

    @Column(name = "processing_time_ms")
    private Long processingTimeMs;            // Duration from placed to completed/failed

    @Column(name = "failure_reason", length = 512)
    private String failureReason;             // Populated when status = FAILED

    @Column(name = "vendor", length = 64)
    private String vendor;                    // PROGILANT, VCTECH, etc.

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
        if (status == OrderStatus.COMPLETED || status == OrderStatus.FAILED) {
            if (completedAt == null) {
                completedAt = LocalDateTime.now();
            }
            if (createdAt != null && completedAt != null) {
                processingTimeMs = java.time.Duration.between(createdAt, completedAt).toMillis();
            }
        }
    }
}
