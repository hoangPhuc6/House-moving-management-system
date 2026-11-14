package com.example.house_moving_service.modules.scheduling.dto.response;

import com.example.house_moving_service.modules.booking.enums.BookingStatus;
import lombok.Builder;

import java.util.List;

/**
 * Lịch sử vận chuyển của đơn, phía khách: các chuyến, sự cố chưa bị bác bỏ, và phụ phí khách được thấy
 * (đang chờ xác nhận / đã xác nhận / đã từ chối, kèm acceptedTotal).
 */
@Builder
public record CustomerMovingHistoryResponse(
        Integer bookingId,
        String bookingCode,
        BookingStatus bookingStatus,
        List<CustomerMovingTripResponse> trips,
        List<CustomerIncidentResponse> incidents,
        CustomerSurchargeSummaryResponse surcharges
) {
}
