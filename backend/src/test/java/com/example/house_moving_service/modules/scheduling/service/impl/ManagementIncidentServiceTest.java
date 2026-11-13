package com.example.house_moving_service.modules.scheduling.service.impl;

import com.example.house_moving_service.common.exception.AppException;
import com.example.house_moving_service.common.exception.ErrorCode;
import com.example.house_moving_service.modules.booking.dto.response.BookingSnapshot;
import com.example.house_moving_service.modules.booking.entity.Booking;
import com.example.house_moving_service.modules.booking.enums.BookingStatus;
import com.example.house_moving_service.modules.booking.service.BookingWorkflowService;
import com.example.house_moving_service.modules.identity.entity.User;
import com.example.house_moving_service.modules.identity.service.UserLookupService;
import com.example.house_moving_service.modules.scheduling.dto.request.MovingNoteRequest;
import com.example.house_moving_service.modules.scheduling.dto.request.RejectIncidentRequest;
import com.example.house_moving_service.modules.scheduling.dto.request.ReportIncidentRequest;
import com.example.house_moving_service.modules.scheduling.dto.request.ResolveIncidentRequest;
import com.example.house_moving_service.modules.scheduling.dto.response.IncidentResponse;
import com.example.house_moving_service.modules.scheduling.entity.Incident;
import com.example.house_moving_service.modules.scheduling.entity.MovingSchedule;
import com.example.house_moving_service.modules.scheduling.enums.IncidentStatus;
import com.example.house_moving_service.modules.scheduling.enums.IncidentType;
import com.example.house_moving_service.modules.scheduling.enums.MovingScheduleStatus;
import com.example.house_moving_service.modules.scheduling.mapper.IncidentMapper;
import com.example.house_moving_service.modules.scheduling.repository.IncidentRepository;
import com.example.house_moving_service.modules.scheduling.repository.MovingScheduleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
class ManagementIncidentServiceTest {

