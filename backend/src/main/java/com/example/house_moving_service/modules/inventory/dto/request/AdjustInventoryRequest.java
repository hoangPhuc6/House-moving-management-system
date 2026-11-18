package com.example.house_moving_service.modules.inventory.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Kiểm kê: số đếm thực tế của một vật tư; chênh lệch so với tồn hiện tại ghi thành ADJUST. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdjustInventoryRequest {

    @NotNull(message = "Vật tư không được để trống")
    private Integer materialId;

    @NotNull(message = "Số lượng thực tế không được để trống")
    @PositiveOrZero(message = "Số lượng thực tế không được âm")
    private Integer actualQuantity;

    @NotBlank(message = "Lý do kiểm kê không được để trống")
    @Size(max = 5000, message = "Lý do tối đa 5000 ký tự")
    private String note;
}
