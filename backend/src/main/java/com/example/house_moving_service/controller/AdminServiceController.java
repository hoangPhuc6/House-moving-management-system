package com.example.house_moving_service.controller;

import com.example.house_moving_service.dto.request.CreateServiceRequest;
import com.example.house_moving_service.dto.request.UpdateServiceRequest;
import com.example.house_moving_service.dto.request.UpdateServiceStatusRequest;
import com.example.house_moving_service.dto.response.ApiResponse;
import com.example.house_moving_service.dto.response.ServiceResponse;
import com.example.house_moving_service.enums.ServiceStatus;
import com.example.house_moving_service.service.ServiceCatalogService;
import com.example.house_moving_service.security.access.AdminOnly;
import io.swagger.v3.oas.annotations.Operation;
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
@RequestMapping("/api/v1/admin/services")
@AdminOnly
@RequiredArgsConstructor
@Tag(name = "Admin - Services", description = "APIs quản lý dịch vụ và giá niêm yết dành cho Quản trị viên")
public class AdminServiceController {

    private final ServiceCatalogService serviceCatalogService;

    @PostMapping
    @Operation(summary = "Tạo mới dịch vụ kèm base_price")
    public ResponseEntity<ApiResponse<ServiceResponse>> create(
            @Valid @RequestBody CreateServiceRequest request) {
        ServiceResponse response = serviceCatalogService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Tạo dịch vụ thành công", response));
    }

    @GetMapping
    @Operation(summary = "Quản lý danh sách dịch vụ (hỗ trợ lọc theo typeId và status)")
    public ResponseEntity<ApiResponse<List<ServiceResponse>>> getAll(
            @RequestParam(required = false) Integer typeId,
            @RequestParam(required = false) ServiceStatus status) {
        List<ServiceResponse> response = serviceCatalogService.getAll(typeId, status);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Xem chi tiết một dịch vụ theo ID")
    public ResponseEntity<ApiResponse<ServiceResponse>> getById(@PathVariable Integer id) {
        ServiceResponse response = serviceCatalogService.getById(id);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Cập nhật thông tin dịch vụ (tên, mô tả, base_price, nhóm dịch vụ)")
    public ResponseEntity<ApiResponse<ServiceResponse>> update(
            @PathVariable Integer id,
            @Valid @RequestBody UpdateServiceRequest request) {
        ServiceResponse response = serviceCatalogService.update(id, request);
        return ResponseEntity.ok(ApiResponse.success("Cập nhật dịch vụ thành công", response));
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Đổi trạng thái dịch vụ (ACTIVE / INACTIVE)")
    public ResponseEntity<ApiResponse<ServiceResponse>> updateStatus(
            @PathVariable Integer id,
            @Valid @RequestBody UpdateServiceStatusRequest request) {
        ServiceResponse response = serviceCatalogService.updateStatus(id, request);
        return ResponseEntity.ok(ApiResponse.success("Cập nhật trạng thái dịch vụ thành công", response));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Kích hoạt Soft-delete cho dịch vụ (chuyển sang INACTIVE)")
    public ResponseEntity<ApiResponse<Void>> softDelete(@PathVariable Integer id) {
        serviceCatalogService.softDelete(id);
        return ResponseEntity.ok(ApiResponse.success("Xóa dịch vụ thành công (đã chuyển sang INACTIVE)"));
    }
}

