package com.example.house_moving_service.modules.scheduling.dto.response;

import com.example.house_moving_service.modules.scheduling.enums.MovingScheduleStatus;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.List;

/** Một chuyến trong lịch sử vận chuyển phía điều phối: đầy đủ nhật ký gốc, mọi nhân viên và xe (cả dòng đã hủy). */
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ManagementMovingTripResponse(
        Integer id,
        MovingScheduleStatus status,
        LocalDateTime scheduledStartAt,
        LocalDateTime scheduledEndAt,
        LocalDateTime actualStartAt,
        LocalDateTime actualEndAt,
        Long delayMinutes,
        String note,
        List<MovingLogEntry> logs,
        List<MovingStaffResponse> staff,
        List<MovingVehicleResponse> vehicles,
        Long createdBy,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
