package com.example.house_moving_service.modules.scheduling.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;

import java.time.LocalDateTime;

/**
 * Một dòng nhật ký chuyến (moving_schedules.note) đã tách: thời điểm, tag (START, mốc tiến độ, COMPLETE...),
 * người ghi và nội dung. Dòng không đúng định dạng: at / tag / actorUserId null, content là cả dòng.
 */
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public record MovingLogEntry(
        LocalDateTime at,
        String tag,
        Long actorUserId,
        String content
) {
}
