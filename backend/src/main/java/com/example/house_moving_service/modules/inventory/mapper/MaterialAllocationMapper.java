package com.example.house_moving_service.modules.inventory.mapper;

import com.example.house_moving_service.modules.inventory.dto.response.CustomerMaterialChargeResponse;
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
        return toResponse(a, true);
    }

    // withPrices = false: bản cho MOVER, unitPrice / chargedAmount = null
    private MaterialAllocationResponse toResponse(MaterialAllocation a, boolean withPrices) {
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
                .unitPrice(withPrices ? a.getUnitPrice() : null)
                .chargedAmount(withPrices ? a.getChargedAmount() : null)
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

    /** Bản cho MOVER: cùng dạng ScheduleMaterialsResponse nhưng bỏ hết tiền (unitPrice, chargedAmount, các tổng = null). */
    public ScheduleMaterialsResponse toFieldScheduleResponse(MovingScheduleSnapshot schedule,
                                                             List<MaterialAllocation> allocations) {
        return ScheduleMaterialsResponse.builder()
                .scheduleId(schedule.id())
                .bookingId(schedule.bookingId())
                .scheduleStatus(schedule.status())
                .allocations(allocations.stream()
                        .map(a -> toResponse(a, false))
                        .toList())
                .build();
    }

    public CustomerMaterialChargeResponse toCustomerCharge(MaterialAllocation a) {
        return CustomerMaterialChargeResponse.builder()
                .materialName(a.getMaterial().getName())
                .unit(a.getMaterial().getUnit())
                .quantityUsed(a.getQuantityUsed())
                .unitPrice(a.getUnitPrice())
                .chargedAmount(a.getChargedAmount())
                .settledAt(a.getSettledAt())
                .build();
    }
}
