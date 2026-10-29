package com.example.house_moving_service.modules.survey.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/** Một món đồ MOVER ghi khi khảo sát. Không có giá: giá do bên báo giá điền sau. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SurveyItemRequest {

    @NotBlank(message = "Tên đồ đạc không được để trống")
    @Size(max = 255, message = "Tên đồ đạc tối đa 255 ký tự")
    private String itemName;

    @NotNull(message = "Số lượng không được để trống")
    @Min(value = 1, message = "Số lượng phải từ 1 trở lên")
    private Integer quantity;

    @Size(max = 50, message = "Đơn vị tối đa 50 ký tự")
    private String unit;

    @DecimalMin(value = "0.0", message = "Thể tích ước tính không được âm")
    private BigDecimal estimatedVolumeM3;

    // null → false
    private Boolean isFragile;
    private Boolean packingRequired;
    private Boolean disassemblyRequired;

    @Size(max = 1000, message = "Ghi chú tối đa 1000 ký tự")
    private String note;
}
