package com.example.house_moving_service.modules.inventory.dto.response;

import com.example.house_moving_service.modules.inventory.enums.MaterialCategory;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Builder
public record MaterialResponse(
        Integer id,
        String code,
        String name,
        MaterialCategory category,
        String unit,
        BigDecimal unitPrice,
        Integer quantityOnHand,
        Integer minStock,
        // quantityOnHand < minStock
        boolean lowStock,
        String description,
        Boolean isActive,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
