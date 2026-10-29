package com.example.house_moving_service.modules.booking.dto.response;

import com.example.house_moving_service.modules.booking.enums.BookingStatus;
import lombok.Builder;

import java.time.LocalDateTime;

/* Bản sao chỉ-đọc thông tin chung của đơn hàng, trả cho module khác qua BookingWorkflowService. */
@Builder
public record BookingSnapshot(
        Integer id,
        String bookingCode,
        BookingStatus status,
        Long customerUserId,
        String customerName,
        String contactName,
        String contactPhone,
        LocalDateTime desiredStartAt,
        String note
) {
}
