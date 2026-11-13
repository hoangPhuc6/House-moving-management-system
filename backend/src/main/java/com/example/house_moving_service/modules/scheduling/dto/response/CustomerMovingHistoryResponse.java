package com.example.house_moving_service.modules.scheduling.dto.response;

import com.example.house_moving_service.modules.booking.enums.BookingStatus;
import lombok.Builder;

import java.util.List;

/**
 * Lịch sử vận chuyển của đơn, phía khách: các chuyến (Task 1) và sự cố chưa bị bác bỏ (Task 2);
 * phần phụ phí (Task 3) bổ sung sau.
 */
@Builder
public record CustomerMovingHistoryResponse(
        Integer bookingId,
        String bookingCode,
        BookingStatus bookingStatus,
        List<CustomerMovingTripResponse> trips,
        List<CustomerIncidentResponse> incidents
) {
}
