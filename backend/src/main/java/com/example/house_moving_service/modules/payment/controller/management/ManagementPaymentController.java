package com.example.house_moving_service.modules.payment.controller.management;

import com.example.house_moving_service.common.dto.ApiResponse;
import com.example.house_moving_service.common.dto.PageResponse;
import com.example.house_moving_service.modules.payment.dto.request.ConfirmPaymentRequest;
import com.example.house_moving_service.modules.payment.dto.request.CreatePaymentRequest;
import com.example.house_moving_service.modules.payment.dto.request.PaymentFilterRequest;
import com.example.house_moving_service.modules.payment.dto.response.PaymentResponse;
import com.example.house_moving_service.modules.payment.dto.response.PaymentSummaryResponse;
import com.example.house_moving_service.modules.payment.service.PaymentManagementService;
import com.example.house_moving_service.security.access.StaffOnly;
import com.example.house_moving_service.security.userdetails.CustomUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/management/payments")
@Slf4j
@StaffOnly
@Tag(name = "Management - Payments", description = "APIs quản lý, đối soát và xác nhận thanh toán dành cho Quản trị viên & Điều phối viên")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
public class ManagementPaymentController {

    private final PaymentManagementService paymentManagementService;

    @GetMapping
    @Operation(summary = "Tìm kiếm và lọc danh sách giao dịch thanh toán",
            description = "Tra cứu danh sách thanh toán theo nhiều tiêu chí (mã đơn, trạng thái, phương thức, loại thanh toán, khoảng thời gian) kèm phân trang.")
    public ResponseEntity<ApiResponse<PageResponse<PaymentSummaryResponse>>> searchPayments(
            PaymentFilterRequest filter,
            @PageableDefault(page = 0, size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        log.info("Management search payments with filter: {}, pageable: {}", filter, pageable);
        PageResponse<PaymentSummaryResponse> response = paymentManagementService.searchPayments(filter, pageable);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Xem thông tin chi tiết một giao dịch thanh toán",
            description = "Tra cứu chi tiết một giao dịch thanh toán theo ID mà không bị giới hạn bởi quyền sở hữu đơn hàng.")
    public ResponseEntity<ApiResponse<PaymentResponse>> getPaymentDetail(
            @Parameter(description = "Mã định danh ID của giao dịch thanh toán", required = true)
            @PathVariable Integer id) {
        log.info("Management get payment detail for id: {}", id);
        PaymentResponse response = paymentManagementService.getPaymentDetail(id);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @GetMapping("/bookings/{bookingId}")
    @Operation(summary = "Xem toàn bộ lịch sử thanh toán của một đơn hàng",
            description = "Trả về danh sách tất cả các đợt thanh toán (cọc, từng phần, quyết toán, hoàn tiền) gắn liền với đơn hàng.")
    public ResponseEntity<ApiResponse<List<PaymentResponse>>> getPaymentsByBooking(
            @Parameter(description = "Mã định danh ID của đơn hàng", required = true)
            @PathVariable Integer bookingId) {
        log.info("Management get payment history for booking id: {}", bookingId);
        List<PaymentResponse> response = paymentManagementService.getPaymentsByBookingId(bookingId);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @PostMapping("/{id}/confirm")
    @Operation(summary = "Xác nhận giao dịch thanh toán thành công",
            description = "Dành cho nhân viên/kế toán xác nhận giao dịch Tiền mặt (CASH) hoặc Chuyển khoản (BANK_TRANSFER). Chuyển trạng thái giao dịch sang SUCCESS và cập nhật số bút toán.")
    public ResponseEntity<ApiResponse<PaymentResponse>> confirmPayment(
            @Parameter(description = "Mã định danh ID của giao dịch cần xác nhận", required = true)
            @PathVariable Integer id,
            @Valid @RequestBody ConfirmPaymentRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        log.info("Staff user {} confirming payment id: {}", userDetails.getId(), id);
        PaymentResponse response = paymentManagementService.confirmPayment(id, request, userDetails.getId());
        return ResponseEntity.ok(ApiResponse.success("Xác nhận thanh toán thành công", response));
    }

    @PostMapping("/{id}/reject")
    @Operation(summary = "Từ chối giao dịch thanh toán",
            description = "Đánh dấu giao dịch thanh toán là thất bại (FAILED) khi không nhận được tiền, sai cú pháp chuyển khoản hoặc hóa đơn không hợp lệ.")
    public ResponseEntity<ApiResponse<PaymentResponse>> rejectPayment(
            @Parameter(description = "Mã định danh ID của giao dịch cần từ chối", required = true)
            @PathVariable Integer id,
            @Parameter(description = "Lý do từ chối giao dịch")
            @RequestParam(required = false) String reason,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        log.info("Staff user {} rejecting payment id: {} with reason: {}", userDetails.getId(), id, reason);
        PaymentResponse response = paymentManagementService.rejectPayment(id, reason, userDetails.getId());
        return ResponseEntity.ok(ApiResponse.success("Đã từ chối giao dịch thanh toán", response));
    }

    @PostMapping("/manual")
    @Operation(summary = "Lập phiếu thu thanh toán thủ công tại quầy",
            description = "Nhân viên trực tiếp tạo một phiếu thu cho khách hàng khi thu tiền mặt hoặc nhận chuyển khoản trực tiếp tại văn phòng.")
    public ResponseEntity<ApiResponse<PaymentResponse>> recordManualPayment(
            @Valid @RequestBody CreatePaymentRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        log.info("Staff user {} recording manual payment for booking: {}", userDetails.getId(), request.getBookingId());
        PaymentResponse response = paymentManagementService.recordManualPayment(request, userDetails.getId());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Ghi nhận phiếu thanh toán thành công", response));
    }

    @PostMapping("/refund")
    @Operation(summary = "Khởi tạo phiếu hoàn tiền cho đơn hàng",
            description = "Tạo giao dịch hoàn tiền (REFUND) cho khách hàng trong trường hợp hủy đơn có cọc hoặc đền bù thiệt hại dịch vụ.")
    public ResponseEntity<ApiResponse<PaymentResponse>> createRefund(
            @Valid @RequestBody CreatePaymentRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        log.info("Staff user {} creating refund for booking: {}", userDetails.getId(), request.getBookingId());
        PaymentResponse response = paymentManagementService.createRefund(request, userDetails.getId());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Tạo yêu cầu hoàn tiền thành công", response));
    }
}

