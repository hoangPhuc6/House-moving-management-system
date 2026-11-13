package com.example.house_moving_service.modules.scheduling.dto.response;

import com.example.house_moving_service.modules.booking.enums.BookingStatus;
import lombok.Builder;

import java.util.List;

/**
 * Lịch sử vận chuyển của đơn, phía khách. Hiện có phần các chuyến (Task 1);
 * phần sự cố (Task 2) và phụ phí (Task 3) bổ sung sau.
 */
@Builder
public record CustomerMovingHistoryResponse(
        Integer bookingId,
        String bookingCode,
        BookingStatus bookingStatus,
        List<CustomerMovingTripResponse> trips
) {
}
