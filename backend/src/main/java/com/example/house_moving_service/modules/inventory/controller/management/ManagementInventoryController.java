package com.example.house_moving_service.modules.inventory.controller.management;

import com.example.house_moving_service.common.dto.ApiResponse;
import com.example.house_moving_service.common.dto.PageResponse;
import com.example.house_moving_service.modules.inventory.dto.request.AdjustInventoryRequest;
import com.example.house_moving_service.modules.inventory.dto.request.DamageInventoryRequest;
import com.example.house_moving_service.modules.inventory.dto.request.ImportInventoryRequest;
import com.example.house_moving_service.modules.inventory.dto.request.InventoryTransactionFilterRequest;
import com.example.house_moving_service.modules.inventory.dto.request.MaterialFilterRequest;
import com.example.house_moving_service.modules.inventory.dto.request.MaterialHistoryFilterRequest;
import com.example.house_moving_service.modules.inventory.dto.response.InventoryTransactionResponse;
import com.example.house_moving_service.modules.inventory.dto.response.MaterialHistoryResponse;
import com.example.house_moving_service.modules.inventory.dto.response.MaterialResponse;
import com.example.house_moving_service.modules.inventory.service.InventoryService;
import com.example.house_moving_service.modules.inventory.service.MaterialService;
import com.example.house_moving_service.security.access.StaffOnly;
import com.example.house_moving_service.security.userdetails.CustomUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/management")
@StaffOnly
@RequiredArgsConstructor
@Tag(name = "Management - Inventory", description = "Điều phối viên xem tồn kho và lịch sử giao dịch, nhập kho, xuất hủy hàng hư trong kho, kiểm kê")
@SecurityRequirement(name = "bearerAuth")
public class ManagementInventoryController {

    private final InventoryService inventoryService;
    private final MaterialService materialService;

    @GetMapping("/materials")
    @Operation(
            summary = "Danh sách vật tư và tồn kho (phân trang)",
            description = "Cho điều phối chọn vật tư khi xuất cho chuyến. Lọc giống API admin: category, isActive, keyword "
                    + "(một phần mã hoặc tên), lowStock. Mỗi dòng có quantityOnHand và cờ lowStock."
    )
    public ResponseEntity<ApiResponse<PageResponse<MaterialResponse>>> searchMaterials(
            @ParameterObject MaterialFilterRequest filter,
            @ParameterObject @PageableDefault(size = 10) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(materialService.search(filter, pageable)));
    }

    @GetMapping("/materials/{id}/transactions")
    @Operation(
            summary = "Lịch sử nhập xuất của một vật tư",
            description = "Trả thông tin vật tư (kèm tồn hiện tại), totals = tổng quantity theo từng loại giao dịch trong "
                    + "khoảng lọc (đủ 5 loại, không có thì 0) và trang giao dịch. Lọc type, from / to (yyyy-MM-dd, tính "
                    + "cả ngày to). Không truyền sort thì mới nhất trước. Mỗi dòng có balanceAfter = tồn sau giao dịch."
    )
    public ResponseEntity<ApiResponse<MaterialHistoryResponse>> getMaterialHistory(
            @Parameter(description = "ID vật tư", required = true) @PathVariable Integer id,
            @ParameterObject MaterialHistoryFilterRequest filter,
            @ParameterObject @PageableDefault(size = 10) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(inventoryService.getMaterialHistory(id, filter, pageable)));
    }

    @GetMapping("/inventory/transactions")
    @Operation(
            summary = "Tra cứu giao dịch kho",
            description = "Mọi giao dịch toàn kho, lọc materialId, type, bookingId, scheduleId, from / to (yyyy-MM-dd, "
                    + "tính cả ngày to). Không truyền sort thì mới nhất trước (createdAt desc, id desc)."
    )
    public ResponseEntity<ApiResponse<PageResponse<InventoryTransactionResponse>>> searchTransactions(
            @ParameterObject InventoryTransactionFilterRequest filter,
            @ParameterObject @PageableDefault(size = 10) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.ok(inventoryService.searchTransactions(filter, pageable)));
    }

    @GetMapping("/inventory/low-stock")
    @Operation(
            summary = "Vật tư tồn thấp",
            description = "Vật tư đang hoạt động có quantityOnHand < minStock, thiếu nhiều nhất trước. Vật tư đang tắt "
                    + "không được liệt kê."
    )
    public ResponseEntity<ApiResponse<List<MaterialResponse>>> getLowStock() {
        return ResponseEntity.ok(ApiResponse.ok(inventoryService.getLowStock()));
    }

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

    @PostMapping("/inventory/adjustments")
    @Operation(
            summary = "Kiểm kê",
            description = "Nhập số đếm thực tế (≥ 0). Chênh lệch = actualQuantity − tồn hiện tại, ghi ADJUST với quantity = "
                    + "|chênh lệch| và quantityChange = chênh lệch (dương hoặc âm); tồn kho thành actualQuantity. Bắt buộc "
                    + "lý do. Không lệch → 400 mã 9011, không ghi gì."
    )
    public ResponseEntity<ApiResponse<InventoryTransactionResponse>> adjust(
            @Valid @RequestBody AdjustInventoryRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        InventoryTransactionResponse response = inventoryService.adjust(request, userDetails.getId());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Kiểm kê thành công", response));
    }
}
