package com.example.house_moving_service.modules.scheduling.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

/**
 * Tra xe / nhân viên rảnh trong khung giờ [start, end) (query param, ISO, vd 2026-11-20T08:00:00).
 * start / end bắt buộc (kiểm tra ở service). excludeScheduleId: bỏ qua chuyến đang sửa để xe / người của chính
 * chuyến đó không bị tính là bận. vehicleTypeId chỉ dùng khi tra xe.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AvailabilityQuery {

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime start;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime end;

    private Integer excludeScheduleId;

    private Integer vehicleTypeId;
}
