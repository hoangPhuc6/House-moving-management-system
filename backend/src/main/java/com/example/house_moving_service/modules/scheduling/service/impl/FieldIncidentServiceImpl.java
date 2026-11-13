package com.example.house_moving_service.modules.scheduling.service.impl;

import com.example.house_moving_service.common.exception.AppException;
import com.example.house_moving_service.common.exception.ErrorCode;
import com.example.house_moving_service.modules.identity.service.UserLookupService;
import com.example.house_moving_service.modules.scheduling.config.MovingProperties;
import com.example.house_moving_service.modules.scheduling.dto.request.ReportIncidentRequest;
import com.example.house_moving_service.modules.scheduling.dto.response.IncidentResponse;
import com.example.house_moving_service.modules.scheduling.entity.Incident;
import com.example.house_moving_service.modules.scheduling.entity.MovingSchedule;
import com.example.house_moving_service.modules.scheduling.enums.AssignmentStatus;
import com.example.house_moving_service.modules.scheduling.enums.IncidentStatus;
import com.example.house_moving_service.modules.scheduling.enums.MovingScheduleStatus;
import com.example.house_moving_service.modules.scheduling.mapper.IncidentMapper;
import com.example.house_moving_service.modules.scheduling.repository.IncidentRepository;
import com.example.house_moving_service.modules.scheduling.repository.MovingScheduleRepository;
import com.example.house_moving_service.modules.scheduling.repository.ScheduleStaffAssignmentRepository;
import com.example.house_moving_service.modules.scheduling.service.FieldIncidentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

@Service
@Slf4j
@RequiredArgsConstructor
public class FieldIncidentServiceImpl implements FieldIncidentService {

    private static final Set<MovingScheduleStatus> REPORTABLE_SCHEDULE_STATUSES =
            EnumSet.of(MovingScheduleStatus.CONFIRMED, MovingScheduleStatus.IN_PROGRESS, MovingScheduleStatus.COMPLETED);

    private final IncidentRepository incidentRepository;
    private final MovingScheduleRepository movingScheduleRepository;
    private final ScheduleStaffAssignmentRepository staffAssignmentRepository;
    private final UserLookupService userLookupService;
    private final IncidentMapper incidentMapper;
    private final MovingProperties movingProperties;
    private final Clock clock;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public IncidentResponse report(Integer scheduleId, ReportIncidentRequest request, Long userId) {
        MovingSchedule schedule = movingScheduleRepository.findById(scheduleId)
                .orElseThrow(() -> new AppException(ErrorCode.ERR_MOVING_SCHEDULE_NOT_FOUND, scheduleId));
        if (!isAssigned(scheduleId, userId)) {
            throw new AppException(ErrorCode.ERR_MOVING_FORBIDDEN);
        }
        LocalDateTime now = LocalDateTime.now(clock);
        ensureReportable(schedule, now);

        Incident incident = Incident.builder()
                .booking(schedule.getBooking())
                .schedule(schedule)
                .reportedBy(userLookupService.getUserReference(userId))
                .incidentType(request.getIncidentType())
                .description(request.getDescription().trim())
                .damageCost(request.getEstimatedDamageCost() != null ? request.getEstimatedDamageCost() : BigDecimal.ZERO)
                .status(IncidentStatus.REPORTED)
                .reportedAt(now)
                .build();
        Incident saved = incidentRepository.save(incident);

        log.info("Incident id: {} ({}) reported on moving schedule id: {} by user id: {}",
                saved.getId(), saved.getIncidentType(), scheduleId, userId);
        return incidentMapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<IncidentResponse> getBySchedule(Integer scheduleId, Long userId) {
        if (!movingScheduleRepository.existsById(scheduleId)) {
            throw new AppException(ErrorCode.ERR_MOVING_SCHEDULE_NOT_FOUND, scheduleId);
        }
        if (!isAssigned(scheduleId, userId)) {
            throw new AppException(ErrorCode.ERR_MOVING_FORBIDDEN);
        }
        return incidentRepository.findAllByScheduleIdOrderByReportedAtDescIdDesc(scheduleId).stream()
                .map(incidentMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public IncidentResponse getDetail(Integer id, Long userId) {
        Incident incident = incidentRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.ERR_INCIDENT_NOT_FOUND, id));
        if (incident.getSchedule() == null || !isAssigned(incident.getSchedule().getId(), userId)) {
            throw new AppException(ErrorCode.ERR_INCIDENT_FORBIDDEN);
        }
        return incidentMapper.toResponse(incident);
    }

    // Có dòng phân công chưa hủy trên chuyến (mọi role)
    private boolean isAssigned(Integer scheduleId, Long userId) {
        return staffAssignmentRepository.findByMovingScheduleIdAndStaffId(scheduleId, userId)
                .filter(a -> a.getStatus() != AssignmentStatus.CANCELLED)
                .isPresent();
    }

    private void ensureReportable(MovingSchedule schedule, LocalDateTime now) {
        if (!REPORTABLE_SCHEDULE_STATUSES.contains(schedule.getStatus())) {
            throw new AppException(ErrorCode.ERR_INCIDENT_SCHEDULE_INVALID, "chuyến đang ở trạng thái " + schedule.getStatus());
        }
        if (schedule.getStatus() == MovingScheduleStatus.COMPLETED && schedule.getActualEndAt() != null) {
            int windowHours = movingProperties.getIncidentReportWindowHours();
            if (now.isAfter(schedule.getActualEndAt().plusHours(windowHours))) {
                throw new AppException(ErrorCode.ERR_INCIDENT_SCHEDULE_INVALID,
                        "quá " + windowHours + " giờ sau khi hoàn tất chuyến");
            }
        }
    }
}