    private static final long DISPATCHER_ID = 2L;
    private static final int BOOKING_ID = 7;
    private static final int INCIDENT_ID = 30;
    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 3, 10, 0);
    private static final Clock CLOCK = Clock.fixed(NOW.atZone(ZONE).toInstant(), ZONE);

    @Mock
    private IncidentRepository incidentRepository;
    @Mock
    private MovingScheduleRepository movingScheduleRepository;
    @Mock
    private BookingWorkflowService bookingWorkflowService;
    @Mock
    private UserLookupService userLookupService;

    private ManagementIncidentServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ManagementIncidentServiceImpl(incidentRepository, movingScheduleRepository, bookingWorkflowService,
                userLookupService, new IncidentMapper(), CLOCK);
    }

    // ---------- fixtures ----------

    private static Booking booking(int id) {
        return Booking.builder().id(id).bookingCode("BK-000" + id).build();
    }

    private static Incident incident(IncidentStatus status) {
        return Incident.builder().id(INCIDENT_ID).booking(booking(BOOKING_ID))
                .incidentType(IncidentType.ITEM_LOST).description("Mất hộp").damageCost(BigDecimal.ZERO)
                .status(status).reportedAt(NOW.minusDays(1)).build();
    }

    private void givenLocked(Incident incident) {
        when(incidentRepository.findByIdForUpdate(INCIDENT_ID)).thenReturn(Optional.of(incident));
    }

    private void givenBooking(BookingStatus status) {
        when(bookingWorkflowService.getSnapshot(BOOKING_ID))
                .thenReturn(BookingSnapshot.builder().id(BOOKING_ID).bookingCode("BK-0007").status(status).build());
    }

    private void givenSaveReturnsArgument() {
        when(incidentRepository.save(any(Incident.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private void givenDispatcherReference() {
        when(userLookupService.getUserReference(DISPATCHER_ID)).thenReturn(User.builder().id(DISPATCHER_ID).build());
    }

    private static ReportIncidentRequest request(Integer scheduleId) {
        return ReportIncidentRequest.builder().scheduleId(scheduleId)
                .incidentType(IncidentType.CUSTOMER_ISSUE).description("Khách phản ánh trầy tường").build();
    }

    private static void assertError(Runnable call, ErrorCode expected) {
        assertThatThrownBy(call::run)
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", expected);
    }

    // ---------- record ----------

    @Test
    @DisplayName("record: đơn hợp lệ, không có scheduleId → REPORTED, không gắn chuyến")
    void testRecord_WithoutSchedule() {
        givenBooking(BookingStatus.COMPLETED);
        when(bookingWorkflowService.getReference(BOOKING_ID)).thenReturn(booking(BOOKING_ID));
        givenDispatcherReference();
        givenSaveReturnsArgument();

        IncidentResponse response = service.record(BOOKING_ID, request(null), DISPATCHER_ID);

        assertThat(response.status()).isEqualTo(IncidentStatus.REPORTED);
        assertThat(response.scheduleId()).isNull();
        assertThat(response.reportedBy()).isEqualTo(DISPATCHER_ID);
        assertThat(response.reportedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("record: đơn CANCELLED → 8105; chuyến thuộc đơn khác → 8104")
    void testRecord_Invalid() {
        givenBooking(BookingStatus.CANCELLED);
        assertError(() -> service.record(BOOKING_ID, request(null), DISPATCHER_ID), ErrorCode.ERR_INCIDENT_BOOKING_INVALID_STATUS);

        givenBooking(BookingStatus.IN_PROGRESS);
        MovingSchedule otherBooking = MovingSchedule.builder().id(50).booking(booking(99))
                .status(MovingScheduleStatus.IN_PROGRESS).build();
        when(movingScheduleRepository.findById(50)).thenReturn(Optional.of(otherBooking));
        assertError(() -> service.record(BOOKING_ID, request(50), DISPATCHER_ID), ErrorCode.ERR_INCIDENT_SCHEDULE_INVALID);

        verify(incidentRepository, never()).save(any());
    }

    // ---------- state machine ----------

    @Test
    @DisplayName("investigate: REPORTED → INVESTIGATING, ghi chú lưu tạm; đã INVESTIGATING → 8102")
    void testInvestigate() {
        Incident incident = incident(IncidentStatus.REPORTED);
        givenLocked(incident);
        givenSaveReturnsArgument();

        IncidentResponse response = service.investigate(INCIDENT_ID, new MovingNoteRequest("Liên hệ khách"), DISPATCHER_ID);

        assertThat(response.status()).isEqualTo(IncidentStatus.INVESTIGATING);
        assertThat(response.resolutionNote()).isEqualTo("Liên hệ khách");
        assertError(() -> service.investigate(INCIDENT_ID, null, DISPATCHER_ID), ErrorCode.ERR_INCIDENT_INVALID_STATUS);
    }

    @Test
    @DisplayName("resolve: INVESTIGATING → RESOLVED, ghi chi phí, resolved_at, resolved_by")
    void testResolve() {
        givenLocked(incident(IncidentStatus.INVESTIGATING));
        givenDispatcherReference();
        givenSaveReturnsArgument();

        IncidentResponse response = service.resolve(INCIDENT_ID,
                new ResolveIncidentRequest(" Đền bù hộp mới ", new BigDecimal("350000")), DISPATCHER_ID);

        assertThat(response.status()).isEqualTo(IncidentStatus.RESOLVED);
        assertThat(response.damageCost()).isEqualByComparingTo("350000");
        assertThat(response.resolutionNote()).isEqualTo("Đền bù hộp mới");
        assertThat(response.resolvedAt()).isEqualTo(NOW);
        assertThat(response.resolvedBy()).isEqualTo(DISPATCHER_ID);
    }

    @Test
    @DisplayName("reject: REPORTED → REJECTED; sự cố đã đóng → 8102; không tồn tại → 8101")
    void testReject() {
        givenLocked(incident(IncidentStatus.REPORTED));
        givenDispatcherReference();
        givenSaveReturnsArgument();

        IncidentResponse response = service.reject(INCIDENT_ID, new RejectIncidentRequest("Không có bằng chứng"), DISPATCHER_ID);
        assertThat(response.status()).isEqualTo(IncidentStatus.REJECTED);
        assertThat(response.resolvedBy()).isEqualTo(DISPATCHER_ID);

        givenLocked(incident(IncidentStatus.RESOLVED));
        assertError(() -> service.reject(INCIDENT_ID, new RejectIncidentRequest("x"), DISPATCHER_ID),
                ErrorCode.ERR_INCIDENT_INVALID_STATUS);
        assertError(() -> service.resolve(INCIDENT_ID, new ResolveIncidentRequest("x", BigDecimal.ONE), DISPATCHER_ID),
                ErrorCode.ERR_INCIDENT_INVALID_STATUS);

        when(incidentRepository.findByIdForUpdate(99)).thenReturn(Optional.empty());
        assertError(() -> service.reject(99, new RejectIncidentRequest("x"), DISPATCHER_ID), ErrorCode.ERR_INCIDENT_NOT_FOUND);
    }
}
