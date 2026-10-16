package com.example.house_moving_service.controller;

import com.example.house_moving_service.dto.request.CreateServiceTypeRequest;
import com.example.house_moving_service.dto.request.UpdateServiceTypeRequest;
import com.example.house_moving_service.dto.response.ApiResponse;
import com.example.house_moving_service.dto.response.ServiceTypeResponse;
import com.example.house_moving_service.service.ServiceTypeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/service-types")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
@Tag(name = "Admin - Service Types", description = "APIs quản lý nhóm loại dịch vụ dành cho Quản trị viên")
public class AdminServiceTypeController {

    private final ServiceTypeService serviceTypeService;

    @PostMapping
    @Operation(summary = "Thêm mới một loại dịch vụ")
    public ResponseEntity<ApiResponse<ServiceTypeResponse>> create(
            @Valid @RequestBody CreateServiceTypeRequest request) {
        ServiceTypeResponse response = serviceTypeService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Tạo loại dịch vụ thành công", response));
    }

    @GetMapping
    @Operation(summary = "Xem toàn bộ loại dịch vụ (cả bật lẫn tắt)")
    public ResponseEntity<ApiResponse<List<ServiceTypeResponse>>> getAll() {
        List<ServiceTypeResponse> response = serviceTypeService.getAll();
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Xem chi tiết một loại dịch vụ theo ID")
    public ResponseEntity<ApiResponse<ServiceTypeResponse>> getById(@PathVariable Integer id) {
        ServiceTypeResponse response = serviceTypeService.getById(id);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Cập nhật thông tin loại dịch vụ")
    public ResponseEntity<ApiResponse<ServiceTypeResponse>> update(
            @PathVariable Integer id,
            @Valid @RequestBody UpdateServiceTypeRequest request) {
        ServiceTypeResponse response = serviceTypeService.update(id, request);
        return ResponseEntity.ok(ApiResponse.success("Cập nhật loại dịch vụ thành công", response));
    }

    @PatchMapping("/{id}/toggle-active")
    @Operation(summary = "Đổi trạng thái bật/tắt của loại dịch vụ")
    public ResponseEntity<ApiResponse<ServiceTypeResponse>> toggleActive(@PathVariable Integer id) {
        ServiceTypeResponse response = serviceTypeService.toggleActive(id);
        return ResponseEntity.ok(ApiResponse.success("Đổi trạng thái loại dịch vụ thành công", response));
    }
}

