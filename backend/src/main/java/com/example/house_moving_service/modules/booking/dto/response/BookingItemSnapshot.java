package com.example.house_moving_service.modules.booking.dto.response;

import lombok.Builder;

import java.math.BigDecimal;

/** Bản sao chỉ-đọc một món đồ khách đã khai báo, trả cho module khác qua BookingWorkflowService. */
@Builder
public record BookingItemSnapshot(
        Integer id,
        String itemName,
        Integer quantity,
        BigDecimal estimatedVolumeM3,
        Boolean isFragile,
        Boolean packingRequired,
        Boolean disassemblyRequired,
        String note
) {
}
