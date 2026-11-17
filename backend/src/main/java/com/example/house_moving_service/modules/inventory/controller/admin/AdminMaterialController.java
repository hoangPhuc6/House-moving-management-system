package com.example.house_moving_service.modules.inventory.controller.admin;

import com.example.house_moving_service.common.dto.ApiResponse;
import com.example.house_moving_service.common.dto.PageResponse;
import com.example.house_moving_service.modules.inventory.dto.request.CreateMaterialRequest;
import com.example.house_moving_service.modules.inventory.dto.request.MaterialFilterRequest;
import com.example.house_moving_service.modules.inventory.dto.request.UpdateMaterialRequest;
import com.example.house_moving_service.modules.inventory.dto.request.UpdateMaterialStatusRequest;
import com.example.house_moving_service.modules.inventory.dto.response.MaterialResponse;
import com.example.house_moving_service.modules.inventory.service.MaterialService;
import com.example.house_moving_service.security.access.AdminOnly;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/materials")
@AdminOnly
@RequiredArgsConstructor
@Tag(name = "Admin - Materials", description = "Quản lý danh mục vật tư / dụng cụ kho dành cho Quản trị viên")
@SecurityRequirement(name = "bearerAuth")
public class AdminMaterialController {

    private final MaterialService materialService;

    @GetMapping
    @Operation(summary = "Danh sách vật tư (phân trang)",
            description = "Lọc theo category (CONSUMABLE / TOOL), isActive, keyword (một phần mã hoặc tên), "
                    + "lowStock (true: tồn < tồn tối thiểu). Không truyền sort thì sắp theo mã; page bắt đầu từ 0.")
    public ResponseEntity<ApiResponse<PageResponse<MaterialResponse>>> search(
            @ParameterObject MaterialFilterRequest filter,
            @ParameterObject @PageableDefault(size = 10) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(materialService.search(filter, pageable)));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Chi tiết vật tư", description = "Kèm tồn hiện tại và cờ lowStock.")
    public ResponseEntity<ApiResponse<MaterialResponse>> getById(@PathVariable Integer id) {
        return ResponseEntity.ok(ApiResponse.ok(materialService.getById(id)));
    }

    @PostMapping
    @Operation(summary = "Thêm vật tư",
            description = "Mã được chuẩn hóa viết hoa, không trùng (không phân biệt hoa thường). Vật tư mới: tồn = 0, "
                    + "isActive = true; muốn có hàng phải nhập kho. unitPrice / minStock không truyền thì 0. "
                    + "TOOL nên để unitPrice = 0 (không bán cho khách).")
    public ResponseEntity<ApiResponse<MaterialResponse>> create(@Valid @RequestBody CreateMaterialRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Thêm vật tư thành công", materialService.create(request)));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Cập nhật vật tư",
            description = "Sửa name, unit, unitPrice, minStock, description. Không sửa được code, category, "
                    + "quantityOnHand. Giá mới chỉ áp dụng cho lần cấp phát sau.")
    public ResponseEntity<ApiResponse<MaterialResponse>> update(
            @PathVariable Integer id,
            @Valid @RequestBody UpdateMaterialRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Cập nhật vật tư thành công", materialService.update(id, request)));
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Bật / tắt vật tư",
            description = "isActive true → false: vật tư không được nhập / xuất kho nữa (tồn giữ nguyên). "
                    + "false → true: dùng lại bình thường.")
    public ResponseEntity<ApiResponse<MaterialResponse>> updateStatus(
            @PathVariable Integer id,
            @Valid @RequestBody UpdateMaterialStatusRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Đổi trạng thái vật tư thành công",
                materialService.updateStatus(id, request)));
    }
}
