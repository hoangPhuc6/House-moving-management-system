package com.example.house_moving_service.modules.inventory.dto.response;

import com.example.house_moving_service.modules.inventory.enums.AllocationStatus;
import com.example.house_moving_service.modules.inventory.enums.MaterialCategory;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Builder
public record MaterialAllocationResponse(
        Integer id,
        Integer materialId,
        String materialCode,
        String materialName,
        MaterialCategory category,
        String unit,
        Integer quantityAllocated,
        Integer quantityUsed,
        Integer quantityReturned,
        Integer quantityDamaged,
        AllocationStatus status,
        // Giá chụp lúc cấp phát
        BigDecimal unitPrice,
        // = quantityUsed × unitPrice, có sau khi chốt
        BigDecimal chargedAmount,
        // Tên người cấp / người chốt
        String allocatedBy,
        LocalDateTime allocatedAt,
        String settledBy,
        LocalDateTime settledAt,
        String note
) {
}
