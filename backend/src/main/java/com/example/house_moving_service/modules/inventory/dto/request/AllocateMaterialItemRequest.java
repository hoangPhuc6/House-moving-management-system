package com.example.house_moving_service.modules.inventory.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Một món xuất cho chuyến. quantity ≤ 0 do service báo ERR_INVENTORY_INVALID_QUANTITY. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AllocateMaterialItemRequest {

    @NotNull(message = "Vật tư không được để trống")
    private Integer materialId;

    @NotNull(message = "Số lượng không được để trống")
    private Integer quantity;
}
