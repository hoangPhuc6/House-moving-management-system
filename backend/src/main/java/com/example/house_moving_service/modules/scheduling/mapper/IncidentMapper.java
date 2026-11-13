package com.example.house_moving_service.modules.scheduling.mapper;

import com.example.house_moving_service.modules.scheduling.dto.response.CustomerIncidentResponse;
import com.example.house_moving_service.modules.scheduling.dto.response.IncidentResponse;
import com.example.house_moving_service.modules.scheduling.entity.Incident;
import com.example.house_moving_service.modules.scheduling.enums.IncidentStatus;
import org.springframework.stereotype.Component;

@Component
public class IncidentMapper {

    public IncidentResponse toResponse(Incident incident) {
        return IncidentResponse.builder()
                .id(incident.getId())
                .bookingId(incident.getBooking().getId())
                .bookingCode(incident.getBooking().getBookingCode())
                .scheduleId(incident.getSchedule() != null ? incident.getSchedule().getId() : null)
                .incidentType(incident.getIncidentType())
                .description(incident.getDescription())
                .damageCost(incident.getDamageCost())
                .status(incident.getStatus())
                .resolutionNote(incident.getResolutionNote())
                .reportedBy(incident.getReportedBy() != null ? incident.getReportedBy().getId() : null)
                .reportedAt(incident.getReportedAt())
                .resolvedBy(incident.getResolvedBy() != null ? incident.getResolvedBy().getId() : null)
                .resolvedAt(incident.getResolvedAt())
                .updatedAt(incident.getUpdatedAt())
                .build();
    }

    /** Bỏ thông tin nội bộ; ghi chú điều tra (lưu tạm ở resolutionNote khi INVESTIGATING) không trả cho khách. */
    public CustomerIncidentResponse toCustomerResponse(Incident incident) {
        return CustomerIncidentResponse.builder()
                .id(incident.getId())
                .scheduleId(incident.getSchedule() != null ? incident.getSchedule().getId() : null)
                .incidentType(incident.getIncidentType())
                .description(incident.getDescription())
                .status(incident.getStatus())
                .resolutionNote(incident.getStatus() == IncidentStatus.RESOLVED ? incident.getResolutionNote() : null)
                .reportedAt(incident.getReportedAt())
                .resolvedAt(incident.getResolvedAt())
                .build();
    }
}
