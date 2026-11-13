package com.example.house_moving_service.modules.scheduling.dto.response;

import com.example.house_moving_service.modules.scheduling.enums.IncidentStatus;
import com.example.house_moving_service.modules.scheduling.enums.IncidentType;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Sự cố đầy đủ, cho nhân viên hiện trường và điều phối. reportedBy / resolvedBy là id tài khoản. */
@Builder
public record IncidentResponse(
        Integer id,
        Integer bookingId,
        String bookingCode,
        Integer scheduleId,
        IncidentType incidentType,
        String description,
        BigDecimal damageCost,
        IncidentStatus status,
        String resolutionNote,
        Long reportedBy,
        LocalDateTime reportedAt,
        Long resolvedBy,
        LocalDateTime resolvedAt,
        LocalDateTime updatedAt
) {
}
