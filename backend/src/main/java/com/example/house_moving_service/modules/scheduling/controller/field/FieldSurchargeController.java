package com.example.house_moving_service.modules.scheduling.controller.field;

import com.example.house_moving_service.common.dto.ApiResponse;
import com.example.house_moving_service.modules.scheduling.dto.request.CreateSurchargeRequest;
import com.example.house_moving_service.modules.scheduling.dto.response.FieldScheduleSurchargesResponse;
import com.example.house_moving_service.modules.scheduling.dto.response.SurchargeResponse;
import com.example.house_moving_service.modules.scheduling.service.FieldSurchargeService;
import com.example.house_moving_service.security.access.MoverOnly;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/field")
@MoverOnly
@RequiredArgsConstructor
@Tag(name = "Field - Surcharges", description = "Trưởng nhóm báo sai lệch / đề xuất phụ phí phát sinh và xác nhận đã thu tiền")
@SecurityRequirement(name = "bearerAuth")
public class FieldSurchargeController {

    private final FieldSurchargeService fieldSurchargeService;

    @PostMapping("/moving-schedules/{scheduleId}/surcharges")
    @Operation(
            summary = "Đề xuất phụ phí phát sinh",
            description = "Chỉ SUPERVISOR của chuyến. Chuyến phải CONFIRMED hoặc IN_PROGRESS; đơn không CANCELLED / COMPLETED. "
                    + "Tạo khoản PROPOSED chờ điều phối viên chốt giá. quantity là phần chênh lệch so với lúc khách khai. "
                    + "Đơn giá: EXTRA_FLOOR / NO_ELEVATOR / LONG_CARRY / EXTRA_DISTANCE tự lấy theo mã dịch vụ hệ thống "
                    + "(không gửi serviceId / unitPrice); EXTRA_ITEMS / DISASSEMBLY / PACKING_MATERIAL bắt buộc serviceId "
                    + "(không gửi unitPrice); WAITING_TIME / OTHER bắt buộc unitPrice (không gửi serviceId). "
                    + "Còn khoản PROPOSED / WAITING_CUSTOMER / REJECTED_BY_CUSTOMER thì chưa bắt đầu được chuyến."
    )
    public ResponseEntity<ApiResponse<SurchargeResponse>> propose(
            @Parameter(description = "ID chuyến vận chuyển", required = true) @PathVariable Integer scheduleId,
            @Valid @RequestBody CreateSurchargeRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        SurchargeResponse response = fieldSurchargeService.propose(scheduleId, request, userDetails.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Đã gửi đề xuất phụ phí", response));
    }

    @GetMapping("/moving-schedules/{scheduleId}/surcharges")
    @Operation(
            summary = "Phụ phí của chuyến",
            description = "Khoản gắn chuyến và khoản cấp đơn, cũ nhất trước; kèm startBlocked / blockingSurchargeIds "
                    + "(tính trên toàn đơn) và uncollectedTotal. Mọi nhân viên được phân công vào chuyến."
    )
    public ResponseEntity<ApiResponse<FieldScheduleSurchargesResponse>> getBySchedule(
            @Parameter(description = "ID chuyến vận chuyển", required = true) @PathVariable Integer scheduleId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(ApiResponse.ok(fieldSurchargeService.getBySchedule(scheduleId, userDetails.getId())));
    }

    @PatchMapping("/surcharges/{id}/cancel")
    @Operation(summary = "Rút lại đề xuất", description = "Chỉ người đề xuất, khi khoản còn PROPOSED → CANCELLED.")
    public ResponseEntity<ApiResponse<SurchargeResponse>> cancel(
            @Parameter(description = "ID phụ phí", required = true) @PathVariable Integer id,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        SurchargeResponse response = fieldSurchargeService.cancel(id, userDetails.getId());
        return ResponseEntity.ok(ApiResponse.success("Đã rút lại đề xuất phụ phí", response));
    }

    @PatchMapping("/surcharges/{id}/collect")
    @Operation(
            summary = "Xác nhận đã thu tiền phụ phí",
            description = "Chỉ SUPERVISOR của chuyến (khoản không gắn chuyến: SUPERVISOR của một chuyến chưa hủy của đơn). "
                    + "Khoản phải ACCEPTED và chưa thu. Tạo phiếu thu CASH / PARTIAL trong payments và lưu paymentId."
    )
    public ResponseEntity<ApiResponse<SurchargeResponse>> collect(
            @Parameter(description = "ID phụ phí", required = true) @PathVariable Integer id,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        SurchargeResponse response = fieldSurchargeService.collect(id, userDetails.getId());
        return ResponseEntity.ok(ApiResponse.success("Đã ghi nhận thu tiền phụ phí", response));
    }
}
