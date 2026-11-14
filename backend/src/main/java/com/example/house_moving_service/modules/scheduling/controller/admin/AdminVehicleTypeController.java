package com.example.house_moving_service.modules.scheduling.controller.admin;

import com.example.house_moving_service.common.dto.ApiResponse;
import com.example.house_moving_service.modules.scheduling.dto.request.CreateVehicleTypeRequest;
import com.example.house_moving_service.modules.scheduling.dto.request.UpdateVehicleTypeRequest;
import com.example.house_moving_service.modules.scheduling.dto.response.VehicleTypeResponse;
import com.example.house_moving_service.modules.scheduling.service.VehicleTypeService;
import com.example.house_moving_service.security.access.AdminOnly;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/vehicle-types")
@AdminOnly
@RequiredArgsConstructor
@Tag(name = "Admin - Vehicle Types", description = "Quản lý loại xe (tải trọng, thể tích) dành cho Quản trị viên")
@SecurityRequirement(name = "bearerAuth")
public class AdminVehicleTypeController {

    private final VehicleTypeService vehicleTypeService;

    @PostMapping
    @Operation(summary = "Thêm loại xe")
    public ResponseEntity<ApiResponse<VehicleTypeResponse>> create(
            @Valid @RequestBody CreateVehicleTypeRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Tạo loại xe thành công", vehicleTypeService.create(request)));
    }

    @GetMapping
    @Operation(summary = "Danh sách loại xe", description = "Không truyền isActive thì lấy cả loại đang và ngừng hoạt động. "
            + "Mỗi loại kèm vehicleCount = số xe thuộc loại đó.")
    public ResponseEntity<ApiResponse<List<VehicleTypeResponse>>> getAll(
            @Parameter(description = "Lọc theo trạng thái hoạt động") @RequestParam(required = false) Boolean isActive) {
        return ResponseEntity.ok(ApiResponse.success(vehicleTypeService.getAll(isActive)));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Chi tiết loại xe")
    public ResponseEntity<ApiResponse<VehicleTypeResponse>> getById(@PathVariable Integer id) {
        return ResponseEntity.ok(ApiResponse.success(vehicleTypeService.getById(id)));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Cập nhật loại xe")
    public ResponseEntity<ApiResponse<VehicleTypeResponse>> update(
            @PathVariable Integer id,
            @Valid @RequestBody UpdateVehicleTypeRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Cập nhật loại xe thành công", vehicleTypeService.update(id, request)));
    }

    @PatchMapping("/{id}/toggle-active")
    @Operation(summary = "Bật/tắt loại xe", description = "Loại xe ngừng hoạt động không gán được cho xe mới; xe hiện có giữ nguyên.")
    public ResponseEntity<ApiResponse<VehicleTypeResponse>> toggleActive(@PathVariable Integer id) {
        return ResponseEntity.ok(ApiResponse.success("Đổi trạng thái loại xe thành công", vehicleTypeService.toggleActive(id)));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Xóa loại xe", description = "Chỉ xóa được khi chưa có xe nào thuộc loại này (409 nếu còn xe).")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Integer id) {
        vehicleTypeService.delete(id);
        return ResponseEntity.ok(ApiResponse.success("Xóa loại xe thành công"));
    }
}
