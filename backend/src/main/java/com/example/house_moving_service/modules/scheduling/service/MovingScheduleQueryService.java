package com.example.house_moving_service.modules.scheduling.service;

import com.example.house_moving_service.common.dto.PageResponse;
import com.example.house_moving_service.modules.scheduling.dto.request.AvailabilityQuery;
import com.example.house_moving_service.modules.scheduling.dto.request.MovingScheduleFilterRequest;
import com.example.house_moving_service.modules.scheduling.dto.response.MovingScheduleSummaryResponse;
import com.example.house_moving_service.modules.scheduling.dto.response.MovingStaffAvailabilityResponse;
import com.example.house_moving_service.modules.scheduling.dto.response.VehicleResponse;
import org.springframework.data.domain.Pageable;

import java.util.List;

/** Tra cứu cho điều phối: danh sách chuyến, xe rảnh, nhân viên rảnh theo khung giờ. */
public interface MovingScheduleQueryService {

    PageResponse<MovingScheduleSummaryResponse> search(MovingScheduleFilterRequest filter, Pageable pageable);

    /** Xe AVAILABLE / IN_USE không trùng chuyến chưa xong nào trong [start, end). */
    List<VehicleResponse> findAvailableVehicles(AvailabilityQuery query);

    /** Mọi nhân viên hiện trường (MOVER) đang làm việc, kèm cờ rảnh và lý do bận (MOVING / SURVEY). */
    List<MovingStaffAvailabilityResponse> findStaffAvailability(AvailabilityQuery query);
}
