package com.example.house_moving_service.modules.inventory.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/** Một món trong phiếu nhập kho. quantity ≤ 0 do service báo ERR_INVENTORY_INVALID_QUANTITY. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ImportInventoryItemRequest {

    @NotNull(message = "Vật tư không được để trống")
    private Integer materialId;

    @NotNull(message = "Số lượng không được để trống")
    private Integer quantity;

    // Giá nhập một đơn vị, lưu vào inventory_transactions.unit_cost
    @NotNull(message = "Giá nhập không được để trống")
    @DecimalMin(value = "0", message = "Giá nhập không được âm")
    @Digits(integer = 10, fraction = 2, message = "Giá nhập không hợp lệ")
    private BigDecimal unitCost;
}
