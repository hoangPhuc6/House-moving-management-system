package com.example.house_moving_service.modules.booking.entity;

import com.example.house_moving_service.modules.booking.enums.BookingStatus;
import com.example.house_moving_service.modules.identity.entity.Customer;
import com.example.house_moving_service.modules.identity.entity.User;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "bookings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "booking_code", nullable = false, unique = true, length = 50)
    private String bookingCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private User createdBy;

    @Column(name = "total_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "discount_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal discountAmount;

    @Column(name = "note", columnDefinition = "text")
    private String note;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    private BookingStatus status;

    @Column(name = "desired_start_at")
    private LocalDateTime desiredStartAt;

    @Column(name = "desired_end_at")
    private LocalDateTime desiredEndAt;

    @Column(name = "contact_name", length = 255)
    private String contactName;

    @Column(name = "contact_phone", length = 20)
    private String contactPhone;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @OneToOne(mappedBy = "booking", fetch = FetchType.LAZY)
    private BookingRoute route;

    @OneToMany(mappedBy = "booking", fetch = FetchType.LAZY)
    private List<BookingItem> items;

    @OneToMany(mappedBy = "booking", fetch = FetchType.LAZY)
    private List<BookingService> services;

    @OneToMany(mappedBy = "booking", fetch = FetchType.LAZY)
    private List<BookingVoucher> vouchers;

    @OneToMany(mappedBy = "booking", fetch = FetchType.LAZY)
    private List<BookingStatusHistory> statusHistories;

    @jakarta.persistence.PrePersist
    public void prePersist() {
        if (this.discountAmount == null) {
            this.discountAmount = BigDecimal.ZERO;
        }
        if (this.status == null) {
            this.status = BookingStatus.PENDING_SURVEY;
        }
    }
}

