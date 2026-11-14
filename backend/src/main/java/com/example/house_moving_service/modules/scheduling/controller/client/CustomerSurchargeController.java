package com.example.house_moving_service.modules.scheduling.controller.client;

import com.example.house_moving_service.common.dto.ApiResponse;
import com.example.house_moving_service.modules.scheduling.dto.request.CustomerRejectSurchargeRequest;
import com.example.house_moving_service.modules.scheduling.dto.response.CustomerSurchargeResponse;
import com.example.house_moving_service.modules.scheduling.dto.response.CustomerSurchargeSummaryResponse;
import com.example.house_moving_service.modules.scheduling.service.CustomerSurchargeService;
import com.example.house_moving_service.security.access.CustomerOnly;
import com.example.house_moving_service.security.userdetails.CustomUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/bookings")
@CustomerOnly
@RequiredArgsConstructor
@Tag(name = "Booking - Surcharges", description = "Khách hàng xem, xác nhận hoặc từ chối báo giá điều chỉnh của đơn mình")
@SecurityRequirement(name = "bearerAuth")
public class CustomerSurchargeController {

    private final CustomerSurchargeService customerSurchargeService;

    @GetMapping("/{bookingId}/surcharges")
    @Operation(
            summary = "Phụ phí của đơn",
            description = "Khoản đang chờ xác nhận (WAITING_CUSTOMER), đã xác nhận (kèm đã thu tiền chưa), đã từ chối; "
                    + "kèm waitingTotal, acceptedTotal, uncollectedTotal. Đơn không phải của mình → 403 (4004)."
    )
    public ResponseEntity<ApiResponse<CustomerSurchargeSummaryResponse>> getSurcharges(
            @Parameter(description = "ID đơn hàng", required = true) @PathVariable Integer bookingId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(ApiResponse.ok(customerSurchargeService.getSurcharges(bookingId, userDetails.getId())));
    }

    @PatchMapping("/{bookingId}/surcharges/{id}/accept")
    @Operation(
            summary = "Xác nhận báo giá điều chỉnh",
            description = "WAITING_CUSTOMER → ACCEPTED; số tiền được cộng vào tổng tiền đơn. Khách trả trực tiếp cho người "
                    + "vận chuyển tại hiện trường."
    )
    public ResponseEntity<ApiResponse<CustomerSurchargeResponse>> accept(
            @Parameter(description = "ID đơn hàng", required = true) @PathVariable Integer bookingId,
            @Parameter(description = "ID phụ phí", required = true) @PathVariable Integer id,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        CustomerSurchargeResponse response = customerSurchargeService.accept(bookingId, id, userDetails.getId());
        return ResponseEntity.ok(ApiResponse.success("Đã xác nhận báo giá điều chỉnh", response));
    }

    @PatchMapping("/{bookingId}/surcharges/{id}/reject")
    @Operation(
            summary = "Từ chối báo giá điều chỉnh",
            description = "WAITING_CUSTOMER → REJECTED_BY_CUSTOMER. Bắt buộc customerNote. Công ty sẽ liên hệ thương lượng lại."
    )
    public ResponseEntity<ApiResponse<CustomerSurchargeResponse>> reject(
            @Parameter(description = "ID đơn hàng", required = true) @PathVariable Integer bookingId,
            @Parameter(description = "ID phụ phí", required = true) @PathVariable Integer id,
            @Valid @RequestBody CustomerRejectSurchargeRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        CustomerSurchargeResponse response = customerSurchargeService.reject(bookingId, id, request, userDetails.getId());
        return ResponseEntity.ok(ApiResponse.success("Đã từ chối báo giá điều chỉnh", response));
    }
}
