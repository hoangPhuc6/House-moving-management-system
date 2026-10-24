package com.example.house_moving_service.modules.catalog.controller.client;

import com.example.house_moving_service.common.dto.ApiResponse;
import com.example.house_moving_service.modules.catalog.dto.response.ServiceResponse;
import com.example.house_moving_service.modules.catalog.dto.response.ServiceTypeResponse;
import com.example.house_moving_service.modules.catalog.service.ServiceCatalogService;
import com.example.house_moving_service.security.access.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/services")
@AuthenticatedUser
@RequiredArgsConstructor
@Tag(name = "Services Catalog", description = "APIs tra cứu danh mục dịch vụ niêm yết dành cho người dùng đã đăng nhập")
public class ServiceCatalogController {

    private final ServiceCatalogService serviceCatalogService;

    @GetMapping
    @Operation(summary = "Lấy toàn bộ dịch vụ đang ACTIVE để render giao diện chọn đồ")
    public ResponseEntity<ApiResponse<Object>> getActiveCatalog(
            @RequestParam(defaultValue = "false") boolean grouped) {
        if (grouped) {
            List<ServiceTypeResponse> response = serviceCatalogService.getActiveCatalogGroupedByType();
            return ResponseEntity.ok(ApiResponse.success(response));
        }
        List<ServiceResponse> response = serviceCatalogService.getActiveCatalog();
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Tra cứu giá niêm yết của 1 dịch vụ đang hoạt động")
    public ResponseEntity<ApiResponse<ServiceResponse>> getById(@PathVariable Integer id) {
        ServiceResponse response = serviceCatalogService.getActiveServiceById(id);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}

