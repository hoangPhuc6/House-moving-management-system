package com.example.house_moving_service.modules.inventory.dto.request;

import com.example.house_moving_service.modules.inventory.enums.MaterialCategory;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Bộ lọc danh sách vật tư (query param), mọi field đều không bắt buộc. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MaterialFilterRequest {

    private MaterialCategory category;

    private Boolean isActive;

    // Tìm theo một phần mã hoặc tên, không phân biệt hoa thường
    private String keyword;

    // true: tồn < tồn tối thiểu; false: tồn ≥ tồn tối thiểu
    private Boolean lowStock;
}
