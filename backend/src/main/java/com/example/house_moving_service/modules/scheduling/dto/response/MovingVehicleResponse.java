package com.example.house_moving_service.modules.scheduling.dto.response;

import com.example.house_moving_service.modules.scheduling.enums.AssignmentStatus;
import com.example.house_moving_service.modules.scheduling.enums.VehicleStatus;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;

import java.math.BigDecimal;

@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public record MovingVehicleResponse(
        Integer assignmentId,
        Integer vehicleId,
        String licensePlate,
        String vehicleTypeName,
        BigDecimal capacityM3,
        VehicleStatus vehicleStatus,
        AssignmentStatus status
) {
}
