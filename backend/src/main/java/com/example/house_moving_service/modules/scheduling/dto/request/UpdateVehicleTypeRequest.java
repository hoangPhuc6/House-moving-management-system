package com.example.house_moving_service.modules.scheduling.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/** Admin cập nhật loại xe: ghi đè toàn bộ thông tin (PUT). */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateVehicleTypeRequest {

    @NotBlank(message = "Tên loại xe không được để trống")
    @Size(max = 100, message = "Tên loại xe tối đa 100 ký tự")
    private String name;

    @Size(max = 5000, message = "Mô tả tối đa 5000 ký tự")
    private String description;

    @DecimalMin(value = "0.01", message = "Tải trọng tối đa phải lớn hơn 0")
    @Digits(integer = 8, fraction = 2, message = "Tải trọng tối đa không hợp lệ (tối đa 8 chữ số phần nguyên, 2 chữ số thập phân)")
    private BigDecimal maxLoadKg;

    @DecimalMin(value = "0.01", message = "Thể tích tối đa phải lớn hơn 0")
    @Digits(integer = 8, fraction = 2, message = "Thể tích tối đa không hợp lệ (tối đa 8 chữ số phần nguyên, 2 chữ số thập phân)")
    private BigDecimal maxVolumeM3;

    // Không truyền thì giữ nguyên trạng thái hiện tại
    private Boolean isActive;
}
