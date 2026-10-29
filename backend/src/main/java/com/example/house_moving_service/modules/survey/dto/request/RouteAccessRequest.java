package com.example.house_moving_service.modules.survey.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Điều kiện ra vào thực tế ở cả hai đầu, MOVER ghi khi khảo sát. Ghi đè giá trị khách khai trong booking_routes. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RouteAccessRequest {

    @NotNull(message = "Điều kiện tại điểm lấy hàng không được để trống")
    @Valid
    private LocationAccessRequest pickup;

    @NotNull(message = "Điều kiện tại điểm giao hàng không được để trống")
    @Valid
    private LocationAccessRequest dropoff;
}
