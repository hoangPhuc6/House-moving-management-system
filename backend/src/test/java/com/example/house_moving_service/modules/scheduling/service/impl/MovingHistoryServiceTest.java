package com.example.house_moving_service.modules.scheduling.service.impl;

import com.example.house_moving_service.common.exception.AppException;
import com.example.house_moving_service.common.exception.ErrorCode;
import com.example.house_moving_service.modules.booking.dto.response.BookingSnapshot;
import com.example.house_moving_service.modules.booking.entity.Booking;
import com.example.house_moving_service.modules.booking.enums.BookingStatus;
import com.example.house_moving_service.modules.booking.service.BookingWorkflowService;
import com.example.house_moving_service.modules.identity.entity.Staff;
import com.example.house_moving_service.modules.identity.entity.User;
import com.example.house_moving_service.modules.scheduling.dto.response.CustomerMovingHistoryResponse;
import com.example.house_moving_service.modules.scheduling.dto.response.CustomerMovingTripResponse;
import com.example.house_moving_service.modules.scheduling.dto.response.ManagementMovingHistoryResponse;
import com.example.house_moving_service.modules.scheduling.dto.response.MovingTimelineEntry;
import com.example.house_moving_service.modules.scheduling.dto.response.SupervisorContact;
import com.example.house_moving_service.modules.scheduling.entity.MovingSchedule;
import com.example.house_moving_service.modules.scheduling.entity.ScheduleStaffAssignment;
import com.example.house_moving_service.modules.scheduling.entity.ScheduleVehicleAssignment;
import com.example.house_moving_service.modules.scheduling.entity.Vehicle;
import com.example.house_moving_service.modules.scheduling.enums.AssignmentStatus;
import com.example.house_moving_service.modules.scheduling.enums.MovingScheduleStatus;
import com.example.house_moving_service.modules.scheduling.enums.StaffAssignmentRole;
import com.example.house_moving_service.modules.scheduling.enums.VehicleStatus;
import com.example.house_moving_service.modules.scheduling.mapper.MovingScheduleMapper;
import com.example.house_moving_service.modules.scheduling.repository.MovingScheduleRepository;
import com.example.house_moving_service.modules.scheduling.repository.ScheduleStaffAssignmentRepository;
import com.example.house_moving_service.modules.scheduling.repository.ScheduleVehicleAssignmentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MovingHistoryServiceTest {

    private static final long CUSTOMER_ID = 20L;
    private static final int BOOKING_ID = 7;
    private static final LocalDateTime T0 = LocalDateTime.of(2026, 11, 2, 8, 0);

    @Mock
    private MovingScheduleRepository movingScheduleRepository;
    @Mock
    private ScheduleStaffAssignmentRepository staffAssignmentRepository;
    @Mock
    private ScheduleVehicleAssignmentRepository vehicleAssignmentRepository;
    @Mock
    private BookingWorkflowService bookingWorkflowService;

    private MovingHistoryServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new MovingHistoryServiceImpl(movingScheduleRepository, staffAssignmentRepository, vehicleAssignmentRepository,
                bookingWorkflowService, new MovingScheduleMapper());
    }

    private static MovingSchedule schedule(int id, MovingScheduleStatus status) {
        return MovingSchedule.builder()
                .id(id).booking(Booking.builder().id(BOOKING_ID).build())
                .scheduledStartAt(T0).scheduledEndAt(T0.plusHours(4)).status(status).build();
    }

    private static ScheduleStaffAssignment staff(MovingSchedule s, long userId, StaffAssignmentRole role, AssignmentStatus status) {
        Staff staff = Staff.builder().id(userId).fullName("NV " + userId)
                .user(User.builder().id(userId).phoneNumber("090" + userId).build()).build();
        return ScheduleStaffAssignment.builder().id((int) userId).movingSchedule(s).staff(staff).role(role).status(status).build();
    }

    private static ScheduleVehicleAssignment vehicle(MovingSchedule s, int vehicleId, AssignmentStatus status) {
        return ScheduleVehicleAssignment.builder().id(vehicleId).movingSchedule(s).status(status)
                .vehicle(Vehicle.builder().id(vehicleId).licensePlate("51C-" + vehicleId).status(VehicleStatus.IN_USE).build())
                .build();
    }

    private void givenBookingOwnedBy(Long ownerId) {
        when(bookingWorkflowService.getSnapshot(BOOKING_ID)).thenReturn(BookingSnapshot.builder()
                .id(BOOKING_ID).bookingCode("BK-0007").status(BookingStatus.IN_PROGRESS).customerUserId(ownerId).build());
    }

    private static void assertError(Runnable call, ErrorCode expected) {
        assertThatThrownBy(call::run)
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", expected);
    }

    @Test
    @DisplayName("Khách xem đơn của người khác → 4004")
    void testCustomer_Forbidden() {
        givenBookingOwnedBy(99L);

        assertError(() -> service.getCustomerSchedules(BOOKING_ID, CUSTOMER_ID), ErrorCode.ERR_BOOKING_FORBIDDEN);
        assertError(() -> service.getCustomerHistory(BOOKING_ID, CUSTOMER_ID), ErrorCode.ERR_BOOKING_FORBIDDEN);
    }

    @Test
    @DisplayName("getCustomerSchedules: bỏ chuyến CANCELLED")
    void testCustomerSchedules_HidesCancelled() {
        givenBookingOwnedBy(CUSTOMER_ID);
        when(movingScheduleRepository.findAllByBookingIdOrderByScheduledStartAtAscIdAsc(BOOKING_ID))
                .thenReturn(List.of(schedule(1, MovingScheduleStatus.CANCELLED), schedule(2, MovingScheduleStatus.CONFIRMED)));

        assertThat(service.getCustomerSchedules(BOOKING_ID, CUSTOMER_ID)).extracting("id").containsExactly(2);
    }

    @Test
    @DisplayName("getCustomerSchedule: chuyến thuộc đơn khác / đã hủy → 8001")
    void testCustomerSchedule_NotVisible() {
        givenBookingOwnedBy(CUSTOMER_ID);
        MovingSchedule otherBooking = schedule(3, MovingScheduleStatus.CONFIRMED);
        otherBooking.setBooking(Booking.builder().id(99).build());
        when(movingScheduleRepository.findById(3)).thenReturn(Optional.of(otherBooking));
        when(movingScheduleRepository.findById(4)).thenReturn(Optional.of(schedule(4, MovingScheduleStatus.CANCELLED)));

        assertError(() -> service.getCustomerSchedule(BOOKING_ID, 3, CUSTOMER_ID), ErrorCode.ERR_MOVING_SCHEDULE_NOT_FOUND);
        assertError(() -> service.getCustomerSchedule(BOOKING_ID, 4, CUSTOMER_ID), ErrorCode.ERR_MOVING_SCHEDULE_NOT_FOUND);
    }

    @Test
    @DisplayName("getCustomerHistory: dòng thời gian chỉ tên mốc + thời điểm; biển số xe chưa hủy; liên hệ giám sát viên")
    void testCustomerHistory() {
        givenBookingOwnedBy(CUSTOMER_ID);
        MovingSchedule trip = schedule(1, MovingScheduleStatus.IN_PROGRESS);
        trip.setActualStartAt(T0.plusMinutes(15));
        trip.appendNote("START", 8L, "Bắt đầu chuyến", T0.plusMinutes(15));
        trip.appendNote("ARRIVED_PICKUP", 8L, "nội dung nội bộ", T0.plusMinutes(40));
        when(movingScheduleRepository.findAllByBookingIdOrderByScheduledStartAtAscIdAsc(BOOKING_ID)).thenReturn(List.of(trip));
        when(staffAssignmentRepository.findAllWithStaffByScheduleIds(List.of(1))).thenReturn(List.of(
                staff(trip, 8L, StaffAssignmentRole.SUPERVISOR, AssignmentStatus.CONFIRMED),
                staff(trip, 9L, StaffAssignmentRole.MOVER, AssignmentStatus.CONFIRMED)));
        when(vehicleAssignmentRepository.findAllWithVehicleByScheduleIds(List.of(1))).thenReturn(List.of(
                vehicle(trip, 1, AssignmentStatus.CONFIRMED), vehicle(trip, 2, AssignmentStatus.CANCELLED)));

        CustomerMovingHistoryResponse history = service.getCustomerHistory(BOOKING_ID, CUSTOMER_ID);

        assertThat(history.bookingCode()).isEqualTo("BK-0007");
        CustomerMovingTripResponse t = history.trips().getFirst();
        assertThat(t.delayMinutes()).isEqualTo(15L);
        assertThat(t.timeline()).containsExactly(
                new MovingTimelineEntry("START", T0.plusMinutes(15)),
                new MovingTimelineEntry("ARRIVED_PICKUP", T0.plusMinutes(40)));
        assertThat(t.licensePlates()).containsExactly("51C-1");
        assertThat(t.supervisor()).isEqualTo(new SupervisorContact("NV 8", "0908"));
    }

    @Test
    @DisplayName("getManagementHistory: mọi chuyến kể cả đã hủy, nhật ký gốc, mọi phân công")
    void testManagementHistory() {
        when(bookingWorkflowService.getSnapshot(BOOKING_ID)).thenReturn(BookingSnapshot.builder()
                .id(BOOKING_ID).bookingCode("BK-0007").status(BookingStatus.IN_PROGRESS).build());
        MovingSchedule cancelled = schedule(1, MovingScheduleStatus.CANCELLED);
        cancelled.appendNote("CANCEL", 2L, "khách dời", T0);
        MovingSchedule active = schedule(2, MovingScheduleStatus.CONFIRMED);
        when(movingScheduleRepository.findAllByBookingIdOrderByScheduledStartAtAscIdAsc(BOOKING_ID))
                .thenReturn(List.of(cancelled, active));
        when(staffAssignmentRepository.findAllWithStaffByScheduleIds(List.of(1, 2))).thenReturn(List.of(
                staff(cancelled, 8L, StaffAssignmentRole.SUPERVISOR, AssignmentStatus.CANCELLED),
                staff(active, 9L, StaffAssignmentRole.SUPERVISOR, AssignmentStatus.ASSIGNED)));
        when(vehicleAssignmentRepository.findAllWithVehicleByScheduleIds(List.of(1, 2))).thenReturn(List.of());

        ManagementMovingHistoryResponse history = service.getManagementHistory(BOOKING_ID);

        assertThat(history.trips()).extracting("id").containsExactly(1, 2);
        assertThat(history.trips().get(0).note()).contains("khách dời");
        assertThat(history.trips().get(0).staff()).extracting("userId").containsExactly(8L);
        assertThat(history.trips().get(1).staff()).extracting("userId").containsExactly(9L);
        assertThat(history.trips().get(1).vehicles()).isEmpty();
    }
}
