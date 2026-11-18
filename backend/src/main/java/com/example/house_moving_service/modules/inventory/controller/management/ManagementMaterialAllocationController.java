package com.example.house_moving_service.modules.inventory.controller.management;

import com.example.house_moving_service.common.dto.ApiResponse;
import com.example.house_moving_service.modules.inventory.dto.request.AllocateMaterialsRequest;
import com.example.house_moving_service.modules.inventory.dto.request.CancelAllocationRequest;
import com.example.house_moving_service.modules.inventory.dto.request.SettleMaterialsRequest;
import com.example.house_moving_service.modules.inventory.dto.response.BookingMaterialsResponse;
import com.example.house_moving_service.modules.inventory.dto.response.MaterialAllocationResponse;
import com.example.house_moving_service.modules.inventory.dto.response.ScheduleMaterialsResponse;
import com.example.house_moving_service.modules.inventory.service.MaterialAllocationService;
import com.example.house_moving_service.security.access.StaffOnly;
import com.example.house_moving_service.security.userdetails.CustomUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/management")
@StaffOnly
@RequiredArgsConstructor
@Tag(name = "Management - Material Allocations", description = "Điều phối viên xuất vật tư cho chuyến, chốt và hủy cấp phát")
@SecurityRequirement(name = "bearerAuth")
public class ManagementMaterialAllocationController {

    private final MaterialAllocationService materialAllocationService;

    @GetMapping("/moving-schedules/{scheduleId}/materials")
    @Operation(
            summary = "Danh sách cấp phát vật tư của chuyến",
            description = "Mọi dòng cấp phát của chuyến (ALLOCATED / SETTLED / CANCELLED), theo id tăng dần, kèm tổng "
                    + "tiền tạm tính và đã tính. Dùng để hiển thị và lấy allocationId trước khi chốt."
    )
    public ResponseEntity<ApiResponse<ScheduleMaterialsResponse>> getScheduleMaterials(
            @Parameter(description = "ID chuyến", required = true) @PathVariable Integer scheduleId) {
        return ResponseEntity.ok(ApiResponse.ok(materialAllocationService.getScheduleMaterials(scheduleId)));
    }

    @GetMapping("/bookings/{bookingId}/materials")
    @Operation(
            summary = "Vật tư của đơn",
            description = "Mọi dòng cấp phát của đơn gom theo chuyến (chỉ chuyến có cấp phát, scheduleId tăng dần), mọi "
                    + "trạng thái; mỗi chuyến kèm tiền dự kiến / đã tính, chargedTotal = tổng đã tính của cả đơn."
    )
    public ResponseEntity<ApiResponse<BookingMaterialsResponse>> getBookingMaterials(
            @Parameter(description = "ID đơn hàng", required = true) @PathVariable Integer bookingId) {
        return ResponseEntity.ok(ApiResponse.ok(materialAllocationService.getBookingMaterials(bookingId)));
    }

    @PostMapping("/moving-schedules/{scheduleId}/materials")
    @Operation(
            summary = "Xuất vật tư cho chuyến",
            description = "Chuyến phải PLANNED / CONFIRMED / RESCHEDULED / IN_PROGRESS. Mỗi món một EXPORT (trừ tồn). Món "
                    + "chưa cấp cho chuyến thì tạo dòng mới và chụp đơn giá hiện tại; đã cấp thì cộng thêm vào dòng cũ, "
                    + "giữ đơn giá cũ. Một món thiếu tồn / đang tắt thì không xuất món nào."
    )
    public ResponseEntity<ApiResponse<ScheduleMaterialsResponse>> allocate(
            @Parameter(description = "ID chuyến", required = true) @PathVariable Integer scheduleId,
            @Valid @RequestBody AllocateMaterialsRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        ScheduleMaterialsResponse response = materialAllocationService.allocate(scheduleId, request, userDetails.getId());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Xuất vật tư cho chuyến thành công", response));
    }

    @PutMapping("/moving-schedules/{scheduleId}/materials/settle")
    @Operation(
            summary = "Chốt vật tư sau chuyến",
            description = "Chuyến phải COMPLETED. Gửi đúng và đủ mọi dòng ALLOCATED của chuyến; từng dòng quantityUsed + "
                    + "quantityReturned + quantityDamaged = quantityAllocated, TOOL thì quantityUsed = 0. Số hoàn → RETURN "
                    + "(cộng tồn); số hư / mất → DAMAGED (không trừ thêm, đã trừ lúc xuất). Tiền = quantityUsed × đơn giá "
                    + "lúc cấp, cộng vào tổng tiền đơn một lần."
    )
    public ResponseEntity<ApiResponse<ScheduleMaterialsResponse>> settle(
            @Parameter(description = "ID chuyến", required = true) @PathVariable Integer scheduleId,
            @Valid @RequestBody SettleMaterialsRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(ApiResponse.success("Chốt vật tư chuyến thành công",
                materialAllocationService.settle(scheduleId, request, userDetails.getId())));
    }

    @PatchMapping("/material-allocations/{id}/cancel")
    @Operation(
            summary = "Hủy cấp phát",
            description = "Dòng phải ALLOCATED; chuyến PLANNED / CONFIRMED / RESCHEDULED / CANCELLED. Hoàn toàn bộ số đã "
                    + "cấp về kho (RETURN), dòng chuyển CANCELLED, không tính tiền. Body không bắt buộc."
    )
    public ResponseEntity<ApiResponse<MaterialAllocationResponse>> cancel(
            @Parameter(description = "ID dòng cấp phát", required = true) @PathVariable Integer id,
            @Valid @RequestBody(required = false) CancelAllocationRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(ApiResponse.success("Hủy cấp phát thành công",
                materialAllocationService.cancel(id, request, userDetails.getId())));
    }
}
