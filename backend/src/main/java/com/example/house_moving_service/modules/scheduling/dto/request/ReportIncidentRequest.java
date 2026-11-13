package com.example.house_moving_service.modules.scheduling.dto.request;

import com.example.house_moving_service.modules.scheduling.enums.IncidentType;
import jakarta.validation.constraints.DecimalMax;
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

/**
 * Báo / ghi nhận sự cố. scheduleId chỉ dùng ở API điều phối (không bắt buộc); API hiện trường lấy chuyến theo đường dẫn
 * và bỏ qua field này.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReportIncidentRequest {

    private Integer scheduleId;

    @NotNull(message = "Loại sự cố không được để trống")
    private IncidentType incidentType;

    @NotBlank(message = "Mô tả sự cố không được để trống")
    @Size(max = 5000, message = "Mô tả tối đa 5000 ký tự")
    private String description;

    @DecimalMin(value = "0", message = "Chi phí thiệt hại ước tính không được âm")
    @DecimalMax(value = "9999999999.99", message = "Chi phí thiệt hại ước tính quá lớn")
    @Digits(integer = 10, fraction = 2, message = "Chi phí thiệt hại tối đa 2 chữ số thập phân")
    private BigDecimal estimatedDamageCost;
}
