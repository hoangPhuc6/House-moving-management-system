package com.example.house_moving_service.modules.inventory.entity;

import com.example.house_moving_service.modules.booking.entity.Booking;
import com.example.house_moving_service.modules.identity.entity.User;
import com.example.house_moving_service.modules.inventory.enums.AllocationStatus;
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
import lombok.Setter;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Vật tư cấp cho một chuyến (bảng booking_material_allocations). Mỗi chuyến chỉ có một dòng chưa hủy cho mỗi vật tư
 * (uq_material_allocations_schedule_material); xuất thêm thì tăng quantityAllocated, giữ unitPrice cũ.
 * booking lấy từ chuyến, không nhận từ request. chargedAmount = quantityUsed × unitPrice, chỉ gán một lần khi chốt.
 */
@Entity
@Table(name = "booking_material_allocations")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MaterialAllocation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "booking_id", nullable = false)
    private Booking booking;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "schedule_id", nullable = false)
    private MovingSchedule schedule;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "material_id", nullable = false)
    private Material material;

    @Column(name = "quantity_allocated", nullable = false)
    private int quantityAllocated;

    // Vật tư tiêu hao đã dùng hết; TOOL luôn = 0 (kiểm ở service)
    @Column(name = "quantity_used", nullable = false)
    private int quantityUsed;

    // Đã hoàn về kho
    @Column(name = "quantity_returned", nullable = false)
    private int quantityReturned;

    // Hư / mất ngoài chuyến
    @Column(name = "quantity_damaged", nullable = false)
    private int quantityDamaged;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private AllocationStatus status;

    // Giá chụp lại từ materials.unit_price lúc cấp; giá vật tư đổi sau này không ảnh hưởng
    @Builder.Default
    @Column(name = "unit_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal unitPrice = BigDecimal.ZERO;

    // NULL = chưa cộng vào bookings.total_amount
    @Column(name = "charged_amount", precision = 12, scale = 2)
    private BigDecimal chargedAmount;

    @Column(name = "charged_at")
    private LocalDateTime chargedAt;

    @Column(name = "note", columnDefinition = "text")
    private String note;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "allocated_by")
    private User allocatedBy;

    @Column(name = "allocated_at", nullable = false, updatable = false)
    private LocalDateTime allocatedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "settled_by")
    private User settledBy;

    @Column(name = "settled_at")
    private LocalDateTime settledAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        if (this.status == null) {
            this.status = AllocationStatus.ALLOCATED;
        }
        if (this.unitPrice == null) {
            this.unitPrice = BigDecimal.ZERO;
        }
        if (this.allocatedAt == null) {
            this.allocatedAt = LocalDateTime.now();
        }
    }

    /** Đã cộng tiền vào đơn thì không cộng lại. */
    public boolean isCharged() {
        return this.chargedAmount != null;
    }
}
