package com.example.house_moving_service.modules.scheduling.dto.request;

import com.example.house_moving_service.modules.scheduling.enums.VehicleStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Bộ lọc danh sách xe (query param), mọi field đều không bắt buộc. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VehicleFilterRequest {

    // Tìm theo một phần biển số, không phân biệt hoa thường
    private String keyword;

    private Integer vehicleTypeId;

    private VehicleStatus status;
}
