package com.example.house_moving_service.dto.request.pricing;

import com.example.house_moving_service.common.enums.PropertyType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
public class LocationDto {

    @NotBlank(message = "Địa chỉ không được để trống")
    private String address;

    @NotNull(message = "Tọa độ vĩ độ (lat) không được để trống")
    private BigDecimal lat;

    @NotNull(message = "Tọa độ kinh độ (lng) không được để trống")
    private BigDecimal lng;

    private PropertyType propertyType;

    @Min(value = 0, message = "Số tầng không được âm")
    @Builder.Default
    private Integer floorNumber = 0;

    @NotNull(message = "Cờ xác định thang máy không được để trống")
    private Boolean hasElevator;

    @Min(value = 0, message = "Cự ly vác hẻm không được âm")
    @Builder.Default
    private Integer walkingDistanceMeters = 0;
}

