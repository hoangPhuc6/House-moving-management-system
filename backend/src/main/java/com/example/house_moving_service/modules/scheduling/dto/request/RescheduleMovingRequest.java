package com.example.house_moving_service.modules.scheduling.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** Đổi giờ chuyến PLANNED / CONFIRMED. Xe và nhân viên đang phân công được kiểm tra trùng lại với giờ mới. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RescheduleMovingRequest {

    @NotNull(message = "Giờ bắt đầu mới không được để trống")
    private LocalDateTime scheduledStartAt;

    @NotNull(message = "Giờ kết thúc mới không được để trống")
    private LocalDateTime scheduledEndAt;

    @NotBlank(message = "Lý do đổi lịch không được để trống")
    @Size(max = 1000, message = "Lý do đổi lịch tối đa 1000 ký tự")
    private String reason;
}
