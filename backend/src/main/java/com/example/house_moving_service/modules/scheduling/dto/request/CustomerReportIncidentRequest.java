package com.example.house_moving_service.modules.scheduling.dto.request;

import com.example.house_moving_service.modules.scheduling.enums.IncidentType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Khách tự báo sự cố của đơn mình. Không có chi phí thiệt hại: khách không tự định giá, điều phối viên ghi chi phí khi
 * giải quyết. scheduleId không bắt buộc (khách có thể không biết sự cố thuộc chuyến nào).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CustomerReportIncidentRequest {

    private Integer scheduleId;

    @NotNull(message = "Loại sự cố không được để trống")
    private IncidentType incidentType;

    @NotBlank(message = "Mô tả sự cố không được để trống")
    @Size(max = 5000, message = "Mô tả tối đa 5000 ký tự")
    private String description;
}
