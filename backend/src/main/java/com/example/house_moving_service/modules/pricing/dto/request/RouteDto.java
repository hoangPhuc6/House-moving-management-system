package com.example.house_moving_service.modules.pricing.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
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
public class RouteDto {

    @NotNull(message = "Thông tin điểm đi không được để trống")
    @Valid
    private LocationDto pickup;

    @NotNull(message = "Thông tin điểm đến không được để trống")
    @Valid
    private LocationDto dropoff;

    @DecimalMin(value = "0.0", message = "Khoảng cách thủ công không được âm")
    private BigDecimal manualDistanceKm;
}

