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

/** Phiếu nhập kho: mỗi món thành một giao dịch IMPORT; một món lỗi thì cả phiếu không được nhập. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ImportInventoryRequest {

    // Không được trùng materialId trong cùng phiếu
    @NotEmpty(message = "Danh sách vật tư nhập không được để trống")
    private List<@Valid ImportInventoryItemRequest> items;

    // Ghi chú chung, ghi vào từng giao dịch (vd: số phiếu, nhà cung cấp)
    @Size(max = 5000, message = "Ghi chú tối đa 5000 ký tự")
    private String note;
}
