package com.example.house_moving_service.modules.scheduling.dto.response;

import com.example.house_moving_service.modules.booking.enums.BookingStatus;
import lombok.Builder;

/** Một chuyến cho điều phối: thông tin đơn + chi tiết chuyến (mọi phân công, kể cả đã hủy). */
@Builder
public record ManagementMovingScheduleResponse(
        Integer bookingId,
        String bookingCode,
        BookingStatus bookingStatus,
        ManagementMovingTripResponse schedule
) {
}
