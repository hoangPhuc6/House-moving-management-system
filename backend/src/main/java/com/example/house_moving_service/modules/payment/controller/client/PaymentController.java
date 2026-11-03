package com.example.house_moving_service.modules.payment.controller.client;

import com.example.house_moving_service.common.dto.ApiResponse;
import com.example.house_moving_service.modules.payment.dto.request.CreatePaymentRequest;
import com.example.house_moving_service.modules.payment.dto.response.PaymentResponse;
import com.example.house_moving_service.modules.payment.service.PaymentService;
import com.example.house_moving_service.security.access.CustomerOnly;
import com.example.house_moving_service.security.userdetails.CustomUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

import java.util.List;

@RestController
@RequestMapping("/api/v1/payments")
@Slf4j
@CustomerOnly
@Tag(name = "Payment - Client", description = "APIs thanh toán dịch vụ dành cho Khách hàng")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping
    @Operation(summary = "Khởi tạo yêu cầu thanh toán",
            description = "Khách hàng tạo yêu cầu thanh toán tiền đặt cọc (DEPOSIT) hoặc quyết toán (FINAL). Hỗ trợ trả về đường link cổng thanh toán hoặc thông tin chuyển khoản.")
    public ResponseEntity<ApiResponse<PaymentResponse>> createPayment(
            @Valid @RequestBody CreatePaymentRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        log.info("REST request to create payment by customer user: {} for booking: {}", userDetails.getId(), request.getBookingId());
        PaymentResponse response = paymentService.createPayment(request, userDetails.getId());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Khởi tạo yêu cầu thanh toán thành công", response));
    }

    @GetMapping("/bookings/{bookingId}")
    @Operation(summary = "Xem lịch sử thanh toán theo đơn hàng",
            description = "Lấy danh sách tất cả các đợt thanh toán của một đơn hàng thuộc quyền sở hữu của khách hàng hiện tại (chống IDOR).")
    public ResponseEntity<ApiResponse<List<PaymentResponse>>> getPaymentsByBooking(
            @Parameter(description = "Mã định danh ID của đơn hàng", required = true)
            @PathVariable Integer bookingId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        log.info("REST request to get payments for booking id: {} by customer user: {}", bookingId, userDetails.getId());
        List<PaymentResponse> response = paymentService.getPaymentsByBookingId(bookingId, userDetails.getId());
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Xem chi tiết một giao dịch thanh toán",
            description = "Tra cứu chi tiết một giao dịch thanh toán của khách hàng hiện tại. Kiểm tra quyền sở hữu đơn hàng tương ứng.")
    public ResponseEntity<ApiResponse<PaymentResponse>> getPaymentDetail(
            @Parameter(description = "Mã định danh ID của giao dịch thanh toán", required = true)
            @PathVariable Integer id,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        log.info("REST request to get payment detail for id: {} by customer user: {}", id, userDetails.getId());
        PaymentResponse response = paymentService.getPaymentDetail(id, userDetails.getId());
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @PatchMapping("/{id}/cancel")
    @Operation(summary = "Hủy yêu cầu thanh toán đang chờ",
            description = "Khách hàng hủy yêu cầu thanh toán khi giao dịch còn ở trạng thái PENDING (ví dụ muốn đổi phương thức thanh toán khác).")
    public ResponseEntity<ApiResponse<PaymentResponse>> cancelPayment(
            @Parameter(description = "Mã định danh ID của giao dịch thanh toán", required = true)
            @PathVariable Integer id,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        log.info("REST request to cancel payment id: {} by customer user: {}", id, userDetails.getId());
        PaymentResponse response = paymentService.cancelPayment(id, userDetails.getId());
        return ResponseEntity.ok(ApiResponse.success("Hủy yêu cầu thanh toán thành công", response));
    }
}

