package com.example.house_moving_service.modules.inventory.controller.field;

import com.example.house_moving_service.common.dto.ApiResponse;
import com.example.house_moving_service.modules.inventory.dto.response.ScheduleMaterialsResponse;
import com.example.house_moving_service.modules.inventory.service.MaterialAllocationService;
import com.example.house_moving_service.security.access.MoverOnly;
import com.example.house_moving_service.security.userdetails.CustomUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/field")
@MoverOnly
@RequiredArgsConstructor
@Tag(name = "Field - Materials", description = "Nhân viên hiện trường xem vật tư đã cấp cho chuyến mình được phân công")
@SecurityRequirement(name = "bearerAuth")
public class FieldMaterialController {

    private final MaterialAllocationService materialAllocationService;

    @GetMapping("/moving-schedules/{scheduleId}/materials")
    @Operation(
            summary = "Vật tư đã cấp cho chuyến",
            description = "Chỉ nhân viên có phân công (chưa hủy) trên chuyến, sai → 403 mã 8003. Mọi dòng cấp phát (mọi "
                    + "trạng thái), không có tiền: unitPrice, chargedAmount, estimatedTotal, chargedTotal = null."
    )
    public ResponseEntity<ApiResponse<ScheduleMaterialsResponse>> getScheduleMaterials(
            @Parameter(description = "ID chuyến", required = true) @PathVariable Integer scheduleId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(ApiResponse.ok(
                materialAllocationService.getFieldScheduleMaterials(scheduleId, userDetails.getId())));
    }
}
