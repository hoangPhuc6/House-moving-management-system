package com.example.house_moving_service.modules.inventory.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Xuất hủy hàng hư ngay trong kho (DAMAGED, trừ tồn). quantity ≤ 0 do service báo ERR_INVENTORY_INVALID_QUANTITY. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DamageInventoryRequest {

    @NotNull(message = "Vật tư không được để trống")
    private Integer materialId;

    @NotNull(message = "Số lượng không được để trống")
    private Integer quantity;

    @NotBlank(message = "Lý do xuất hủy không được để trống")
    @Size(max = 5000, message = "Lý do tối đa 5000 ký tự")
    private String note;
}
