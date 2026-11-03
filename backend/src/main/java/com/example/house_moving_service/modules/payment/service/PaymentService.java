package com.example.house_moving_service.modules.payment.service;

import com.example.house_moving_service.modules.payment.dto.request.CreatePaymentRequest;
import com.example.house_moving_service.modules.payment.dto.response.PaymentResponse;

import java.util.List;

/**
 * Service xử lý các nghiệp vụ thanh toán dành cho Khách hàng (Client).
 */
public interface PaymentService {

    /**
     * Khởi tạo giao dịch thanh toán mới (đặt cọc, từng phần hoặc quyết toán).
     *
     * @param request Thông tin yêu cầu thanh toán
     * @param userId  ID của tài khoản khách hàng thực hiện
     * @return Thông tin giao dịch thanh toán (kèm link URL nếu là cổng online)
     */
    PaymentResponse createPayment(CreatePaymentRequest request, Long userId);

    /**
     * Lấy toàn bộ lịch sử thanh toán của một đơn hàng thuộc quyền sở hữu của khách hàng.
     *
     * @param bookingId ID của đơn hàng
     * @param userId    ID của tài khoản khách hàng
     * @return Danh sách các giao dịch thanh toán của đơn
     */
    List<PaymentResponse> getPaymentsByBookingId(Integer bookingId, Long userId);

    /**
     * Xem thông tin chi tiết một giao dịch thanh toán cụ thể của khách hàng.
     *
     * @param id     ID của giao dịch thanh toán
     * @param userId ID của tài khoản khách hàng
     * @return Chi tiết giao dịch thanh toán
     */
    PaymentResponse getPaymentDetail(Integer id, Long userId);

    /**
     * Khách hàng hủy một yêu cầu thanh toán khi giao dịch còn ở trạng thái PENDING.
     *
     * @param id     ID của giao dịch thanh toán
     * @param userId ID của tài khoản khách hàng
     * @return Chi tiết giao dịch thanh toán sau khi hủy
     */
    PaymentResponse cancelPayment(Integer id, Long userId);
}

