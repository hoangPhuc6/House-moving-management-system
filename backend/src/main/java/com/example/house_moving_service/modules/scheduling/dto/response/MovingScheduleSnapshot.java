package com.example.house_moving_service.modules.scheduling.dto.response;

import com.example.house_moving_service.modules.scheduling.enums.MovingScheduleStatus;
import lombok.Builder;

/* Bản sao chỉ-đọc thông tin chung của chuyến, trả cho module khác qua MovingScheduleLookupService. */
@Builder
public record MovingScheduleSnapshot(
        Integer id,
        Integer bookingId,
        MovingScheduleStatus status
) {
}
