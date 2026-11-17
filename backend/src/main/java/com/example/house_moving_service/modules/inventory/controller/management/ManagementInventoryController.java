package com.example.house_moving_service.modules.inventory.controller.management;

import com.example.house_moving_service.common.dto.ApiResponse;
import com.example.house_moving_service.modules.inventory.dto.request.DamageInventoryRequest;
import com.example.house_moving_service.modules.inventory.dto.request.ImportInventoryRequest;
import com.example.house_moving_service.modules.inventory.dto.response.InventoryTransactionResponse;
import com.example.house_moving_service.modules.inventory.service.InventoryService;
import com.example.house_moving_service.security.access.StaffOnly;
import com.example.house_moving_service.security.userdetails.CustomUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/management")
@StaffOnly
@RequiredArgsConstructor
@Tag(name = "Management - Inventory", description = "Điều phối viên nhập kho, xuất hủy hàng hư trong kho")
@SecurityRequirement(name = "bearerAuth")
public class ManagementInventoryController {

    private final InventoryService inventoryService;

    @PostMapping("/inventory/imports")
    @Operation(
            summary = "Nhập kho",
            description = "Mỗi món thành một giao dịch IMPORT (+quantity, lưu unitCost). Cả phiếu trong một transaction: "
                    + "một món lỗi thì không món nào được nhập. Vật tư phải đang hoạt động, quantity > 0, unitCost ≥ 0, "
                    + "không trùng materialId trong phiếu. Kết quả sắp theo materialId tăng dần."
    )
    public ResponseEntity<ApiResponse<List<InventoryTransactionResponse>>> importStock(
            @Valid @RequestBody ImportInventoryRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        List<InventoryTransactionResponse> response = inventoryService.importStock(request, userDetails.getId());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Nhập kho thành công", response));
    }

    @PostMapping("/inventory/damages")
    @Operation(
            summary = "Xuất hủy hàng hư trong kho",
            description = "Ghi DAMAGED và trừ tồn đúng quantity. Không được vượt tồn hiện tại (409 mã 9004), bắt buộc lý "
                    + "do. Vật tư phải đang hoạt động. Hàng hư / mất trong chuyến thì ghi lúc chốt chuyến, không dùng API này."
    )
    public ResponseEntity<ApiResponse<InventoryTransactionResponse>> writeOffDamaged(
            @Valid @RequestBody DamageInventoryRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        InventoryTransactionResponse response = inventoryService.writeOffDamaged(request, userDetails.getId());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Xuất hủy thành công", response));
    }
}
