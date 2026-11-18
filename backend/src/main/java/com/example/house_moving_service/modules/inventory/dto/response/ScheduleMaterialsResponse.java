package com.example.house_moving_service.modules.inventory.dto.response;

import com.example.house_moving_service.modules.scheduling.enums.MovingScheduleStatus;
import lombok.Builder;

import java.math.BigDecimal;
import java.util.List;

@Builder
public record ScheduleMaterialsResponse(
        Integer scheduleId,
        Integer bookingId,
        MovingScheduleStatus scheduleStatus,
        // Mọi dòng cấp phát của chuyến (mọi trạng thái), theo id tăng dần
        List<MaterialAllocationResponse> allocations,
        // Σ quantityAllocated × unitPrice của CONSUMABLE đang ALLOCATED
        BigDecimal estimatedTotal,
        // Σ chargedAmount đã cộng vào đơn
        BigDecimal chargedTotal
) {
}
