package com.example.house_moving_service.modules.scheduling.dto.request;

import com.example.house_moving_service.modules.scheduling.enums.MovingScheduleStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

/**
 * Bộ lọc danh sách chuyến cho điều phối (query param)
 * from / to (yyyy-MM-dd): chuyến có khung giờ giao với khoảng ngày này (tính cả ngày cuối).
 * vehicleId / staffId: chuyến có xe / nhân viên đó trong phân công còn hiệu lực.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MovingScheduleFilterRequest {

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate from;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate to;

    private MovingScheduleStatus status;

    private Integer bookingId;

    private Integer vehicleId;

    private Long staffId;
}
