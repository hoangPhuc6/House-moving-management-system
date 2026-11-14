package com.example.house_moving_service.modules.scheduling.dto.request;

import com.example.house_moving_service.modules.scheduling.enums.VehicleStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Đổi trạng thái xe: AVAILABLE / MAINTENANCE / INACTIVE. IN_USE do hệ thống tự đặt khi chuyến bắt đầu. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateVehicleStatusRequest {

    @NotNull(message = "Trạng thái xe không được để trống")
    private VehicleStatus status;

    // Ghi đè ghi chú của xe nếu có truyền (vd: lý do bảo dưỡng)
    @Size(max = 5000, message = "Ghi chú tối đa 5000 ký tự")
    private String note;
}
