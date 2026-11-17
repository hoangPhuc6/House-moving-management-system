package com.example.house_moving_service.modules.inventory.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Bật / tắt vật tư. Vật tư đang tắt không được nhập / xuất kho. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateMaterialStatusRequest {

    @NotNull(message = "Trạng thái hoạt động không được để trống")
    private Boolean isActive;
}
