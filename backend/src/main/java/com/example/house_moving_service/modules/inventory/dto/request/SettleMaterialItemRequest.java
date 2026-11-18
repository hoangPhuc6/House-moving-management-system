package com.example.house_moving_service.modules.inventory.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Số chốt của một dòng cấp phát: used + returned + damaged phải bằng quantityAllocated; TOOL thì used = 0. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SettleMaterialItemRequest {

    @NotNull(message = "Dòng cấp phát không được để trống")
    private Integer allocationId;

    // Đã dùng hết (tính tiền khách)
    @NotNull(message = "Số đã dùng không được để trống")
    @Min(value = 0, message = "Số đã dùng không được âm")
    private Integer quantityUsed;

    // Hoàn về kho (cộng lại tồn)
    @NotNull(message = "Số hoàn kho không được để trống")
    @Min(value = 0, message = "Số hoàn kho không được âm")
    private Integer quantityReturned;

    // Hư / mất trong chuyến (đã trừ tồn lúc xuất, không trừ thêm)
    @NotNull(message = "Số hư / mất không được để trống")
    @Min(value = 0, message = "Số hư / mất không được âm")
    private Integer quantityDamaged;
}
