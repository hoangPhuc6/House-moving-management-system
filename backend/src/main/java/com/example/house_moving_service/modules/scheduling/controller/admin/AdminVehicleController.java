package com.example.house_moving_service.modules.scheduling.controller.admin;

import com.example.house_moving_service.common.dto.ApiResponse;
import com.example.house_moving_service.common.dto.PageResponse;
import com.example.house_moving_service.modules.scheduling.dto.request.CreateVehicleRequest;
import com.example.house_moving_service.modules.scheduling.dto.request.UpdateVehicleRequest;
import com.example.house_moving_service.modules.scheduling.dto.request.UpdateVehicleStatusRequest;
import com.example.house_moving_service.modules.scheduling.dto.request.VehicleFilterRequest;
import com.example.house_moving_service.modules.scheduling.dto.response.VehicleResponse;
import com.example.house_moving_service.modules.scheduling.service.VehicleService;
import com.example.house_moving_service.security.access.AdminOnly;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
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
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/vehicles")
@AdminOnly
@RequiredArgsConstructor
@Tag(name = "Admin - Vehicles", description = "Quản lý đội xe dành cho Quản trị viên")
@SecurityRequirement(name = "bearerAuth")
public class AdminVehicleController {

    private final VehicleService vehicleService;

    @PostMapping
    @Operation(summary = "Thêm xe", description = "Biển số được chuẩn hóa viết hoa. Không truyền capacityM3 thì lấy theo loại xe; "
            + "không truyền status thì AVAILABLE.")
    public ResponseEntity<ApiResponse<VehicleResponse>> create(@Valid @RequestBody CreateVehicleRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Thêm xe thành công", vehicleService.create(request)));
    }

    @GetMapping
    @Operation(summary = "Danh sách xe (phân trang)", description = "Lọc theo keyword (một phần biển số), vehicleTypeId, status. "
            + "Mặc định mới nhất trước; page bắt đầu từ 0.")
    public ResponseEntity<ApiResponse<PageResponse<VehicleResponse>>> search(
            @ParameterObject VehicleFilterRequest filter,
            @ParameterObject @PageableDefault(size = 10, sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(vehicleService.search(filter, pageable)));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Chi tiết xe")
    public ResponseEntity<ApiResponse<VehicleResponse>> getById(@PathVariable Integer id) {
        return ResponseEntity.ok(ApiResponse.success(vehicleService.getById(id)));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Cập nhật thông tin xe", description = "Biển số, loại xe, thể tích thùng, ghi chú. Trạng thái đổi qua PATCH /{id}/status.")
    public ResponseEntity<ApiResponse<VehicleResponse>> update(
            @PathVariable Integer id,
            @Valid @RequestBody UpdateVehicleRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Cập nhật xe thành công", vehicleService.update(id, request)));
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Đổi trạng thái xe", description = "AVAILABLE / MAINTENANCE / INACTIVE. Không đặt tay IN_USE; "
            + "không đổi khi xe đang chạy chuyến; không chuyển MAINTENANCE/INACTIVE khi xe còn chuyến chưa hoàn thành.")
    public ResponseEntity<ApiResponse<VehicleResponse>> updateStatus(
            @PathVariable Integer id,
            @Valid @RequestBody UpdateVehicleStatusRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Đổi trạng thái xe thành công", vehicleService.updateStatus(id, request)));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Xóa xe (xóa mềm)", description = "Chuyển xe sang INACTIVE để giữ lịch sử chuyến; cùng điều kiện như đổi sang INACTIVE.")
    public ResponseEntity<ApiResponse<Void>> softDelete(@PathVariable Integer id) {
        vehicleService.softDelete(id);
        return ResponseEntity.ok(ApiResponse.success("Xóa xe thành công (đã chuyển sang INACTIVE)"));
    }
}
