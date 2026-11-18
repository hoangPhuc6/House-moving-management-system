package com.example.house_moving_service.modules.inventory.dto.response;

import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Một dòng vật tư đã tính tiền trong đơn, dành cho khách (không có giá nhập, không có số hoàn / hư). */
@Builder
public record CustomerMaterialChargeResponse(
        String materialName,
        String unit,
        Integer quantityUsed,
        // Đơn giá chụp lúc cấp phát
        BigDecimal unitPrice,
        BigDecimal chargedAmount,
        LocalDateTime settledAt
) {
}
