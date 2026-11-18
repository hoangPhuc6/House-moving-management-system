package com.example.house_moving_service.modules.inventory.service;

import com.example.house_moving_service.common.dto.PageResponse;
import com.example.house_moving_service.modules.inventory.dto.request.CreateMaterialRequest;
import com.example.house_moving_service.modules.inventory.dto.request.MaterialFilterRequest;
import com.example.house_moving_service.modules.inventory.dto.request.UpdateMaterialRequest;
import com.example.house_moving_service.modules.inventory.dto.request.UpdateMaterialStatusRequest;
import com.example.house_moving_service.modules.inventory.dto.response.MaterialResponse;
import org.springframework.data.domain.Pageable;

/** Quản lý danh mục vật tư (Admin). Không đổi tồn kho: tồn chỉ đổi qua nhập / xuất / hoàn / kiểm kê. */
public interface MaterialService {

    PageResponse<MaterialResponse> search(MaterialFilterRequest filter, Pageable pageable);

    MaterialResponse getById(Integer id);

    /** Tạo vật tư với tồn = 0. Trùng code (không phân biệt hoa thường) → ERR_MATERIAL_CODE_DUPLICATE. */
    MaterialResponse create(CreateMaterialRequest request);

    /** Sửa name, unit, unitPrice, minStock, description; không đổi code, category, quantityOnHand. */
    MaterialResponse update(Integer id, UpdateMaterialRequest request);

    MaterialResponse updateStatus(Integer id, UpdateMaterialStatusRequest request);
}
