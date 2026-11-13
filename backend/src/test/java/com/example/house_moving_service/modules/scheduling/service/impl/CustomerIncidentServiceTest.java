package com.example.house_moving_service.modules.scheduling.service.impl;

import com.example.house_moving_service.common.exception.AppException;
import com.example.house_moving_service.common.exception.ErrorCode;
import com.example.house_moving_service.modules.booking.dto.response.BookingSnapshot;
import com.example.house_moving_service.modules.booking.entity.Booking;
import com.example.house_moving_service.modules.booking.enums.BookingStatus;
import com.example.house_moving_service.modules.booking.service.BookingWorkflowService;
import com.example.house_moving_service.modules.identity.entity.User;
import com.example.house_moving_service.modules.identity.service.UserLookupService;
import com.example.house_moving_service.modules.scheduling.config.MovingProperties;
import com.example.house_moving_service.modules.scheduling.dto.request.CustomerReportIncidentRequest;
import com.example.house_moving_service.modules.scheduling.dto.response.CustomerIncidentResponse;
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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomerIncidentServiceTest {

    private static final long CUSTOMER_ID = 20L;
    private static final int BOOKING_ID = 7;
    private static final int SCHEDULE_ID = 50;
    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 10, 10, 0);
    private static final Clock CLOCK = Clock.fixed(NOW.atZone(ZONE).toInstant(), ZONE);

    @Mock
    private IncidentRepository incidentRepository;
    @Mock
    private MovingScheduleRepository movingScheduleRepository;
    @Mock
    private BookingWorkflowService bookingWorkflowService;
    @Mock
    private UserLookupService userLookupService;

    private CustomerIncidentServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new CustomerIncidentServiceImpl(incidentRepository, movingScheduleRepository, bookingWorkflowService,
                userLookupService, new IncidentMapper(), new MovingProperties(), CLOCK);
    }

    // ---------- fixtures ----------

    private void givenBooking(Long ownerId, BookingStatus status) {
        when(bookingWorkflowService.getSnapshot(BOOKING_ID)).thenReturn(BookingSnapshot.builder()
                .id(BOOKING_ID).status(status).customerUserId(ownerId).build());
    }

    private void givenBookingOwnedBy(Long ownerId) {
        givenBooking(ownerId, BookingStatus.IN_PROGRESS);
    }

    private static Incident incident(int id, IncidentStatus status, String resolutionNote) {
        return Incident.builder().id(id).booking(Booking.builder().id(BOOKING_ID).build())
                .incidentType(IncidentType.DELAY).description("Kẹt xe").status(status)
                .resolutionNote(resolutionNote).reportedAt(LocalDateTime.of(2026, 11, 2, 10, 0)).build();
    }

    private static MovingSchedule schedule(int bookingId, MovingScheduleStatus status, LocalDateTime actualEndAt) {
        return MovingSchedule.builder().id(SCHEDULE_ID).booking(Booking.builder().id(bookingId).build())
                .status(status).actualEndAt(actualEndAt).build();
    }

    private static CustomerReportIncidentRequest request(Integer scheduleId) {
        return CustomerReportIncidentRequest.builder().scheduleId(scheduleId)
                .incidentType(IncidentType.PROPERTY_DAMAGED).description("  Trầy tường phòng khách  ").build();
    }

    private void givenSaveSucceeds() {
        when(bookingWorkflowService.getReference(BOOKING_ID)).thenReturn(Booking.builder().id(BOOKING_ID).build());
        when(userLookupService.getUserReference(CUSTOMER_ID)).thenReturn(User.builder().id(CUSTOMER_ID).build());
        when(incidentRepository.save(any(Incident.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private static void assertError(Runnable call, ErrorCode expected) {
        assertThatThrownBy(call::run)
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", expected);
    }

    // ---------- getIncidents ----------

    @Test
    @DisplayName("Không phải chủ đơn → 8103")
    void testForbidden() {
        givenBookingOwnedBy(99L);

        assertError(() -> service.getIncidents(BOOKING_ID, CUSTOMER_ID), ErrorCode.ERR_INCIDENT_FORBIDDEN);
    }

    @Test
    @DisplayName("Ẩn sự cố REJECTED; ghi chú điều tra không trả, kết quả xử lý chỉ trả khi RESOLVED")
    void testHidesInternalData() {
        givenBookingOwnedBy(CUSTOMER_ID);
        when(incidentRepository.findAllByBookingIdOrderByReportedAtDescIdDesc(BOOKING_ID)).thenReturn(List.of(
                incident(3, IncidentStatus.RESOLVED, "Đã đền bù"),
                incident(2, IncidentStatus.INVESTIGATING, "ghi chú nội bộ"),
                incident(1, IncidentStatus.REJECTED, "Không hợp lệ")));

        List<CustomerIncidentResponse> result = service.getIncidents(BOOKING_ID, CUSTOMER_ID);

        assertThat(result).extracting("id").containsExactly(3, 2);
        assertThat(result.get(0).resolutionNote()).isEqualTo("Đã đền bù");
        assertThat(result.get(1).resolutionNote()).isNull();
    }

    // ---------- report ----------

    @Test
    @DisplayName("report: đơn IN_PROGRESS, có scheduleId → REPORTED, chi phí 0, người báo là khách")
    void testReport_Success() {
        givenBooking(CUSTOMER_ID, BookingStatus.IN_PROGRESS);
        when(movingScheduleRepository.findById(SCHEDULE_ID))
                .thenReturn(Optional.of(schedule(BOOKING_ID, MovingScheduleStatus.IN_PROGRESS, null)));
        givenSaveSucceeds();

        CustomerIncidentResponse response = service.report(BOOKING_ID, request(SCHEDULE_ID), CUSTOMER_ID);

        ArgumentCaptor<Incident> captor = ArgumentCaptor.forClass(Incident.class);
        verify(incidentRepository).save(captor.capture());
        Incident saved = captor.getValue();
        assertThat(saved.getStatus()).isEqualTo(IncidentStatus.REPORTED);
        assertThat(saved.getDamageCost()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(saved.getReportedBy().getId()).isEqualTo(CUSTOMER_ID);
        assertThat(saved.getDescription()).isEqualTo("Trầy tường phòng khách");
        assertThat(saved.getReportedAt()).isEqualTo(NOW);
        assertThat(response.status()).isEqualTo(IncidentStatus.REPORTED);
        assertThat(response.scheduleId()).isEqualTo(SCHEDULE_ID);
    }

    @Test
    @DisplayName("report: đơn COMPLETED trong 7 ngày thì được, quá 7 ngày → 8106")
    void testReport_CompletedWindow() {
        givenBooking(CUSTOMER_ID, BookingStatus.COMPLETED);
        when(movingScheduleRepository.findAllByBookingIdOrderByScheduledStartAtAscIdAsc(BOOKING_ID)).thenReturn(List.of(
                schedule(BOOKING_ID, MovingScheduleStatus.COMPLETED, NOW.minusDays(9)),
                schedule(BOOKING_ID, MovingScheduleStatus.COMPLETED, NOW.minusDays(6))));
        givenSaveSucceeds();

        assertThat(service.report(BOOKING_ID, request(null), CUSTOMER_ID).status()).isEqualTo(IncidentStatus.REPORTED);

        when(movingScheduleRepository.findAllByBookingIdOrderByScheduledStartAtAscIdAsc(BOOKING_ID)).thenReturn(List.of(
                schedule(BOOKING_ID, MovingScheduleStatus.COMPLETED, NOW.minusDays(8))));
        assertError(() -> service.report(BOOKING_ID, request(null), CUSTOMER_ID),
                ErrorCode.ERR_INCIDENT_CUSTOMER_REPORT_NOT_ALLOWED);

        // Không chuyến nào có actualEndAt → chặn luôn
        when(movingScheduleRepository.findAllByBookingIdOrderByScheduledStartAtAscIdAsc(BOOKING_ID)).thenReturn(List.of(
                schedule(BOOKING_ID, MovingScheduleStatus.COMPLETED, null)));
        assertError(() -> service.report(BOOKING_ID, request(null), CUSTOMER_ID),
                ErrorCode.ERR_INCIDENT_CUSTOMER_REPORT_NOT_ALLOWED);
    }

    @Test
    @DisplayName("report: không phải chủ đơn → 8103; CANCELLED → 8105; chưa vận chuyển → 8106")
    void testReport_InvalidBooking() {
        givenBooking(99L, BookingStatus.IN_PROGRESS);
        assertError(() -> service.report(BOOKING_ID, request(null), CUSTOMER_ID), ErrorCode.ERR_INCIDENT_FORBIDDEN);

        givenBooking(CUSTOMER_ID, BookingStatus.CANCELLED);
        assertError(() -> service.report(BOOKING_ID, request(null), CUSTOMER_ID),
                ErrorCode.ERR_INCIDENT_BOOKING_INVALID_STATUS);

        for (BookingStatus status : new BookingStatus[]{BookingStatus.PENDING_SURVEY, BookingStatus.CONFIRMED}) {
            givenBooking(CUSTOMER_ID, status);
            assertError(() -> service.report(BOOKING_ID, request(null), CUSTOMER_ID),
                    ErrorCode.ERR_INCIDENT_CUSTOMER_REPORT_NOT_ALLOWED);
        }
        verify(incidentRepository, never()).save(any());
    }

    @Test
    @DisplayName("report: chuyến thuộc đơn khác / đã CANCELLED / không tồn tại → 8104")
    void testReport_InvalidSchedule() {
        givenBooking(CUSTOMER_ID, BookingStatus.IN_PROGRESS);

        when(movingScheduleRepository.findById(SCHEDULE_ID))
                .thenReturn(Optional.of(schedule(99, MovingScheduleStatus.IN_PROGRESS, null)));
        assertError(() -> service.report(BOOKING_ID, request(SCHEDULE_ID), CUSTOMER_ID),
                ErrorCode.ERR_INCIDENT_SCHEDULE_INVALID);

        when(movingScheduleRepository.findById(SCHEDULE_ID))
                .thenReturn(Optional.of(schedule(BOOKING_ID, MovingScheduleStatus.CANCELLED, null)));
        assertError(() -> service.report(BOOKING_ID, request(SCHEDULE_ID), CUSTOMER_ID),
                ErrorCode.ERR_INCIDENT_SCHEDULE_INVALID);

        when(movingScheduleRepository.findById(SCHEDULE_ID)).thenReturn(Optional.empty());
        assertError(() -> service.report(BOOKING_ID, request(SCHEDULE_ID), CUSTOMER_ID),
                ErrorCode.ERR_INCIDENT_SCHEDULE_INVALID);

        verify(incidentRepository, never()).save(any());
    }
}
