package com.example.house_moving_service.modules.quotation.entity;

import com.example.house_moving_service.modules.booking.entity.BookingItem;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

import java.math.BigDecimal;

@Entity
@Table(name = "quotation_items")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QuotationItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "quotation_id", nullable = false)
    private Quotation quotation;

    // Món đồ khách đã khai; null nếu nhân viên khảo sát thêm mới
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "booking_item_id")
    private BookingItem bookingItem;

    @Column(name = "item_name", nullable = false, length = 255)
    private String itemName;

    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    @Column(name = "unit", length = 50)
    private String unit;

    @Column(name = "unit_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal unitPrice;

    @Column(name = "line_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal lineAmount;

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

    @PrePersist
    public void prePersist() {
        if (this.unitPrice == null) {
            this.unitPrice = BigDecimal.ZERO;
        }
        if (this.lineAmount == null) {
            this.lineAmount = BigDecimal.ZERO;
        }
        if (this.isFragile == null) {
            this.isFragile = false;
        }
        if (this.packingRequired == null) {
            this.packingRequired = false;
        }
        if (this.disassemblyRequired == null) {
            this.disassemblyRequired = false;
        }
    }
}
