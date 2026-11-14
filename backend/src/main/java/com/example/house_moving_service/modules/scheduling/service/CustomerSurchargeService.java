package com.example.house_moving_service.modules.scheduling.service;

import com.example.house_moving_service.modules.scheduling.dto.request.CustomerRejectSurchargeRequest;
import com.example.house_moving_service.modules.scheduling.dto.response.CustomerSurchargeResponse;
import com.example.house_moving_service.modules.scheduling.dto.response.CustomerSurchargeSummaryResponse;

/**
 * Phụ phí phía khách. Đơn không tồn tại → ERR_BOOKING_NOT_FOUND; xem đơn không phải của mình → ERR_BOOKING_FORBIDDEN,
 * xác nhận / từ chối trên đơn của người khác → ERR_SURCHARGE_FORBIDDEN;
 * khoản không thuộc đơn trong đường dẫn hoặc khách không được thấy → ERR_SURCHARGE_NOT_FOUND.
 */
public interface CustomerSurchargeService {

    // Khoản đang chờ xác nhận, đã xác nhận (kèm đã thu tiền chưa), đã từ chối; kèm acceptedTotal
    CustomerSurchargeSummaryResponse getSurcharges(Integer bookingId, Long customerUserId);

    // WAITING_CUSTOMER → ACCEPTED, cộng amount vào bookings.total_amount (đơn không được CANCELLED), cùng một transaction
    CustomerSurchargeResponse accept(Integer bookingId, Integer id, Long customerUserId);

    // WAITING_CUSTOMER → REJECTED_BY_CUSTOMER, ghi lý do của khách
    CustomerSurchargeResponse reject(Integer bookingId, Integer id, CustomerRejectSurchargeRequest request, Long customerUserId);
}
