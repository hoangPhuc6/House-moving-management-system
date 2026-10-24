package com.example.house_moving_service.modules.booking.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "booking_items")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BookingItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "booking_id", nullable = false)
    private Booking booking;

    @Column(name = "item_name", nullable = false, length = 255)
    private String itemName;

    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    @Column(name = "estimated_volume_m3", precision = 10, scale = 2)
    private BigDecimal estimatedVolumeM3;

    @Column(name = "is_fragile", nullable = false)
    private Boolean isFragile;

    @Column(name = "packing_required", nullable = false)
    private Boolean packingRequired;

    @Column(name = "disassembly_required", nullable = false)
    private Boolean disassemblyRequired;

    @Column(name = "note", columnDefinition = "text")
    private String note;

    @jakarta.persistence.PrePersist
    public void prePersist() {
        if (this.quantity == null) this.quantity = 1;
        if (this.isFragile == null) this.isFragile = false;
        if (this.packingRequired == null) this.packingRequired = false;
        if (this.disassemblyRequired == null) this.disassemblyRequired = false;
    }
}

