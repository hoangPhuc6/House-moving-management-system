package com.example.house_moving_service.modules.scheduling.controller.management;

import com.example.house_moving_service.common.dto.ApiResponse;
import com.example.house_moving_service.modules.scheduling.dto.response.ManagementMovingHistoryResponse;
import com.example.house_moving_service.modules.scheduling.service.MovingHistoryService;
import com.example.house_moving_service.security.access.StaffOnly;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/management/bookings")
@StaffOnly
@RequiredArgsConstructor
@Tag(name = "Management - Moving history", description = "Điều phối viên xem lịch sử vận chuyển đầy đủ của đơn")
@SecurityRequirement(name = "bearerAuth")
public class ManagementMovingHistoryController {

    private final MovingHistoryService movingHistoryService;

    @GetMapping("/{bookingId}/moving-history")
    @Operation(
            summary = "Lịch sử vận chuyển đầy đủ của đơn",
            description = "Mọi chuyến (kể cả đã hủy), nhật ký gốc (note) và đã tách, toàn bộ nhân viên và xe được phân công."
    )
    public ResponseEntity<ApiResponse<ManagementMovingHistoryResponse>> getHistory(
            @Parameter(description = "ID đơn hàng", required = true) @PathVariable Integer bookingId) {
        return ResponseEntity.ok(ApiResponse.ok(movingHistoryService.getManagementHistory(bookingId)));
    }
}
