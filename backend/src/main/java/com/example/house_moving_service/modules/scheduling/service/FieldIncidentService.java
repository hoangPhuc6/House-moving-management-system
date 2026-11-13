package com.example.house_moving_service.modules.scheduling.service;

import com.example.house_moving_service.modules.scheduling.dto.request.ReportIncidentRequest;
import com.example.house_moving_service.modules.scheduling.dto.response.IncidentResponse;

import java.util.List;

/**
 * Sự cố phía hiện trường. Người gọi phải có dòng phân công chưa hủy trên chuyến (mọi role, không cần SUPERVISOR).
 */
public interface FieldIncidentService {

    /**
     * Báo sự cố của chuyến. Chuyến phải CONFIRMED / IN_PROGRESS / COMPLETED; đã COMPLETED thì chỉ trong
     * app.moving.incident-report-window-hours sau actual_end_at (→ ERR_INCIDENT_SCHEDULE_INVALID). booking lấy theo chuyến.
     */
    IncidentResponse report(Integer scheduleId, ReportIncidentRequest request, Long userId);

    // Sự cố của chuyến, mới nhất trước; không được phân công → ERR_MOVING_FORBIDDEN
    List<IncidentResponse> getBySchedule(Integer scheduleId, Long userId);

    // Chi tiết; sự cố không gắn chuyến hoặc người gọi không thuộc chuyến → ERR_INCIDENT_FORBIDDEN
    IncidentResponse getDetail(Integer id, Long userId);
}
