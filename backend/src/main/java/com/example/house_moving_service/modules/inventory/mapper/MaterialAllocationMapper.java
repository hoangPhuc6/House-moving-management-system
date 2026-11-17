package com.example.house_moving_service.modules.inventory.mapper;

import com.example.house_moving_service.modules.inventory.dto.response.MaterialAllocationResponse;
import com.example.house_moving_service.modules.inventory.dto.response.ScheduleMaterialsResponse;
import com.example.house_moving_service.modules.inventory.entity.Material;
import com.example.house_moving_service.modules.inventory.entity.MaterialAllocation;
import com.example.house_moving_service.modules.inventory.enums.AllocationStatus;
import com.example.house_moving_service.modules.inventory.enums.MaterialCategory;
import com.example.house_moving_service.modules.scheduling.dto.response.MovingScheduleSnapshot;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Component
public class MaterialAllocationMapper {

    public MaterialAllocationResponse toResponse(MaterialAllocation a) {
        Material material = a.getMaterial();
        return MaterialAllocationResponse.builder()
                .id(a.getId())
                .materialId(material.getId())
                .materialCode(material.getCode())
                .materialName(material.getName())
                .category(material.getCategory())
                .unit(material.getUnit())
                .quantityAllocated(a.getQuantityAllocated())
                .quantityUsed(a.getQuantityUsed())
                .quantityReturned(a.getQuantityReturned())
                .quantityDamaged(a.getQuantityDamaged())
                .status(a.getStatus())
                .unitPrice(a.getUnitPrice())
                .chargedAmount(a.getChargedAmount())
                .allocatedBy(UserNames.of(a.getAllocatedBy()))
                .allocatedAt(a.getAllocatedAt())
                .settledBy(UserNames.of(a.getSettledBy()))
                .settledAt(a.getSettledAt())
                .note(a.getNote())
                .build();
    }

    public ScheduleMaterialsResponse toScheduleResponse(MovingScheduleSnapshot schedule, List<MaterialAllocation> allocations) {
        BigDecimal estimated = BigDecimal.ZERO;
        BigDecimal charged = BigDecimal.ZERO;
        for (MaterialAllocation a : allocations) {
            if (a.getStatus() == AllocationStatus.ALLOCATED && a.getMaterial().getCategory() == MaterialCategory.CONSUMABLE) {
                estimated = estimated.add(a.getUnitPrice().multiply(BigDecimal.valueOf(a.getQuantityAllocated())));
            }
            if (a.getChargedAmount() != null) {
                charged = charged.add(a.getChargedAmount());
            }
        }
        return ScheduleMaterialsResponse.builder()
                .scheduleId(schedule.id())
                .bookingId(schedule.bookingId())
                .scheduleStatus(schedule.status())
                .allocations(allocations.stream().map(this::toResponse).toList())
                .estimatedTotal(estimated.setScale(2, RoundingMode.HALF_UP))
                .chargedTotal(charged.setScale(2, RoundingMode.HALF_UP))
                .build();
    }
}
