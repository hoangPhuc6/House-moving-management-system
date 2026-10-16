package com.example.house_moving_service.dto.request.pricing;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PricingCalculationRequest {

    @NotNull(message = "Thông tin lịch trình không được để trống")
    @Valid
    private ScheduleDto schedule;

    @NotNull(message = "Thông tin lộ trình không được để trống")
    @Valid
    private RouteDto route;

    @NotEmpty(message = "Danh sách dịch vụ không được để trống")
    @Valid
    private List<ServiceItemDto> serviceItems;

    private String voucherCode;
}

