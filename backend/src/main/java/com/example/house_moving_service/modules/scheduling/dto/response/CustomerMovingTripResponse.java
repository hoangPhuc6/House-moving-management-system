package com.example.house_moving_service.modules.scheduling.dto.response;

import com.example.house_moving_service.modules.scheduling.enums.MovingScheduleStatus;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Một chuyến trong lịch sử vận chuyển phía khách: không có ghi chú tự do, không id nhân viên nội bộ.
 * timeline: START → các mốc → COMPLETE, chỉ tên mốc và thời điểm.
 */
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CustomerMovingTripResponse(
        Integer id,
        MovingScheduleStatus status,
        LocalDateTime scheduledStartAt,
        LocalDateTime scheduledEndAt,
        LocalDateTime actualStartAt,
        LocalDateTime actualEndAt,
        Long delayMinutes,
        List<MovingTimelineEntry> timeline,
        List<String> licensePlates,
        SupervisorContact supervisor
) {
}
