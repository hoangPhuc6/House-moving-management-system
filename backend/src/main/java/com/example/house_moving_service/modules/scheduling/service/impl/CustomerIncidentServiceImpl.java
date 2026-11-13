package com.example.house_moving_service.modules.scheduling.service.impl;

import com.example.house_moving_service.common.exception.AppException;
import com.example.house_moving_service.common.exception.ErrorCode;
import com.example.house_moving_service.modules.booking.dto.response.BookingSnapshot;
import com.example.house_moving_service.modules.booking.enums.BookingStatus;
import com.example.house_moving_service.modules.booking.service.BookingWorkflowService;
import com.example.house_moving_service.modules.identity.service.UserLookupService;
import com.example.house_moving_service.modules.scheduling.config.MovingProperties;
import com.example.house_moving_service.modules.scheduling.dto.request.CustomerReportIncidentRequest;
import com.example.house_moving_service.modules.scheduling.dto.response.CustomerIncidentResponse;
import com.example.house_moving_service.modules.scheduling.entity.Incident;
import com.example.house_moving_service.modules.scheduling.entity.MovingSchedule;
import com.example.house_moving_service.modules.scheduling.enums.IncidentStatus;
import com.example.house_moving_service.modules.scheduling.enums.MovingScheduleStatus;
import com.example.house_moving_service.modules.scheduling.mapper.IncidentMapper;
import com.example.house_moving_service.modules.scheduling.repository.IncidentRepository;
import com.example.house_moving_service.modules.scheduling.repository.MovingScheduleRepository;
import com.example.house_moving_service.modules.scheduling.service.CustomerIncidentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class CustomerIncidentServiceImpl implements CustomerIncidentService {

    private final IncidentRepository incidentRepository;
    private final MovingScheduleRepository movingScheduleRepository;
    private final BookingWorkflowService bookingWorkflowService;
    private final UserLookupService userLookupService;
    private final IncidentMapper incidentMapper;
    private final MovingProperties movingProperties;
    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    public List<CustomerIncidentResponse> getIncidents(Integer bookingId, Long customerUserId) {
        requireOwner(bookingId, customerUserId);
        return incidentRepository.findAllByBookingIdOrderByReportedAtDescIdDesc(bookingId).stream()
                .filter(i -> i.getStatus() != IncidentStatus.REJECTED)
                .map(incidentMapper::toCustomerResponse)
                .toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CustomerIncidentResponse report(Integer bookingId, CustomerReportIncidentRequest request, Long customerUserId) {
        BookingSnapshot booking = requireOwner(bookingId, customerUserId);
        LocalDateTime now = LocalDateTime.now(clock);

        switch (booking.status()) {
            case CANCELLED -> throw new AppException(ErrorCode.ERR_INCIDENT_BOOKING_INVALID_STATUS);
            case IN_PROGRESS -> { /* đang vận chuyển: luôn được báo */ }
            case COMPLETED -> requireWithinWindow(bookingId, now);
            default -> throw new AppException(ErrorCode.ERR_INCIDENT_CUSTOMER_REPORT_NOT_ALLOWED,
                    "đơn hàng chưa bắt đầu vận chuyển");
        }

        MovingSchedule schedule = null;
        if (request.getScheduleId() != null) {
            schedule = movingScheduleRepository.findById(request.getScheduleId())
                    .filter(s -> s.getBooking().getId().equals(bookingId))
                    .filter(s -> s.getStatus() != MovingScheduleStatus.CANCELLED)
                    .orElseThrow(() -> new AppException(ErrorCode.ERR_INCIDENT_SCHEDULE_INVALID,
                            "chuyến không tồn tại hoặc không thuộc đơn hàng"));
        }

        // Luôn REPORTED, chi phí 0: điều phối viên phải xác minh trước khi giải quyết / bác bỏ
        Incident incident = Incident.builder()
                .booking(bookingWorkflowService.getReference(bookingId))
                .schedule(schedule)
                .reportedBy(userLookupService.getUserReference(customerUserId))
                .incidentType(request.getIncidentType())
                .description(request.getDescription().trim())
                .damageCost(BigDecimal.ZERO)
                .status(IncidentStatus.REPORTED)
                .reportedAt(now)
                .build();
        Incident saved = incidentRepository.save(incident);

        log.info("Incident id: {} ({}) reported by customer user id: {} for booking id: {}",
                saved.getId(), saved.getIncidentType(), customerUserId, bookingId);
        return incidentMapper.toCustomerResponse(saved);
    }

    private BookingSnapshot requireOwner(Integer bookingId, Long customerUserId) {
        BookingSnapshot booking = bookingWorkflowService.getSnapshot(bookingId);
        if (booking.customerUserId() == null || !booking.customerUserId().equals(customerUserId)) {
            throw new AppException(ErrorCode.ERR_INCIDENT_FORBIDDEN);
        }
        return booking;
    }

    // Đơn COMPLETED: chỉ được báo trong N ngày sau khi chuyến cuối cùng kết thúc
    private void requireWithinWindow(Integer bookingId, LocalDateTime now) {
        Optional<LocalDateTime> lastEnd = movingScheduleRepository.findAllByBookingIdOrderByScheduledStartAtAscIdAsc(bookingId)
                .stream()
                .map(MovingSchedule::getActualEndAt)
                .filter(Objects::nonNull)
                .max(Comparator.naturalOrder());
        // Không xác định được thời điểm kết thúc → không thể kiểm tra hạn, chặn luôn
        if (lastEnd.isEmpty()) {
            throw new AppException(ErrorCode.ERR_INCIDENT_CUSTOMER_REPORT_NOT_ALLOWED,
                    "không xác định được thời điểm hoàn tất vận chuyển");
        }
        if (now.isAfter(lastEnd.get().plusDays(movingProperties.getCustomerIncidentReportWindowDays()))) {
            throw new AppException(ErrorCode.ERR_INCIDENT_CUSTOMER_REPORT_NOT_ALLOWED,
                    "đã quá " + movingProperties.getCustomerIncidentReportWindowDays() + " ngày kể từ khi hoàn tất vận chuyển");
        }
    }
}
