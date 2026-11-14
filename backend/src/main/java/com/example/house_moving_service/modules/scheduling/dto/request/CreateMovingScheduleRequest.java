package com.example.house_moving_service.modules.scheduling.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Điều phối tạo một chuyến vận chuyển cho đơn. Giờ dạng ISO, vd "2026-11-20T08:00:00".
 * Quy tắc thời gian (ở tương lai, kết thúc sau bắt đầu, tối đa app.moving.max-schedule-hours) kiểm tra ở service.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateMovingScheduleRequest {

    @NotNull(message = "Giờ bắt đầu không được để trống")
    private LocalDateTime scheduledStartAt;

    @NotNull(message = "Giờ kết thúc không được để trống")
    private LocalDateTime scheduledEndAt;

    @Size(max = 5000, message = "Ghi chú tối đa 5000 ký tự")
    private String note;
}
