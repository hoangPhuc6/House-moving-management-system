package com.example.house_moving_service.modules.catalog.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateServiceRequest {

    @NotNull(message = "Mã loại dịch vụ (serviceTypeId) không được để trống")
    private Integer serviceTypeId;

    @NotBlank(message = "Tên dịch vụ không được để trống")
    @Size(max = 255, message = "Tên dịch vụ không được vượt quá 255 ký tự")
    private String name;

    private String description;

    @NotNull(message = "Đơn giá cơ sở không được để trống")
    @DecimalMin(value = "0.0", inclusive = false, message = "Đơn giá cơ sở phải lớn hơn 0")
    @Digits(integer = 10, fraction = 2, message = "Đơn giá cơ sở không hợp lệ (tối đa 2 chữ số thập phân)")
    private BigDecimal basePrice;
}

