package com.example.house_moving_service.modules.scheduling.entity;

import com.example.house_moving_service.modules.booking.entity.Booking;
import com.example.house_moving_service.modules.catalog.entity.Service;
import com.example.house_moving_service.modules.identity.entity.User;
import com.example.house_moving_service.modules.scheduling.enums.SurchargeStatus;
import com.example.house_moving_service.modules.scheduling.enums.SurchargeType;
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
 * Phụ phí phát sinh / báo giá điều chỉnh của đơn (bảng ngoài ERD). amount = quantity × unitPrice; unitPrice của loại
 * có trong danh mục được chụp lại từ services.base_price lúc tạo. Khách xác nhận thì amount được cộng vào
 * bookings.total_amount; người vận chuyển thu tiền thì tạo phiếu thu CASH (paymentId).
 */
@Entity
@Table(name = "booking_surcharges")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BookingSurcharge {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "booking_id", nullable = false)
    private Booking booking;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "schedule_id")
    private MovingSchedule schedule;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "incident_id")
    private Incident incident;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "service_id")
    private Service service;

    @Enumerated(EnumType.STRING)
    @Column(name = "surcharge_type", nullable = false, length = 30)
    private SurchargeType surchargeType;

    @Column(name = "description", nullable = false, columnDefinition = "text")
    private String description;

    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    @Column(name = "unit_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal unitPrice;

    @Column(name = "amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private SurchargeStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requested_by")
    private User requestedBy;

    @Column(name = "requested_at", nullable = false, updatable = false)
    private LocalDateTime requestedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by")
    private User reviewedBy;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Column(name = "review_note", columnDefinition = "text")
    private String reviewNote;

    @Column(name = "sent_to_customer_at")
    private LocalDateTime sentToCustomerAt;

    @Column(name = "customer_responded_at")
    private LocalDateTime customerRespondedAt;

    @Column(name = "customer_note", columnDefinition = "text")
    private String customerNote;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "collected_by")
    private User collectedBy;

    @Column(name = "collected_at")
    private LocalDateTime collectedAt;

    // Phiếu thu tiền mặt (payments.id) tạo khi người vận chuyển thu tiền; giữ id để không phụ thuộc entity của M9
    @Column(name = "payment_id")
    private Integer paymentId;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        if (this.status == null) {
            this.status = SurchargeStatus.PROPOSED;
        }
        if (this.requestedAt == null) {
            this.requestedAt = LocalDateTime.now();
        }
    }

    public boolean isCollected() {
        return this.collectedAt != null;
    }
}
