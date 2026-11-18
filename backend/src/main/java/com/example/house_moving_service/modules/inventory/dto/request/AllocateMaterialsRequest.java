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

/** Xuất vật tư cho chuyến: mỗi món một EXPORT; một món thiếu tồn thì không xuất gì. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AllocateMaterialsRequest {

    // Không được trùng materialId trong cùng request
    @NotEmpty(message = "Danh sách vật tư xuất không được để trống")
    private List<@Valid AllocateMaterialItemRequest> items;

    // Ghi vào giao dịch EXPORT; dòng cấp phát mới cũng lưu ghi chú này
    @Size(max = 5000, message = "Ghi chú tối đa 5000 ký tự")
    private String note;
}
