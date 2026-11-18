package com.example.house_moving_service.modules.inventory.entity;

import com.example.house_moving_service.modules.inventory.enums.MaterialCategory;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
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

/**
 * Danh mục vật tư kèm tồn kho hiện tại (một kho). quantityOnHand chỉ được đổi qua InventoryLedger, luôn đi cùng một
 * dòng inventory_transactions; không nơi nào khác gọi setQuantityOnHand.
 */
@Entity
@Table(name = "materials")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Material {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "code", nullable = false, unique = true, length = 50)
    private String code;

    @Column(name = "name", nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 20)
    private MaterialCategory category;

    // cái, cuộn, mét, bộ...
    @Column(name = "unit", nullable = false, length = 30)
    private String unit;

    // Giá tính khách khi dùng vật tư tiêu hao (0 = miễn phí); TOOL nên để 0
    @Builder.Default
    @Column(name = "unit_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal unitPrice = BigDecimal.ZERO;

    @Column(name = "quantity_on_hand", nullable = false)
    private int quantityOnHand;

    // Dưới mức này thì cảnh báo tồn thấp
    @Column(name = "min_stock", nullable = false)
    private int minStock;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @Builder.Default
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        if (this.unitPrice == null) {
            this.unitPrice = BigDecimal.ZERO;
        }
        if (this.isActive == null) {
            this.isActive = true;
        }
    }

    /** Tồn dưới mức tối thiểu. */
    public boolean isLowStock() {
        return this.quantityOnHand < this.minStock;
    }
}
