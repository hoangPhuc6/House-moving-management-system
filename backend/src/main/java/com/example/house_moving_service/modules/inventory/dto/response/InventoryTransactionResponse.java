package com.example.house_moving_service.modules.inventory.dto.response;

import com.example.house_moving_service.modules.inventory.enums.InventoryTransactionType;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Builder
public record InventoryTransactionResponse(
        Integer id,
        Integer materialId,
        String materialCode,
        String materialName,
        InventoryTransactionType type,
        Integer quantity,
        // Thay đổi thật của tồn (+ / − / 0)
        Integer quantityChange,
        // Tồn sau giao dịch
        Integer balanceAfter,
        BigDecimal unitCost,
        Integer bookingId,
        String bookingCode,
        Integer scheduleId,
        Integer allocationId,
        String note,
        // Tên người thao tác
        String createdBy,
        LocalDateTime createdAt
) {
}
