package com.example.house_moving_service.modules.scheduling.service.impl;

import com.example.house_moving_service.common.exception.AppException;
import com.example.house_moving_service.common.exception.ErrorCode;
import com.example.house_moving_service.modules.booking.entity.Booking;
import com.example.house_moving_service.modules.identity.entity.Staff;
import com.example.house_moving_service.modules.identity.entity.User;
import com.example.house_moving_service.modules.identity.service.UserLookupService;
import com.example.house_moving_service.modules.scheduling.config.MovingProperties;
import com.example.house_moving_service.modules.scheduling.dto.request.ReportIncidentRequest;
import com.example.house_moving_service.modules.scheduling.dto.response.IncidentResponse;
import com.example.house_moving_service.modules.scheduling.entity.Incident;
import com.example.house_moving_service.modules.scheduling.entity.MovingSchedule;
import com.example.house_moving_service.modules.scheduling.entity.ScheduleStaffAssignment;
import com.example.house_moving_service.modules.scheduling.enums.AssignmentStatus;
import com.example.house_moving_service.modules.scheduling.enums.IncidentStatus;
import com.example.house_moving_service.modules.scheduling.enums.IncidentType;
import com.example.house_moving_service.modules.scheduling.enums.MovingScheduleStatus;
import com.example.house_moving_service.modules.scheduling.enums.StaffAssignmentRole;
import com.example.house_moving_service.modules.scheduling.mapper.IncidentMapper;
import com.example.house_moving_service.modules.scheduling.repository.IncidentRepository;
import com.example.house_moving_service.modules.scheduling.repository.MovingScheduleRepository;
import com.example.house_moving_service.modules.scheduling.repository.ScheduleStaffAssignmentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FieldIncidentServiceTest {

    private static final long MOVER_ID = 9L;
    private static final int SCHEDULE_ID = 50;
    private static final int BOOKING_ID = 7;
    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 2, 15, 0);
    private static final Clock CLOCK = Clock.fixed(NOW.atZone(ZONE).toInstant(), ZONE);

    @Mock
    private IncidentRepository incidentRepository;
    @Mock
    private MovingScheduleRepository movingScheduleRepository;
    @Mock
    private ScheduleStaffAssignmentRepository staffAssignmentRepository;
    @Mock
    private UserLookupService userLookupService;

    private FieldIncidentServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new FieldIncidentServiceImpl(incidentRepository, movingScheduleRepository, staffAssignmentRepository,
                userLookupService, new IncidentMapper(), new MovingProperties(), CLOCK);
    }

    // ---------- fixtures ----------

    private static MovingSchedule schedule(MovingScheduleStatus status) {
        return MovingSchedule.builder()
                .id(SCHEDULE_ID).booking(Booking.builder().id(BOOKING_ID).bookingCode("BK-0007").build())
                .scheduledStartAt(NOW.minusHours(6)).scheduledEndAt(NOW.minusHours(2)).status(status).build();
    }

    private void givenSchedule(MovingSchedule schedule) {
        when(movingScheduleRepository.findById(SCHEDULE_ID)).thenReturn(Optional.of(schedule));
    }

    private void givenAssigned(StaffAssignmentRole role, AssignmentStatus status) {
        when(staffAssignmentRepository.findByMovingScheduleIdAndStaffId(SCHEDULE_ID, MOVER_ID))
                .thenReturn(Optional.of(ScheduleStaffAssignment.builder()
                        .staff(Staff.builder().id(MOVER_ID).build()).role(role).status(status).build()));
    }

    private static ReportIncidentRequest request(BigDecimal estimated) {
        return ReportIncidentRequest.builder()
                .incidentType(IncidentType.ITEM_DAMAGED).description("  Vỡ chân bàn  ").estimatedDamageCost(estimated).build();
    }

    private static void assertError(Runnable call, ErrorCode expected) {
        assertThatThrownBy(call::run)
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", expected);
    }

    // ---------- report ----------

    @Test
    @DisplayName("report: MOVER (không phải SUPERVISOR) báo sự cố khi chuyến IN_PROGRESS → REPORTED, booking theo chuyến")
    void testReport_Success() {
        givenSchedule(schedule(MovingScheduleStatus.IN_PROGRESS));
        givenAssigned(StaffAssignmentRole.MOVER, AssignmentStatus.CONFIRMED);
        when(userLookupService.getUserReference(MOVER_ID)).thenReturn(User.builder().id(MOVER_ID).build());
        when(incidentRepository.save(any(Incident.class))).thenAnswer(inv -> inv.getArgument(0));

        IncidentResponse response = service.report(SCHEDULE_ID, request(null), MOVER_ID);

        ArgumentCaptor<Incident> captor = ArgumentCaptor.forClass(Incident.class);
        verify(incidentRepository).save(captor.capture());
        Incident saved = captor.getValue();
        assertThat(saved.getStatus()).isEqualTo(IncidentStatus.REPORTED);
        assertThat(saved.getBooking().getId()).isEqualTo(BOOKING_ID);
        assertThat(saved.getDescription()).isEqualTo("Vỡ chân bàn");
        assertThat(saved.getDamageCost()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(saved.getReportedAt()).isEqualTo(NOW);
        assertThat(response.scheduleId()).isEqualTo(SCHEDULE_ID);
        assertThat(response.reportedBy()).isEqualTo(MOVER_ID);
    }

    @Test
    @DisplayName("report: chuyến PLANNED / CANCELLED / RESCHEDULED → 8104")
    void testReport_InvalidScheduleStatus() {
        for (MovingScheduleStatus status : new MovingScheduleStatus[]{
                MovingScheduleStatus.PLANNED, MovingScheduleStatus.CANCELLED, MovingScheduleStatus.RESCHEDULED}) {
            givenSchedule(schedule(status));
            givenAssigned(StaffAssignmentRole.SUPERVISOR, AssignmentStatus.CONFIRMED);

            assertError(() -> service.report(SCHEDULE_ID, request(null), MOVER_ID), ErrorCode.ERR_INCIDENT_SCHEDULE_INVALID);
        }
        verify(incidentRepository, never()).save(any());
    }

    @Test
    @DisplayName("report: chuyến COMPLETED trong 24 giờ thì được, quá 24 giờ → 8104")
    void testReport_CompletedWindow() {
        MovingSchedule recent = schedule(MovingScheduleStatus.COMPLETED);
        recent.setActualEndAt(NOW.minusHours(23));
        givenSchedule(recent);
        givenAssigned(StaffAssignmentRole.DRIVER, AssignmentStatus.COMPLETED);
        when(incidentRepository.save(any(Incident.class))).thenAnswer(inv -> inv.getArgument(0));

        assertThat(service.report(SCHEDULE_ID, request(new BigDecimal("500000")), MOVER_ID).damageCost())
                .isEqualByComparingTo("500000");

        MovingSchedule old = schedule(MovingScheduleStatus.COMPLETED);
        old.setActualEndAt(NOW.minusHours(25));
        givenSchedule(old);

        assertError(() -> service.report(SCHEDULE_ID, request(null), MOVER_ID), ErrorCode.ERR_INCIDENT_SCHEDULE_INVALID);
    }

    @Test
    @DisplayName("report: không được phân công / phân công đã hủy → 8003; chuyến không tồn tại → 8001")
    void testReport_NotAssigned() {
        givenSchedule(schedule(MovingScheduleStatus.IN_PROGRESS));
        when(staffAssignmentRepository.findByMovingScheduleIdAndStaffId(SCHEDULE_ID, MOVER_ID)).thenReturn(Optional.empty());
        assertError(() -> service.report(SCHEDULE_ID, request(null), MOVER_ID), ErrorCode.ERR_MOVING_FORBIDDEN);

        givenAssigned(StaffAssignmentRole.MOVER, AssignmentStatus.CANCELLED);
        assertError(() -> service.report(SCHEDULE_ID, request(null), MOVER_ID), ErrorCode.ERR_MOVING_FORBIDDEN);

        when(movingScheduleRepository.findById(99)).thenReturn(Optional.empty());
        assertError(() -> service.report(99, request(null), MOVER_ID), ErrorCode.ERR_MOVING_SCHEDULE_NOT_FOUND);
    }

    // ---------- read ----------

    @Test
    @DisplayName("getDetail: sự cố không gắn chuyến hoặc người gọi không thuộc chuyến → 8103")
    void testGetDetail_Forbidden() {
        Incident noSchedule = Incident.builder().id(1).booking(Booking.builder().id(BOOKING_ID).build()).build();
        when(incidentRepository.findById(1)).thenReturn(Optional.of(noSchedule));
        assertError(() -> service.getDetail(1, MOVER_ID), ErrorCode.ERR_INCIDENT_FORBIDDEN);

        Incident onSchedule = Incident.builder().id(2).booking(Booking.builder().id(BOOKING_ID).build())
                .schedule(schedule(MovingScheduleStatus.IN_PROGRESS)).build();
        when(incidentRepository.findById(2)).thenReturn(Optional.of(onSchedule));
        when(staffAssignmentRepository.findByMovingScheduleIdAndStaffId(SCHEDULE_ID, MOVER_ID)).thenReturn(Optional.empty());
        assertError(() -> service.getDetail(2, MOVER_ID), ErrorCode.ERR_INCIDENT_FORBIDDEN);

        when(incidentRepository.findById(3)).thenReturn(Optional.empty());
        assertError(() -> service.getDetail(3, MOVER_ID), ErrorCode.ERR_INCIDENT_NOT_FOUND);
    }
}
