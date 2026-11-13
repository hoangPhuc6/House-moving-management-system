package com.example.house_moving_service.modules.scheduling.dto.response;

import com.example.house_moving_service.modules.booking.enums.BookingStatus;
import lombok.Builder;

import java.util.List;

/**
 * Lịch sử vận chuyển của đơn, phía điều phối (mọi trạng thái): các chuyến (Task 1) và sự cố (Task 2);
 * phần phụ phí (Task 3) bổ sung sau.
 */
@Builder
public record ManagementMovingHistoryResponse(
        Integer bookingId,
        String bookingCode,
        BookingStatus bookingStatus,
        List<ManagementMovingTripResponse> trips,
        List<IncidentResponse> incidents
) {
}
