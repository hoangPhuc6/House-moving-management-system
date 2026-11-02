package com.example.house_moving_service.modules.payment.service;

import com.example.house_moving_service.common.dto.PageResponse;
import com.example.house_moving_service.modules.payment.dto.request.ConfirmPaymentRequest;
import com.example.house_moving_service.modules.payment.dto.request.CreatePaymentRequest;
import com.example.house_moving_service.modules.payment.dto.request.PaymentFilterRequest;
import com.example.house_moving_service.modules.payment.dto.response.PaymentResponse;
import com.example.house_moving_service.modules.payment.dto.response.PaymentSummaryResponse;
import org.springframework.data.domain.Pageable;

import java.util.List;

/**
 * Service xử lý các nghiệp vụ quản lý thanh toán dành cho Quản trị viên & Điều phối viên / Kế toán.
 */
public interface PaymentManagementService {

    /**
     * Tìm kiếm và lọc danh sách giao dịch thanh toán toàn hệ thống với phân trang đa tiêu chí.
     */
    PageResponse<PaymentSummaryResponse> searchPayments(PaymentFilterRequest filter, Pageable pageable);

    /**
     * Xem thông tin chi tiết một giao dịch thanh toán bất kỳ theo ID.
     */
    PaymentResponse getPaymentDetail(Integer id);

    /**
     * Lấy toàn bộ danh sách giao dịch thanh toán của một đơn hàng cụ thể.
     */
    List<PaymentResponse> getPaymentsByBookingId(Integer bookingId);

    /**
     * Xác nhận giao dịch thanh toán thủ công (CASH, BANK_TRANSFER) thành công.
     */
    PaymentResponse confirmPayment(Integer id, ConfirmPaymentRequest request, Long staffId);

    /**
     * Từ chối hoặc đánh dấu giao dịch thanh toán thất bại (FAILED).
     */
    PaymentResponse rejectPayment(Integer id, String reason, Long staffId);

    /**
     * Lập phiếu thu thanh toán thủ công / thanh toán trực tiếp tại quầy từ khách hàng.
     */
    PaymentResponse recordManualPayment(CreatePaymentRequest request, Long staffId);

    /**
     * Khởi tạo giao dịch hoàn tiền (REFUND) cho khách hàng.
     */
    PaymentResponse createRefund(CreatePaymentRequest request, Long staffId);
}

