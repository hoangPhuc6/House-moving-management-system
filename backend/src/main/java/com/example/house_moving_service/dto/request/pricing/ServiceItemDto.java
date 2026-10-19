package com.example.house_moving_service.dto.request.pricing;

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
public class ServiceItemDto {

    @NotNull(message = "serviceId không được để trống")
    private Integer serviceId;

    @NotNull(message = "Số lượng không được để trống")
    @DecimalMin(value = "0.1", message = "Số lượng phải lớn hơn 0")
    private BigDecimal quantity;
}

