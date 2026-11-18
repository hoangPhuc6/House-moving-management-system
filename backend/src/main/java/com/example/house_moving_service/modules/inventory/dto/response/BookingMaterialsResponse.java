package com.example.house_moving_service.modules.inventory.dto.response;

import lombok.Builder;

import java.math.BigDecimal;
import java.util.List;

/** Vật tư của một đơn, gom theo chuyến (chuyến có ít nhất một dòng cấp phát), kèm tổng tiền đã tính của cả đơn. */
@Builder
public record BookingMaterialsResponse(
        Integer bookingId,
        String bookingCode,
        // Theo scheduleId tăng dần
        List<ScheduleMaterialsResponse> schedules,
        // Σ chargedTotal của các chuyến
        BigDecimal chargedTotal
) {
}
