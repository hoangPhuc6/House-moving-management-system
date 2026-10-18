package com.example.house_moving_service.dto.request.booking;

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

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BookingItemRequest {

    @NotBlank(message = "Tên đồ đạc không được để trống")
    @Size(max = 255, message = "Tên đồ đạc không được vượt quá 255 ký tự")
    private String itemName;

    @NotNull(message = "Số lượng đồ đạc không được để trống")
    @Min(value = 1, message = "Số lượng đồ đạc tối thiểu là 1")
    private Integer quantity;

    @DecimalMin(value = "0.0", inclusive = true, message = "Thể tích ước tính không được âm")
    private BigDecimal estimatedVolumeM3;

    @Builder.Default
    private Boolean isFragile = false;

    @Builder.Default
    private Boolean packingRequired = false;

    @Builder.Default
    private Boolean disassemblyRequired = false;

    @Size(max = 500, message = "Ghi chú đồ đạc không được vượt quá 500 ký tự")
    private String note;
}

