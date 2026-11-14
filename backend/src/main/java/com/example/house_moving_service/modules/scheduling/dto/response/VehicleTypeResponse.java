package com.example.house_moving_service.modules.scheduling.dto.response;

import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Builder
public record VehicleTypeResponse(
        Integer id,
        String name,
        String description,
        BigDecimal maxLoadKg,
        BigDecimal maxVolumeM3,
        Boolean isActive,
        // Số xe đang thuộc loại này (kể cả xe INACTIVE)
        long vehicleCount,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
