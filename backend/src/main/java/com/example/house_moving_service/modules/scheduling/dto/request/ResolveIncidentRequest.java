package com.example.house_moving_service.modules.scheduling.dto.request;

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

/** Giải quyết sự cố: kết quả xử lý và chi phí thiệt hại thực tế công ty chịu (có thể 0). */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ResolveIncidentRequest {

    @NotBlank(message = "Kết quả xử lý không được để trống")
    @Size(max = 5000, message = "Kết quả xử lý tối đa 5000 ký tự")
    private String resolutionNote;

    @NotNull(message = "Chi phí thiệt hại không được để trống")
    @DecimalMin(value = "0", message = "Chi phí thiệt hại không được âm")
    @DecimalMax(value = "9999999999.99", message = "Chi phí thiệt hại quá lớn")
    @Digits(integer = 10, fraction = 2, message = "Chi phí thiệt hại tối đa 2 chữ số thập phân")
    private BigDecimal damageCost;
}
