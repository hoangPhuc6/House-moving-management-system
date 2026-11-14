package com.example.house_moving_service.modules.scheduling.mapper;

import com.example.house_moving_service.modules.scheduling.dto.response.VehicleResponse;
import com.example.house_moving_service.modules.scheduling.dto.response.VehicleTypeResponse;
import com.example.house_moving_service.modules.scheduling.entity.Vehicle;
import com.example.house_moving_service.modules.scheduling.entity.VehicleType;
import org.springframework.stereotype.Component;

@Component
public class VehicleMapper {

    public VehicleTypeResponse toTypeResponse(VehicleType type, long vehicleCount) {
        return VehicleTypeResponse.builder()
                .id(type.getId())
                .name(type.getName())
                .description(type.getDescription())
                .maxLoadKg(type.getMaxLoadKg())
                .maxVolumeM3(type.getMaxVolumeM3())
                .isActive(type.getIsActive())
                .vehicleCount(vehicleCount)
                .createdAt(type.getCreatedAt())
                .updatedAt(type.getUpdatedAt())
                .build();
    }

    public VehicleResponse toResponse(Vehicle vehicle) {
        VehicleType type = vehicle.getVehicleType();
        return VehicleResponse.builder()
                .id(vehicle.getId())
                .licensePlate(vehicle.getLicensePlate())
                .vehicleTypeId(type != null ? type.getId() : null)
                .vehicleTypeName(type != null ? type.getName() : null)
                .vehicleTypeActive(type != null ? type.getIsActive() : null)
                .maxLoadKg(type != null ? type.getMaxLoadKg() : null)
                .capacityM3(vehicle.getCapacityM3())
                .status(vehicle.getStatus())
                .note(vehicle.getNote())
                .createdAt(vehicle.getCreatedAt())
                .updatedAt(vehicle.getUpdatedAt())
                .build();
    }
}
