package com.example.house_moving_service.modules.scheduling.dto.response;

import com.example.house_moving_service.modules.scheduling.enums.MovingScheduleStatus;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;

import java.time.LocalDateTime;

/** Một chuyến của đơn, phía khách: chỉ trạng thái và thời gian. */
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CustomerMovingScheduleResponse(
        Integer id,
        MovingScheduleStatus status,
        LocalDateTime scheduledStartAt,
        LocalDateTime scheduledEndAt,
        LocalDateTime actualStartAt,
        LocalDateTime actualEndAt,
        Long delayMinutes
) {
}
