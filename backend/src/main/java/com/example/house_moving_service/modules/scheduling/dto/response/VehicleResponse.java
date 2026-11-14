package com.example.house_moving_service.modules.scheduling.dto.response;

import com.example.house_moving_service.modules.scheduling.enums.VehicleStatus;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Builder
public record VehicleResponse(
        Integer id,
        String licensePlate,
        Integer vehicleTypeId,
        String vehicleTypeName,
        Boolean vehicleTypeActive,
        BigDecimal maxLoadKg,
        BigDecimal capacityM3,
        VehicleStatus status,
        String note,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
