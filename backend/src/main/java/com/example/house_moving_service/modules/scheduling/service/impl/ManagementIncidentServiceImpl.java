package com.example.house_moving_service.modules.scheduling.service.impl;

import com.example.house_moving_service.common.dto.PageResponse;
import com.example.house_moving_service.common.exception.AppException;
import com.example.house_moving_service.common.exception.ErrorCode;
import com.example.house_moving_service.modules.booking.dto.response.BookingSnapshot;
import com.example.house_moving_service.modules.booking.enums.BookingStatus;
import com.example.house_moving_service.modules.booking.service.BookingWorkflowService;
import com.example.house_moving_service.modules.identity.service.UserLookupService;
import com.example.house_moving_service.modules.scheduling.dto.request.IncidentFilterRequest;
import com.example.house_moving_service.modules.scheduling.dto.request.MovingNoteRequest;
import com.example.house_moving_service.modules.scheduling.dto.request.RejectIncidentRequest;
import com.example.house_moving_service.modules.scheduling.dto.request.ReportIncidentRequest;
import com.example.house_moving_service.modules.scheduling.dto.request.ResolveIncidentRequest;
import com.example.house_moving_service.modules.scheduling.dto.response.IncidentResponse;
import com.example.house_moving_service.modules.scheduling.entity.Incident;
import com.example.house_moving_service.modules.scheduling.entity.MovingSchedule;
import com.example.house_moving_service.modules.scheduling.enums.IncidentStatus;
import com.example.house_moving_service.modules.scheduling.mapper.IncidentMapper;
import com.example.house_moving_service.modules.scheduling.repository.IncidentRepository;
import com.example.house_moving_service.modules.scheduling.repository.MovingScheduleRepository;
import com.example.house_moving_service.modules.scheduling.repository.specification.IncidentSpecification;
import com.example.house_moving_service.modules.scheduling.service.ManagementIncidentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;

@Service
@Slf4j
@RequiredArgsConstructor
public class ManagementIncidentServiceImpl implements ManagementIncidentService {

    static final Sort DEFAULT_SORT = Sort.by(Sort.Order.desc("reportedAt"), Sort.Order.desc("id"));

    private final IncidentRepository incidentRepository;
    private final MovingScheduleRepository movingScheduleRepository;
    private final BookingWorkflowService bookingWorkflowService;
    private final UserLookupService userLookupService;
    private final IncidentMapper incidentMapper;
    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<IncidentResponse> search(IncidentFilterRequest filter, Pageable pageable) {
        Pageable sorted = pageable.getSort().isSorted()
                ? pageable
                : PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), DEFAULT_SORT);
        return PageResponse.from(incidentRepository
                .findAll(IncidentSpecification.filterIncidents(filter), sorted)
                .map(incidentMapper::toResponse));
    }

    @Override
    @Transactional(readOnly = true)
    public IncidentResponse getDetail(Integer id) {
        return incidentMapper.toResponse(incidentRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.ERR_INCIDENT_NOT_FOUND, id)));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public IncidentResponse record(Integer bookingId, ReportIncidentRequest request, Long userId) {
        BookingSnapshot booking = bookingWorkflowService.getSnapshot(bookingId);
        if (booking.status() == BookingStatus.CANCELLED) {
            throw new AppException(ErrorCode.ERR_INCIDENT_BOOKING_INVALID_STATUS);
        }
        MovingSchedule schedule = null;
        if (request.getScheduleId() != null) {
            schedule = movingScheduleRepository.findById(request.getScheduleId())
                    .orElseThrow(() -> new AppException(ErrorCode.ERR_MOVING_SCHEDULE_NOT_FOUND, request.getScheduleId()));
            if (!schedule.getBooking().getId().equals(bookingId)) {
                throw new AppException(ErrorCode.ERR_INCIDENT_SCHEDULE_INVALID, "chuyến không thuộc đơn hàng");
            }
        }

        Incident incident = Incident.builder()
                .booking(bookingWorkflowService.getReference(bookingId))
                .schedule(schedule)
                .reportedBy(userLookupService.getUserReference(userId))
                .incidentType(request.getIncidentType())
                .description(request.getDescription().trim())
                .damageCost(request.getEstimatedDamageCost() != null ? request.getEstimatedDamageCost() : BigDecimal.ZERO)
                .status(IncidentStatus.REPORTED)
                .reportedAt(LocalDateTime.now(clock))
                .build();
        Incident saved = incidentRepository.save(incident);

        log.info("Incident id: {} ({}) recorded for booking id: {} by user id: {}",
                saved.getId(), saved.getIncidentType(), bookingId, userId);
        return incidentMapper.toResponse(saved);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public IncidentResponse investigate(Integer id, MovingNoteRequest request, Long userId) {
        Incident incident = loadForUpdate(id);
        if (incident.getStatus() != IncidentStatus.REPORTED) {
            throw new AppException(ErrorCode.ERR_INCIDENT_INVALID_STATUS);
        }
        incident.setStatus(IncidentStatus.INVESTIGATING);
        if (request != null && request.getNote() != null && !request.getNote().isBlank()) {
            incident.setResolutionNote(request.getNote().trim());
        }
        Incident saved = incidentRepository.save(incident);

        log.info("Incident id: {} under investigation by user id: {}", id, userId);
        return incidentMapper.toResponse(saved);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public IncidentResponse resolve(Integer id, ResolveIncidentRequest request, Long userId) {
        Incident incident = loadOpenForUpdate(id);
        incident.setDamageCost(request.getDamageCost());
        close(incident, IncidentStatus.RESOLVED, request.getResolutionNote(), userId);
        Incident saved = incidentRepository.save(incident);

        log.info("Incident id: {} resolved by user id: {} with damage cost {}", id, userId, request.getDamageCost());
        return incidentMapper.toResponse(saved);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public IncidentResponse reject(Integer id, RejectIncidentRequest request, Long userId) {
        Incident incident = loadOpenForUpdate(id);
        close(incident, IncidentStatus.REJECTED, request.getResolutionNote(), userId);
        Incident saved = incidentRepository.save(incident);

        log.info("Incident id: {} rejected by user id: {}", id, userId);
        return incidentMapper.toResponse(saved);
    }

    private Incident loadForUpdate(Integer id) {
        return incidentRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new AppException(ErrorCode.ERR_INCIDENT_NOT_FOUND, id));
    }

    private Incident loadOpenForUpdate(Integer id) {
        Incident incident = loadForUpdate(id);
        if (!IncidentStatus.OPEN.contains(incident.getStatus())) {
            throw new AppException(ErrorCode.ERR_INCIDENT_INVALID_STATUS);
        }
        return incident;
    }

    private void close(Incident incident, IncidentStatus status, String resolutionNote, Long userId) {
        incident.setStatus(status);
        incident.setResolutionNote(resolutionNote.trim());
        incident.setResolvedAt(LocalDateTime.now(clock));
        incident.setResolvedBy(userLookupService.getUserReference(userId));
    }
}
