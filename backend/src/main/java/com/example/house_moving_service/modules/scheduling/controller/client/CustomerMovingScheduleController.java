package com.example.house_moving_service.modules.scheduling.controller.client;

import com.example.house_moving_service.common.dto.ApiResponse;
import com.example.house_moving_service.modules.scheduling.dto.response.CustomerMovingHistoryResponse;
import com.example.house_moving_service.modules.scheduling.dto.response.CustomerMovingScheduleResponse;
import com.example.house_moving_service.modules.scheduling.dto.response.CustomerMovingTripResponse;
import com.example.house_moving_service.modules.scheduling.service.MovingHistoryService;
import com.example.house_moving_service.security.access.CustomerOnly;
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

import java.util.List;

@RestController
@RequestMapping("/api/v1/bookings")
@CustomerOnly
@RequiredArgsConstructor
@Tag(name = "Booking - Moving", description = "Khách hàng theo dõi các chuyến và lịch sử vận chuyển của đơn mình")
@SecurityRequirement(name = "bearerAuth")
public class CustomerMovingScheduleController {

    private final MovingHistoryService movingHistoryService;

    @GetMapping("/{bookingId}/moving-schedules")
    @Operation(
            summary = "Các chuyến vận chuyển của đơn",
            description = "Sắp theo giờ hẹn bắt đầu; mỗi chuyến gồm trạng thái, giờ hẹn, giờ thực tế, delayMinutes. "
                    + "Không gồm chuyến đã hủy. Đơn không phải của mình → 403."
    )
    public ResponseEntity<ApiResponse<List<CustomerMovingScheduleResponse>>> getSchedules(
            @Parameter(description = "ID đơn hàng", required = true) @PathVariable Integer bookingId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(ApiResponse.ok(movingHistoryService.getCustomerSchedules(bookingId, userDetails.getId())));
    }

    @GetMapping("/{bookingId}/moving-schedules/{scheduleId}")
    @Operation(
            summary = "Một chuyến vận chuyển của đơn",
            description = "Trạng thái, giờ hẹn / thực tế, delayMinutes, dòng thời gian các mốc, biển số xe, liên hệ giám sát viên. "
                    + "Chuyến không thuộc đơn hoặc đã hủy → 404."
    )
    public ResponseEntity<ApiResponse<CustomerMovingTripResponse>> getSchedule(
            @Parameter(description = "ID đơn hàng", required = true) @PathVariable Integer bookingId,
            @Parameter(description = "ID chuyến vận chuyển", required = true) @PathVariable Integer scheduleId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(ApiResponse.ok(
                movingHistoryService.getCustomerSchedule(bookingId, scheduleId, userDetails.getId())));
    }

    @GetMapping("/{bookingId}/moving-history")
    @Operation(
            summary = "Lịch sử vận chuyển của đơn",
            description = "Các chuyến chưa hủy: trạng thái, giờ hẹn / thực tế, delayMinutes, dòng thời gian [{ milestone, at }] "
                    + "(START → các mốc → COMPLETE), biển số xe, họ tên và số điện thoại giám sát viên. "
                    + "Khi có chuyến IN_PROGRESS, frontend gọi lại mỗi 30–60 giây."
    )
    public ResponseEntity<ApiResponse<CustomerMovingHistoryResponse>> getHistory(
            @Parameter(description = "ID đơn hàng", required = true) @PathVariable Integer bookingId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(ApiResponse.ok(movingHistoryService.getCustomerHistory(bookingId, userDetails.getId())));
    }
}
