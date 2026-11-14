package com.example.house_moving_service.modules.scheduling.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Hủy chuyến chưa bắt đầu: bắt buộc lý do. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CancelMovingScheduleRequest {

    @NotBlank(message = "Lý do hủy chuyến không được để trống")
    @Size(max = 1000, message = "Lý do hủy chuyến tối đa 1000 ký tự")
    private String reason;
}
