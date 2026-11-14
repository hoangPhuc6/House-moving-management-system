package com.example.house_moving_service.modules.scheduling.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/** Admin cập nhật thông tin xe (PUT). Trạng thái xe đổi qua API riêng PATCH /{id}/status. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateVehicleRequest {

    @NotBlank(message = "Biển số xe không được để trống")
    @Size(max = 30, message = "Biển số xe tối đa 30 ký tự")
    @Pattern(regexp = "^[0-9A-Za-z][0-9A-Za-z.\\- ]{3,29}$",
            message = "Biển số xe chỉ gồm chữ, số, dấu chấm, gạch ngang và khoảng trắng")
    private String licensePlate;

    @NotNull(message = "Loại xe không được để trống")
    private Integer vehicleTypeId;

    @DecimalMin(value = "0.01", message = "Thể tích thùng xe phải lớn hơn 0")
    @Digits(integer = 8, fraction = 2, message = "Thể tích thùng xe không hợp lệ")
    private BigDecimal capacityM3;

    @Size(max = 5000, message = "Ghi chú tối đa 5000 ký tự")
    private String note;
}
