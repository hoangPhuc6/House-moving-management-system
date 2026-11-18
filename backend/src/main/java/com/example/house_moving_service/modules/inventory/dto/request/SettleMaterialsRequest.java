package com.example.house_moving_service.modules.inventory.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/** Chốt vật tư sau chuyến: phải có đúng và đủ mọi dòng ALLOCATED của chuyến. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SettleMaterialsRequest {

    @NotEmpty(message = "Danh sách dòng cấp phát cần chốt không được để trống")
    private List<@Valid SettleMaterialItemRequest> items;

    // Ghi vào các giao dịch RETURN / DAMAGED khi chốt
    @Size(max = 5000, message = "Ghi chú tối đa 5000 ký tự")
    private String note;
}
