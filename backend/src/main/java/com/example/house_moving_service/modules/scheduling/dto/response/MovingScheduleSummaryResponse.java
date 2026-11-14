package com.example.house_moving_service.modules.scheduling.dto.response;

import com.example.house_moving_service.modules.scheduling.enums.MovingScheduleStatus;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.List;

/** Một dòng trong danh sách chuyến của điều phối: chỉ tính phân công còn hiệu lực (chưa hủy). */
@Builder
public record MovingScheduleSummaryResponse(
        Integer id,
        Integer bookingId,
        String bookingCode,
        MovingScheduleStatus status,
        LocalDateTime scheduledStartAt,
        LocalDateTime scheduledEndAt,
        LocalDateTime actualStartAt,
        LocalDateTime actualEndAt,
        String supervisorName,
        int staffCount,
        List<String> licensePlates
) {
}
