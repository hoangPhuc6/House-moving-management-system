package com.example.house_moving_service.modules.payment.mapper;

import com.example.house_moving_service.modules.payment.dto.response.PaymentResponse;
import com.example.house_moving_service.modules.payment.dto.response.PaymentSummaryResponse;
import com.example.house_moving_service.modules.payment.entity.Payment;
import org.springframework.stereotype.Component;

@Component
public class PaymentMapper {

    /**
     * Chuyển đổi Payment entity sang PaymentResponse chi tiết.
     *
     * @param payment Entity Payment
     * @return DTO PaymentResponse
     */
    public PaymentResponse toResponse(Payment payment) {
        return toResponse(payment, null);
    }

    /**
     * Chuyển đổi Payment entity sang PaymentResponse chi tiết có kèm đường dẫn
     * thanh toán trực tuyến (paymentUrl).
     *
     * @param payment    Entity Payment
     * @param paymentUrl URL chuyển hướng thanh toán (VNPay, MoMo, cổng trực
     *                   tuyến...)
     * @return DTO PaymentResponse
     */
    public PaymentResponse toResponse(Payment payment, String paymentUrl) {
        if (payment == null) {
            return null;
        }

        Integer bookingId = null;
        String bookingCode = null;
        if (payment.getBooking() != null) {
            bookingId = payment.getBooking().getId();
            bookingCode = payment.getBooking().getBookingCode();
        }

        return PaymentResponse.builder()
                .id(payment.getId())
                .bookingId(bookingId)
                .bookingCode(bookingCode)
                .paymentMethod(payment.getPaymentMethod())
                .paymentType(payment.getPaymentType())
                .amount(payment.getAmount())
                .status(payment.getStatus())
                .transactionCode(payment.getTransactionCode())
                .paymentTime(payment.getPaymentTime())
                .note(payment.getNote())
                .createdAt(payment.getCreatedAt())
                .paymentUrl(paymentUrl)
                .build();
    }

    /**
     * Chuyển đổi Payment entity sang PaymentSummaryResponse phục vụ danh sách/phân
     * trang.
     *
     * @param payment Entity Payment
     * @return DTO PaymentSummaryResponse
     */
    public PaymentSummaryResponse toSummaryResponse(Payment payment) {
        if (payment == null) {
            return null;
        }

        Integer bookingId = null;
        String bookingCode = null;
        if (payment.getBooking() != null) {
            bookingId = payment.getBooking().getId();
            bookingCode = payment.getBooking().getBookingCode();
        }

        return PaymentSummaryResponse.builder()
                .id(payment.getId())
                .bookingId(bookingId)
                .bookingCode(bookingCode)
                .paymentMethod(payment.getPaymentMethod())
                .paymentType(payment.getPaymentType())
                .amount(payment.getAmount())
                .status(payment.getStatus())
                .transactionCode(payment.getTransactionCode())
                .paymentTime(payment.getPaymentTime())
                .createdAt(payment.getCreatedAt())
                .build();
    }
}
