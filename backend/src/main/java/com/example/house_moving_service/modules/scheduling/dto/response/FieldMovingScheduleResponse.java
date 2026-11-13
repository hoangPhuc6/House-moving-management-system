package com.example.house_moving_service.modules.scheduling.dto.response;

import com.example.house_moving_service.modules.booking.dto.response.BookingRouteResponse;
import com.example.house_moving_service.modules.booking.enums.BookingStatus;
import com.example.house_moving_service.modules.scheduling.enums.MovingScheduleStatus;
import com.example.house_moving_service.modules.scheduling.enums.StaffAssignmentRole;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.List;

/** Chi tiết chuyến cho nhân viên được phân công: lịch, thời gian thực tế, liên hệ, tuyến đường, nhân viên, xe, nhật ký. */
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public record FieldMovingScheduleResponse(
        Integer id,
        Integer bookingId,
        String bookingCode,
        BookingStatus bookingStatus,
        MovingScheduleStatus status,
        LocalDateTime scheduledStartAt,
        LocalDateTime scheduledEndAt,
        LocalDateTime actualStartAt,
        LocalDateTime actualEndAt,
        Long delayMinutes,
        StaffAssignmentRole myRole,
        String contactName,
        String contactPhone,
        BookingRouteResponse route,
        List<MovingStaffResponse> staff,
        List<MovingVehicleResponse> vehicles,
        List<MovingLogEntry> logs
) {
}
