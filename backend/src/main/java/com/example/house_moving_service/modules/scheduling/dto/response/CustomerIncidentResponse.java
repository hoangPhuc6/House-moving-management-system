package com.example.house_moving_service.modules.scheduling.dto.response;

import com.example.house_moving_service.modules.scheduling.enums.IncidentStatus;
import com.example.house_moving_service.modules.scheduling.enums.IncidentType;
import lombok.Builder;

import java.time.LocalDateTime;

/**
 * Sự cố trả cho khách: loại, mô tả, trạng thái, kết quả xử lý. Không có người báo / người xử lý hay chi phí thiệt hại
 * (chi phí nội bộ). resolutionNote chỉ có khi sự cố đã RESOLVED.
 */
@Builder
public record CustomerIncidentResponse(
        Integer id,
        Integer scheduleId,
        IncidentType incidentType,
        String description,
        IncidentStatus status,
        String resolutionNote,
        LocalDateTime reportedAt,
        LocalDateTime resolvedAt
) {
}
