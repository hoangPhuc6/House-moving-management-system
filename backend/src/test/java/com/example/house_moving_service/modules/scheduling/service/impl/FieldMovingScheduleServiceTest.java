package com.example.house_moving_service.modules.scheduling.service.impl;

import com.example.house_moving_service.common.exception.AppException;
import com.example.house_moving_service.common.exception.ErrorCode;
import com.example.house_moving_service.modules.booking.dto.response.BookingSnapshot;
import com.example.house_moving_service.modules.booking.entity.Booking;
import com.example.house_moving_service.modules.booking.enums.BookingStatus;
import com.example.house_moving_service.modules.booking.service.BookingWorkflowService;
import com.example.house_moving_service.modules.identity.entity.Staff;
import com.example.house_moving_service.modules.scheduling.config.MovingProperties;
import com.example.house_moving_service.modules.scheduling.dto.request.MovingNoteRequest;
import com.example.house_moving_service.modules.scheduling.dto.request.MovingProgressRequest;
import com.example.house_moving_service.modules.scheduling.dto.response.FieldMovingScheduleResponse;
import com.example.house_moving_service.modules.scheduling.entity.MovingSchedule;
import com.example.house_moving_service.modules.scheduling.entity.ScheduleStaffAssignment;
import com.example.house_moving_service.modules.scheduling.entity.ScheduleVehicleAssignment;
import com.example.house_moving_service.modules.scheduling.entity.Vehicle;
import com.example.house_moving_service.modules.scheduling.enums.AssignmentStatus;
import com.example.house_moving_service.modules.scheduling.enums.MovingScheduleStatus;
import com.example.house_moving_service.modules.scheduling.enums.ProgressMilestone;
import com.example.house_moving_service.modules.scheduling.enums.StaffAssignmentRole;
import com.example.house_moving_service.modules.scheduling.enums.SurchargeStatus;
import com.example.house_moving_service.modules.scheduling.enums.VehicleStatus;
import com.example.house_moving_service.modules.scheduling.mapper.MovingScheduleMapper;
import com.example.house_moving_service.modules.scheduling.repository.BookingSurchargeRepository;
import com.example.house_moving_service.modules.scheduling.repository.MovingScheduleRepository;
import com.example.house_moving_service.modules.scheduling.repository.ScheduleStaffAssignmentRepository;
import com.example.house_moving_service.modules.scheduling.repository.ScheduleVehicleAssignmentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FieldMovingScheduleServiceTest {

    private static final long SUPERVISOR_ID = 8L;
    private static final long OTHER_ID = 9L;
    private static final int SCHEDULE_ID = 50;
    private static final int BOOKING_ID = 7;
    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 2);
    // "Bây giờ" = 09:00; giờ hẹn mặc định 09:30
    private static final LocalDateTime NOW = TODAY.atTime(9, 0);
    private static final Clock CLOCK = Clock.fixed(NOW.atZone(ZONE).toInstant(), ZONE);

    @Mock
    private MovingScheduleRepository movingScheduleRepository;
    @Mock
    private ScheduleStaffAssignmentRepository staffAssignmentRepository;
    @Mock
    private ScheduleVehicleAssignmentRepository vehicleAssignmentRepository;
    @Mock
    private BookingWorkflowService bookingWorkflowService;
    @Mock
    private BookingSurchargeRepository surchargeRepository;

    private FieldMovingScheduleServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new FieldMovingScheduleServiceImpl(movingScheduleRepository, staffAssignmentRepository,
                vehicleAssignmentRepository, bookingWorkflowService, surchargeRepository, new MovingScheduleMapper(),
                new MovingProperties(), CLOCK);
    }

    // ---------- fixtures ----------

    private static MovingSchedule schedule(MovingScheduleStatus status, LocalDateTime start) {
        return MovingSchedule.builder()
                .id(SCHEDULE_ID)
                .booking(Booking.builder().id(BOOKING_ID).build())
                .scheduledStartAt(start).scheduledEndAt(start.plusHours(4))
                .status(status)
                .build();
    }

    private static MovingSchedule schedule(MovingScheduleStatus status) {
        return schedule(status, TODAY.atTime(LocalTime.of(9, 30)));
    }

    private static ScheduleStaffAssignment assignment(long staffId, StaffAssignmentRole role, AssignmentStatus status) {
        return ScheduleStaffAssignment.builder()
                .id((int) staffId).staff(Staff.builder().id(staffId).fullName("NV " + staffId).build())
                .role(role).status(status).build();
    }

    private static ScheduleVehicleAssignment vehicleAssignment(int vehicleId, VehicleStatus vehicleStatus, AssignmentStatus status) {
        return ScheduleVehicleAssignment.builder()
                .id(vehicleId).status(status)
                .vehicle(Vehicle.builder().id(vehicleId).licensePlate("51C-" + vehicleId).status(vehicleStatus).build())
                .build();
    }

    private static BookingSnapshot booking(BookingStatus status) {
        return BookingSnapshot.builder().id(BOOKING_ID).bookingCode("BK-0007").status(status)
                .contactName("Khách").contactPhone("0900").build();
    }

    private void givenLocked(MovingSchedule schedule) {
        when(movingScheduleRepository.findByIdForUpdate(SCHEDULE_ID)).thenReturn(Optional.of(schedule));
    }

    private void givenAssignment(long userId, ScheduleStaffAssignment assignment) {
        when(staffAssignmentRepository.findByMovingScheduleIdAndStaffId(SCHEDULE_ID, userId))
                .thenReturn(Optional.ofNullable(assignment));
    }

    private void givenSupervisor() {
        givenAssignment(SUPERVISOR_ID, assignment(SUPERVISOR_ID, StaffAssignmentRole.SUPERVISOR, AssignmentStatus.CONFIRMED));
    }

    private static void assertError(Runnable call, ErrorCode expected) {
        assertThatThrownBy(call::run)
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", expected);
    }

    // ---------- getDetail ----------

    @Test
    @DisplayName("getDetail: không có chuyến → 8001; không được phân công / phân công đã hủy → 8003")
    void testGetDetail_NotFoundOrForbidden() {
        when(movingScheduleRepository.findById(SCHEDULE_ID)).thenReturn(Optional.empty());
        assertError(() -> service.getDetail(SCHEDULE_ID, SUPERVISOR_ID), ErrorCode.ERR_MOVING_SCHEDULE_NOT_FOUND);

        when(movingScheduleRepository.findById(SCHEDULE_ID)).thenReturn(Optional.of(schedule(MovingScheduleStatus.CONFIRMED)));
        givenAssignment(OTHER_ID, null);
        assertError(() -> service.getDetail(SCHEDULE_ID, OTHER_ID), ErrorCode.ERR_MOVING_FORBIDDEN);

        givenAssignment(OTHER_ID, assignment(OTHER_ID, StaffAssignmentRole.MOVER, AssignmentStatus.CANCELLED));
        assertError(() -> service.getDetail(SCHEDULE_ID, OTHER_ID), ErrorCode.ERR_MOVING_FORBIDDEN);
    }

    @Test
    @DisplayName("getDetail: MOVER được phân công xem được; chỉ trả nhân viên / xe chưa hủy")
    void testGetDetail_Mover() {
        when(movingScheduleRepository.findById(SCHEDULE_ID)).thenReturn(Optional.of(schedule(MovingScheduleStatus.CONFIRMED)));
        givenAssignment(OTHER_ID, assignment(OTHER_ID, StaffAssignmentRole.MOVER, AssignmentStatus.ASSIGNED));
        when(bookingWorkflowService.getSnapshot(BOOKING_ID)).thenReturn(booking(BookingStatus.CONFIRMED));
        when(staffAssignmentRepository.findAllWithStaffByScheduleIds(List.of(SCHEDULE_ID))).thenReturn(List.of(
                assignment(SUPERVISOR_ID, StaffAssignmentRole.SUPERVISOR, AssignmentStatus.CONFIRMED),
                assignment(OTHER_ID, StaffAssignmentRole.MOVER, AssignmentStatus.ASSIGNED),
                assignment(10L, StaffAssignmentRole.DRIVER, AssignmentStatus.CANCELLED)));

        FieldMovingScheduleResponse response = service.getDetail(SCHEDULE_ID, OTHER_ID);

        assertThat(response.myRole()).isEqualTo(StaffAssignmentRole.MOVER);
        assertThat(response.contactPhone()).isEqualTo("0900");
        assertThat(response.staff()).extracting("userId").containsExactly(SUPERVISOR_ID, OTHER_ID);
        assertThat(response.delayMinutes()).isNull();
    }

    // ---------- start ----------

    @Test
    @DisplayName("start: MOVER / DRIVER không phải giám sát viên → 8004")
    void testStart_NotSupervisor() {
        givenLocked(schedule(MovingScheduleStatus.CONFIRMED));
        givenAssignment(OTHER_ID, assignment(OTHER_ID, StaffAssignmentRole.DRIVER, AssignmentStatus.CONFIRMED));

        assertError(() -> service.start(SCHEDULE_ID, null, OTHER_ID), ErrorCode.ERR_MOVING_NOT_SUPERVISOR);
        verify(movingScheduleRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("start: chuyến không CONFIRMED → 8002")
    void testStart_InvalidStatus() {
        givenLocked(schedule(MovingScheduleStatus.PLANNED));
        givenSupervisor();

        assertError(() -> service.start(SCHEDULE_ID, null, SUPERVISOR_ID), ErrorCode.ERR_MOVING_INVALID_STATUS);
    }

    @Test
    @DisplayName("start: đơn đã CANCELLED / COMPLETED → 8006")
    void testStart_BookingClosed() {
        givenLocked(schedule(MovingScheduleStatus.CONFIRMED));
        givenSupervisor();
        when(bookingWorkflowService.getSnapshot(BOOKING_ID)).thenReturn(booking(BookingStatus.CANCELLED));

        assertError(() -> service.start(SCHEDULE_ID, null, SUPERVISOR_ID), ErrorCode.ERR_MOVING_BOOKING_INVALID_STATUS);
    }

    @Test
    @DisplayName("start: đơn còn phụ phí PROPOSED / WAITING_CUSTOMER / REJECTED_BY_CUSTOMER → 8007 kèm id khoản đang chặn; không đổi gì")
    void testStart_PendingAdjustment() {
        MovingSchedule schedule = schedule(MovingScheduleStatus.CONFIRMED);
        givenLocked(schedule);
        givenSupervisor();
        when(bookingWorkflowService.getSnapshot(BOOKING_ID)).thenReturn(booking(BookingStatus.CONFIRMED));
        when(surchargeRepository.findIdsByBookingIdAndStatusIn(BOOKING_ID, SurchargeStatus.BLOCKING_START))
                .thenReturn(List.of(3, 5));

        assertThatThrownBy(() -> service.start(SCHEDULE_ID, null, SUPERVISOR_ID))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ERR_MOVING_PENDING_ADJUSTMENT)
                .hasFieldOrPropertyWithValue("details", Map.of("blockingSurchargeIds", List.of(3, 5)));
        assertThat(schedule.getStatus()).isEqualTo(MovingScheduleStatus.CONFIRMED);
        assertThat(schedule.getActualStartAt()).isNull();
        verify(bookingWorkflowService, never()).changeStatus(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("start: khác ngày giờ hẹn → 8005; sớm hơn 60 phút → 8005")
    void testStart_InvalidTime() {
        givenSupervisor();
        when(bookingWorkflowService.getSnapshot(BOOKING_ID)).thenReturn(booking(BookingStatus.CONFIRMED));

        givenLocked(schedule(MovingScheduleStatus.CONFIRMED, TODAY.plusDays(1).atTime(8, 0)));
        assertError(() -> service.start(SCHEDULE_ID, null, SUPERVISOR_ID), ErrorCode.ERR_MOVING_INVALID_TIME);

        givenLocked(schedule(MovingScheduleStatus.CONFIRMED, TODAY.atTime(10, 1)));
        assertError(() -> service.start(SCHEDULE_ID, null, SUPERVISOR_ID), ErrorCode.ERR_MOVING_INVALID_TIME);
        verify(bookingWorkflowService, never()).changeStatus(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("start: thành công → IN_PROGRESS, ghi giờ thực tế + nhật ký START; xe AVAILABLE → IN_USE, xe bảo trì giữ nguyên; đơn → IN_PROGRESS")
    void testStart_Success() {
        MovingSchedule schedule = schedule(MovingScheduleStatus.CONFIRMED, TODAY.atTime(10, 0)); // sớm đúng 60 phút
        givenLocked(schedule);
        givenSupervisor();
        when(bookingWorkflowService.getSnapshot(BOOKING_ID)).thenReturn(booking(BookingStatus.CONFIRMED));
        ScheduleVehicleAssignment free = vehicleAssignment(1, VehicleStatus.AVAILABLE, AssignmentStatus.CONFIRMED);
        ScheduleVehicleAssignment maintenance = vehicleAssignment(2, VehicleStatus.MAINTENANCE, AssignmentStatus.CONFIRMED);
        ScheduleVehicleAssignment cancelled = vehicleAssignment(3, VehicleStatus.AVAILABLE, AssignmentStatus.CANCELLED);
        when(vehicleAssignmentRepository.findAllWithVehicleByScheduleIds(List.of(SCHEDULE_ID)))
                .thenReturn(List.of(free, maintenance, cancelled));

        FieldMovingScheduleResponse response = service.start(SCHEDULE_ID, new MovingNoteRequest("  Đủ người  "), SUPERVISOR_ID);

        assertThat(schedule.getStatus()).isEqualTo(MovingScheduleStatus.IN_PROGRESS);
        assertThat(schedule.getActualStartAt()).isEqualTo(NOW);
        assertThat(schedule.getNote()).isEqualTo("[2026-11-02 09:00][START][user#8] Đủ người");
        assertThat(free.getVehicle().getStatus()).isEqualTo(VehicleStatus.IN_USE);
        assertThat(maintenance.getVehicle().getStatus()).isEqualTo(VehicleStatus.MAINTENANCE);
        assertThat(cancelled.getVehicle().getStatus()).isEqualTo(VehicleStatus.AVAILABLE);
        verify(bookingWorkflowService).changeStatus(eq(BOOKING_ID),
                eq(EnumSet.of(BookingStatus.SCHEDULED, BookingStatus.CONFIRMED)), eq(BookingStatus.IN_PROGRESS),
                eq(SUPERVISOR_ID), anyString());
        assertThat(response.delayMinutes()).isEqualTo(-60L);
        assertThat(response.vehicles()).hasSize(2);
    }

    // ---------- progress ----------

    @Test
    @DisplayName("progress: chuyến chưa IN_PROGRESS → 8002; mốc OTHER không note → 1001")
    void testProgress_Invalid() {
        givenSupervisor();
        givenLocked(schedule(MovingScheduleStatus.CONFIRMED));
        assertError(() -> service.reportProgress(SCHEDULE_ID,
                new MovingProgressRequest(ProgressMilestone.ARRIVED_PICKUP, null), SUPERVISOR_ID), ErrorCode.ERR_MOVING_INVALID_STATUS);

        givenLocked(schedule(MovingScheduleStatus.IN_PROGRESS));
        assertError(() -> service.reportProgress(SCHEDULE_ID,
                new MovingProgressRequest(ProgressMilestone.OTHER, "  "), SUPERVISOR_ID), ErrorCode.ERR_INVALID_INPUT);
        verify(movingScheduleRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("progress: nối dòng nhật ký theo tag mốc, không đổi trạng thái, không đụng đơn")
    void testProgress_Success() {
        MovingSchedule schedule = schedule(MovingScheduleStatus.IN_PROGRESS);
        schedule.setActualStartAt(NOW.minusMinutes(30));
        schedule.appendNote("START", SUPERVISOR_ID, "Bắt đầu chuyến", NOW.minusMinutes(30));
        givenLocked(schedule);
        givenSupervisor();
        when(bookingWorkflowService.getSnapshot(BOOKING_ID)).thenReturn(booking(BookingStatus.IN_PROGRESS));

        service.reportProgress(SCHEDULE_ID, new MovingProgressRequest(ProgressMilestone.ARRIVED_PICKUP, "cổng sau"), SUPERVISOR_ID);
        service.reportProgress(SCHEDULE_ID, new MovingProgressRequest(ProgressMilestone.OTHER, "kẹt xe"), SUPERVISOR_ID);

        assertThat(schedule.getStatus()).isEqualTo(MovingScheduleStatus.IN_PROGRESS);
        assertThat(schedule.getNote()).endsWith(
                "[2026-11-02 09:00][ARRIVED_PICKUP][user#8] Đã đến điểm lấy hàng: cổng sau\n"
                        + "[2026-11-02 09:00][OTHER][user#8] kẹt xe");
        verify(bookingWorkflowService, never()).changeStatus(any(), any(), any(), any(), any());
    }

    // ---------- complete ----------

    @Test
    @DisplayName("complete: chuyến chưa IN_PROGRESS → 8002")
    void testComplete_InvalidStatus() {
        givenLocked(schedule(MovingScheduleStatus.CONFIRMED));
        givenSupervisor();

        assertError(() -> service.complete(SCHEDULE_ID, null, SUPERVISOR_ID), ErrorCode.ERR_MOVING_INVALID_STATUS);
    }

    @Test
    @DisplayName("complete: chuyến cuối → COMPLETED + giờ kết thúc; phân công → COMPLETED (trừ dòng đã hủy); xe IN_USE → AVAILABLE; đơn → COMPLETED")
    void testComplete_LastTrip() {
        MovingSchedule schedule = schedule(MovingScheduleStatus.IN_PROGRESS, TODAY.atTime(5, 0));
        schedule.setActualStartAt(TODAY.atTime(5, 10));
        givenLocked(schedule);
        givenSupervisor();
        ScheduleStaffAssignment sup = assignment(SUPERVISOR_ID, StaffAssignmentRole.SUPERVISOR, AssignmentStatus.CONFIRMED);
        ScheduleStaffAssignment cancelledMover = assignment(OTHER_ID, StaffAssignmentRole.MOVER, AssignmentStatus.CANCELLED);
        when(staffAssignmentRepository.findAllWithStaffByScheduleIds(List.of(SCHEDULE_ID))).thenReturn(List.of(sup, cancelledMover));
        ScheduleVehicleAssignment inUse = vehicleAssignment(1, VehicleStatus.IN_USE, AssignmentStatus.CONFIRMED);
        ScheduleVehicleAssignment inactive = vehicleAssignment(2, VehicleStatus.INACTIVE, AssignmentStatus.CONFIRMED);
        when(vehicleAssignmentRepository.findAllWithVehicleByScheduleIds(List.of(SCHEDULE_ID))).thenReturn(List.of(inUse, inactive));
        when(movingScheduleRepository.existsByBookingIdAndIdNotAndStatusIn(BOOKING_ID, SCHEDULE_ID, MovingScheduleStatus.UNFINISHED))
                .thenReturn(false);
        when(bookingWorkflowService.getSnapshot(BOOKING_ID)).thenReturn(booking(BookingStatus.IN_PROGRESS));

        FieldMovingScheduleResponse response = service.complete(SCHEDULE_ID, null, SUPERVISOR_ID);

        assertThat(schedule.getStatus()).isEqualTo(MovingScheduleStatus.COMPLETED);
        assertThat(schedule.getActualEndAt()).isEqualTo(NOW);
        assertThat(schedule.getNote()).isEqualTo("[2026-11-02 09:00][COMPLETE][user#8] Hoàn tất chuyến");
        assertThat(sup.getStatus()).isEqualTo(AssignmentStatus.COMPLETED);
        assertThat(cancelledMover.getStatus()).isEqualTo(AssignmentStatus.CANCELLED);
        assertThat(inUse.getStatus()).isEqualTo(AssignmentStatus.COMPLETED);
        assertThat(inUse.getVehicle().getStatus()).isEqualTo(VehicleStatus.AVAILABLE);
        assertThat(inactive.getVehicle().getStatus()).isEqualTo(VehicleStatus.INACTIVE);
        verify(bookingWorkflowService).changeStatus(eq(BOOKING_ID), eq(EnumSet.of(BookingStatus.IN_PROGRESS)),
                eq(BookingStatus.COMPLETED), eq(SUPERVISOR_ID), anyString());
        // 05:00 + 4h = 09:00 hẹn kết thúc, kết thúc thực tế 09:00
        assertThat(response.delayMinutes()).isZero();
    }

    @Test
    @DisplayName("complete: đơn còn chuyến khác chưa xong → đơn giữ nguyên")
    void testComplete_OtherTripsRemain() {
        MovingSchedule schedule = schedule(MovingScheduleStatus.IN_PROGRESS, TODAY.atTime(5, 0));
        schedule.setActualStartAt(TODAY.atTime(5, 0));
        givenLocked(schedule);
        givenSupervisor();
        when(movingScheduleRepository.existsByBookingIdAndIdNotAndStatusIn(BOOKING_ID, SCHEDULE_ID, MovingScheduleStatus.UNFINISHED))
                .thenReturn(true);
        when(bookingWorkflowService.getSnapshot(BOOKING_ID)).thenReturn(booking(BookingStatus.IN_PROGRESS));

        service.complete(SCHEDULE_ID, new MovingNoteRequest("xong ngày 1"), SUPERVISOR_ID);

        assertThat(schedule.getStatus()).isEqualTo(MovingScheduleStatus.COMPLETED);
        verify(bookingWorkflowService, never()).changeStatus(any(), any(), any(), anyLong(), anyString());
        verify(staffAssignmentRepository, never()).findByMovingScheduleIdAndStaffId(eq(SCHEDULE_ID), eq(OTHER_ID));
    }

    @Test
    @DisplayName("Mọi thao tác ghi khóa dòng chuyến; chuyến không tồn tại → 8001")
    void testWrite_NotFound() {
        when(movingScheduleRepository.findByIdForUpdate(SCHEDULE_ID)).thenReturn(Optional.empty());

        assertError(() -> service.start(SCHEDULE_ID, null, SUPERVISOR_ID), ErrorCode.ERR_MOVING_SCHEDULE_NOT_FOUND);
        assertError(() -> service.complete(SCHEDULE_ID, null, SUPERVISOR_ID), ErrorCode.ERR_MOVING_SCHEDULE_NOT_FOUND);
        verify(movingScheduleRepository, never()).findById(any());
        verify(vehicleAssignmentRepository, never()).findAllWithVehicleByScheduleIds(anyList());
    }
}
