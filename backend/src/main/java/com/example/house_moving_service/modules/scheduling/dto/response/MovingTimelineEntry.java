package com.example.house_moving_service.modules.scheduling.dto.response;

import java.time.LocalDateTime;

/** Mốc trên dòng thời gian phía khách: chỉ tên mốc (START, ARRIVED_PICKUP..., COMPLETE) và thời điểm. */
public record MovingTimelineEntry(String milestone, LocalDateTime at) {
}
