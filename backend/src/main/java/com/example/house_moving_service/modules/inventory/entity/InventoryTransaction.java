package com.example.house_moving_service.modules.inventory.entity;

import com.example.house_moving_service.modules.booking.entity.Booking;
import com.example.house_moving_service.modules.identity.entity.User;
import com.example.house_moving_service.modules.inventory.enums.InventoryTransactionType;
import com.example.house_moving_service.modules.scheduling.entity.MovingSchedule;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Sổ nhật ký kho: mỗi lần tồn kho thay đổi ghi một dòng, chỉ INSERT, không bao giờ UPDATE / DELETE
 * (@Immutable, không có setter). Sai thì ghi giao dịch ADJUST mới. Chỉ InventoryLedger tạo dòng mới.
 * quantity luôn dương; quantityChange là thay đổi thật của tồn; balanceAfter là tồn sau giao dịch để đối soát.
 */
@Entity
@Immutable
@Table(name = "inventory_transactions")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InventoryTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "material_id", nullable = false, updatable = false)
    private Material material;

    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", nullable = false, length = 20, updatable = false)
    private InventoryTransactionType transactionType;

    @Column(name = "quantity", nullable = false, updatable = false)
    private int quantity;

    @Column(name = "quantity_change", nullable = false, updatable = false)
    private int quantityChange;

    @Column(name = "balance_after", nullable = false, updatable = false)
    private int balanceAfter;

    // Giá nhập, dùng cho IMPORT
    @Column(name = "unit_cost", precision = 12, scale = 2, updatable = false)
    private BigDecimal unitCost;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "booking_id", updatable = false)
    private Booking booking;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "schedule_id", updatable = false)
    private MovingSchedule schedule;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "allocation_id", updatable = false)
    private MaterialAllocation allocation;

    @Column(name = "note", columnDefinition = "text", updatable = false)
    private String note;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", updatable = false)
    private User createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
    }
}
