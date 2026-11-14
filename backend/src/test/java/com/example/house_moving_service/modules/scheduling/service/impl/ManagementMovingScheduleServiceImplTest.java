package com.example.house_moving_service.modules.scheduling.service.impl;

import com.example.house_moving_service.common.exception.AppException;
import com.example.house_moving_service.common.exception.ErrorCode;
import com.example.house_moving_service.modules.booking.dto.response.BookingSnapshot;
import com.example.house_moving_service.modules.booking.entity.Booking;
import com.example.house_moving_service.modules.booking.enums.BookingStatus;
import com.example.house_moving_service.modules.booking.service.BookingWorkflowService;
import com.example.house_moving_service.modules.identity.entity.Staff;
import com.example.house_moving_service.modules.identity.enums.RoleCode;
import com.example.house_moving_service.modules.identity.service.StaffLookupService;
import com.example.house_moving_service.modules.identity.service.UserLookupService;
import com.example.house_moving_service.modules.scheduling.config.MovingProperties;
import com.example.house_moving_service.modules.scheduling.dto.request.AssignStaffRequest;
import com.example.house_moving_service.modules.scheduling.dto.request.AssignVehiclesRequest;
import com.example.house_moving_service.modules.scheduling.dto.request.CancelMovingScheduleRequest;
import com.example.house_moving_service.modules.scheduling.dto.request.CreateMovingScheduleRequest;
import com.example.house_moving_service.modules.scheduling.dto.request.RescheduleMovingRequest;
import com.example.house_moving_service.modules.scheduling.dto.response.ManagementMovingScheduleResponse;
import com.example.house_moving_service.modules.scheduling.entity.MovingSchedule;
import com.example.house_moving_service.modules.scheduling.entity.ScheduleStaffAssignment;
import com.example.house_moving_service.modules.scheduling.entity.ScheduleVehicleAssignment;
import com.example.house_moving_service.modules.scheduling.entity.Vehicle;
import com.example.house_moving_service.modules.scheduling.entity.VehicleType;
import com.example.house_moving_service.modules.scheduling.enums.AssignmentStatus;
import com.example.house_moving_service.modules.scheduling.enums.MovingScheduleStatus;
import com.example.house_moving_service.modules.scheduling.enums.StaffAssignmentRole;
import com.example.house_moving_service.modules.scheduling.enums.VehicleStatus;
import com.example.house_moving_service.modules.scheduling.mapper.MovingScheduleMapper;
import com.example.house_moving_service.modules.scheduling.repository.MovingScheduleRepository;
import com.example.house_moving_service.modules.scheduling.repository.ScheduleStaffAssignmentRepository;
import com.example.house_moving_service.modules.scheduling.repository.ScheduleVehicleAssignmentRepository;
import com.example.house_moving_service.modules.scheduling.repository.VehicleRepository;
import com.example.house_moving_service.modules.survey.service.SurveyStaffScheduleService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ManagementMovingScheduleServiceImplTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 14, 9, 0);
    private static final Clock CLOCK = Clock.fixed(NOW.atZone(ZONE).toInstant(), ZONE);
    private static final LocalDateTime START = LocalDateTime.of(2026, 11, 20, 8, 0);
    private static final LocalDateTime END = LocalDateTime.of(2026, 11, 20, 12, 0);

    @Mock
    private MovingScheduleRepository movingScheduleRepository;
    @Mock
    private ScheduleStaffAssignmentRepository staffAssignmentRepository;
    @Mock
    private ScheduleVehicleAssignmentRepository vehicleAssignmentRepository;
    @Mock
    private VehicleRepository vehicleRepository;
    @Mock
    private BookingWorkflowService bookingWorkflowService;
    @Mock
    private StaffLookupService staffLookupService;
    @Mock
    private UserLookupService userLookupService;
    @Mock
    private SurveyStaffScheduleService surveyStaffScheduleService;

    private ManagementMovingScheduleServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ManagementMovingScheduleServiceImpl(movingScheduleRepository, staffAssignmentRepository,
                vehicleAssignmentRepository, vehicleRepository, bookingWorkflowService, staffLookupService,
                userLookupService, surveyStaffScheduleService, new MovingScheduleMapper(),
                new MovingScheduleCancellation(movingScheduleRepository, staffAssignmentRepository, vehicleAssignmentRepository, CLOCK),
                new MovingProperties(), CLOCK);
        when(bookingWorkflowService.getSnapshot(1)).thenReturn(snapshot(BookingStatus.SCHEDULED));
        when(staffLookupService.findSummary(anyLong())).thenReturn(Optional.empty());
    }

    private static BookingSnapshot snapshot(BookingStatus status) {
        return new BookingSnapshot(1, "BK-001", status, 100L, "Lan", "Lan", "0900000000", START, null);
    }

    private static MovingSchedule schedule(MovingScheduleStatus status) {
        return MovingSchedule.builder().id(10).booking(Booking.builder().id(1).build())
                .scheduledStartAt(START).scheduledEndAt(END).status(status).build();
    }

    private static Vehicle vehicle(int id, VehicleStatus status) {
        return Vehicle.builder().id(id).licensePlate("51C-00" + id).status(status)
                .vehicleType(VehicleType.builder().id(1).name("Xe tải 2 tấn").isActive(true).build()).build();
    }

    private static AssignStaffRequest.Item item(long staffId, StaffAssignmentRole role) {
        return AssignStaffRequest.Item.builder().staffId(staffId).role(role).build();
    }

    private void scheduleLocked(MovingSchedule schedule) {
        when(movingScheduleRepository.findByIdForUpdate(10)).thenReturn(Optional.of(schedule));
    }

    @Nested
    @DisplayName("create")
    class Create {

        private CreateMovingScheduleRequest request(LocalDateTime start, LocalDateTime end) {
            return CreateMovingScheduleRequest.builder().scheduledStartAt(start).scheduledEndAt(end).build();
        }

        @Test
        @DisplayName("Đơn chưa đặt cọc (APPROVED) → ERR_MOVING_BOOKING_NOT_SCHEDULABLE")
        void bookingNotConfirmed() {
            when(bookingWorkflowService.getSnapshot(1)).thenReturn(snapshot(BookingStatus.APPROVED));

            AppException ex = assertThrows(AppException.class, () -> service.create(1, request(START, END), 5L));

            assertEquals(ErrorCode.ERR_MOVING_BOOKING_NOT_SCHEDULABLE, ex.getErrorCode());
            verify(movingScheduleRepository, never()).save(any());
        }

        @Test
        @DisplayName("Giờ kết thúc không sau giờ bắt đầu → ERR_MOVING_SCHEDULE_INVALID_TIME")
        void endBeforeStart() {
            when(bookingWorkflowService.getSnapshot(1)).thenReturn(snapshot(BookingStatus.CONFIRMED));

            AppException ex = assertThrows(AppException.class, () -> service.create(1, request(END, START), 5L));

            assertEquals(ErrorCode.ERR_MOVING_SCHEDULE_INVALID_TIME, ex.getErrorCode());
        }

        @Test
        @DisplayName("Giờ bắt đầu đã qua → ERR_MOVING_SCHEDULE_INVALID_TIME")
        void startInPast() {
            when(bookingWorkflowService.getSnapshot(1)).thenReturn(snapshot(BookingStatus.CONFIRMED));

            AppException ex = assertThrows(AppException.class,
                    () -> service.create(1, request(NOW.minusHours(1), NOW.plusHours(2)), 5L));

            assertEquals(ErrorCode.ERR_MOVING_SCHEDULE_INVALID_TIME, ex.getErrorCode());
        }

        @Test
        @DisplayName("Chuyến dài quá 24 giờ → ERR_MOVING_SCHEDULE_INVALID_TIME")
        void tooLong() {
            when(bookingWorkflowService.getSnapshot(1)).thenReturn(snapshot(BookingStatus.CONFIRMED));

            AppException ex = assertThrows(AppException.class, () -> service.create(1, request(START, START.plusHours(25)), 5L));

            assertEquals(ErrorCode.ERR_MOVING_SCHEDULE_INVALID_TIME, ex.getErrorCode());
        }

        @Test
        @DisplayName("Đơn CONFIRMED → tạo chuyến PLANNED và chuyển đơn sang SCHEDULED")
        void success() {
            when(bookingWorkflowService.getSnapshot(1))
                    .thenReturn(snapshot(BookingStatus.CONFIRMED), snapshot(BookingStatus.SCHEDULED));
            when(bookingWorkflowService.getReference(1)).thenReturn(Booking.builder().id(1).build());
            when(movingScheduleRepository.save(any(MovingSchedule.class))).thenAnswer(inv -> {
                MovingSchedule s = inv.getArgument(0);
                s.setId(10);
                return s;
            });

            ManagementMovingScheduleResponse res = service.create(1, request(START, END), 5L);

            assertEquals(MovingScheduleStatus.PLANNED, res.schedule().status());
            assertEquals(BookingStatus.SCHEDULED, res.bookingStatus());
            verify(bookingWorkflowService).changeStatus(eq(1), eq(ManagementMovingScheduleServiceImpl.SCHEDULABLE_BOOKING_STATUSES),
                    eq(BookingStatus.SCHEDULED), eq(5L), any());
        }
    }

    @Nested
    @DisplayName("assignVehicles")
    class AssignVehicles {

        private AssignVehiclesRequest request(Integer... ids) {
            return AssignVehiclesRequest.builder().vehicleIds(List.of(ids)).build();
        }

        @Test
        @DisplayName("Chuyến đang chạy → ERR_MOVING_INVALID_STATUS")
        void scheduleInProgress() {
            scheduleLocked(schedule(MovingScheduleStatus.IN_PROGRESS));

            AppException ex = assertThrows(AppException.class, () -> service.assignVehicles(10, request(1), 5L));

            assertEquals(ErrorCode.ERR_MOVING_INVALID_STATUS, ex.getErrorCode());
        }

        @Test
        @DisplayName("Danh sách xe bị trùng → ERR_MOVING_ASSIGNMENT_INVALID")
        void duplicateIds() {
            scheduleLocked(schedule(MovingScheduleStatus.PLANNED));

            AppException ex = assertThrows(AppException.class, () -> service.assignVehicles(10, request(1, 1), 5L));

            assertEquals(ErrorCode.ERR_MOVING_ASSIGNMENT_INVALID, ex.getErrorCode());
        }

        @Test
        @DisplayName("Xe không tồn tại → ERR_VEHICLE_NOT_FOUND")
        void vehicleNotFound() {
            scheduleLocked(schedule(MovingScheduleStatus.PLANNED));
            when(vehicleRepository.findAllById(anyList())).thenReturn(List.of());

            AppException ex = assertThrows(AppException.class, () -> service.assignVehicles(10, request(99), 5L));

            assertEquals(ErrorCode.ERR_VEHICLE_NOT_FOUND, ex.getErrorCode());
        }

        @Test
        @DisplayName("Xe đang bảo dưỡng → ERR_MOVING_VEHICLE_UNAVAILABLE")
        void vehicleInMaintenance() {
            scheduleLocked(schedule(MovingScheduleStatus.PLANNED));
            when(vehicleRepository.findAllById(anyList())).thenReturn(List.of(vehicle(1, VehicleStatus.MAINTENANCE)));

            AppException ex = assertThrows(AppException.class, () -> service.assignVehicles(10, request(1), 5L));

            assertEquals(ErrorCode.ERR_MOVING_VEHICLE_UNAVAILABLE, ex.getErrorCode());
        }

        @Test
        @DisplayName("Xe đã có chuyến khác trùng giờ → ERR_MOVING_VEHICLE_CONFLICT")
        void vehicleConflict() {
            scheduleLocked(schedule(MovingScheduleStatus.PLANNED));
            when(vehicleRepository.findAllById(anyList())).thenReturn(List.of(vehicle(1, VehicleStatus.AVAILABLE)));
            when(vehicleAssignmentRepository.findConflictingVehicleIds(anyCollection(), eq(START), eq(END), eq(10)))
                    .thenReturn(List.of(1));

            AppException ex = assertThrows(AppException.class, () -> service.assignVehicles(10, request(1), 5L));

            assertEquals(ErrorCode.ERR_MOVING_VEHICLE_CONFLICT, ex.getErrorCode());
            verify(vehicleAssignmentRepository, never()).saveAllAndFlush(anyList());
        }

        @Test
        @DisplayName("Ghi đè: thêm xe mới, hủy xe cũ không còn trong danh sách, khóa từng xe")
        @SuppressWarnings("unchecked")
        void success() {
            MovingSchedule schedule = schedule(MovingScheduleStatus.PLANNED);
            scheduleLocked(schedule);
            Vehicle oldVehicle = vehicle(2, VehicleStatus.AVAILABLE);
            ScheduleVehicleAssignment oldRow = ScheduleVehicleAssignment.builder().id(7).movingSchedule(schedule)
                    .vehicle(oldVehicle).status(AssignmentStatus.ASSIGNED).build();
            when(vehicleRepository.findAllById(anyList())).thenReturn(List.of(vehicle(1, VehicleStatus.IN_USE)));
            when(vehicleAssignmentRepository.findConflictingVehicleIds(anyCollection(), any(), any(), any())).thenReturn(List.of());
            when(vehicleAssignmentRepository.findAllByMovingScheduleId(10)).thenReturn(List.of(oldRow));

            service.assignVehicles(10, request(1), 5L);

            ArgumentCaptor<List<ScheduleVehicleAssignment>> saved = ArgumentCaptor.forClass(List.class);
            verify(vehicleAssignmentRepository).saveAllAndFlush(saved.capture());
            assertEquals(2, saved.getValue().size());
            assertEquals(AssignmentStatus.CANCELLED, oldRow.getStatus());
            ScheduleVehicleAssignment newRow = saved.getValue().stream().filter(a -> a.getId() == null).findFirst().orElseThrow();
            assertEquals(1, newRow.getVehicle().getId());
            assertEquals(AssignmentStatus.ASSIGNED, newRow.getStatus());
            verify(movingScheduleRepository).acquireTransactionLock(ManagementMovingScheduleServiceImpl.LOCK_NAMESPACE_VEHICLE, 1);
        }
    }

    @Nested
    @DisplayName("assignStaff")
    class AssignStaff {

        private AssignStaffRequest request(AssignStaffRequest.Item... items) {
            return AssignStaffRequest.builder().assignments(List.of(items)).build();
        }

        @Test
        @DisplayName("Không có giám sát viên → ERR_MOVING_ASSIGNMENT_INVALID")
        void noSupervisor() {
            scheduleLocked(schedule(MovingScheduleStatus.PLANNED));

            AppException ex = assertThrows(AppException.class,
                    () -> service.assignStaff(10, request(item(1, StaffAssignmentRole.MOVER)), 5L));

            assertEquals(ErrorCode.ERR_MOVING_ASSIGNMENT_INVALID, ex.getErrorCode());
        }

        @Test
        @DisplayName("Hai giám sát viên → ERR_MOVING_ASSIGNMENT_INVALID")
        void twoSupervisors() {
            scheduleLocked(schedule(MovingScheduleStatus.PLANNED));

            AppException ex = assertThrows(AppException.class, () -> service.assignStaff(10,
                    request(item(1, StaffAssignmentRole.SUPERVISOR), item(2, StaffAssignmentRole.SUPERVISOR)), 5L));

            assertEquals(ErrorCode.ERR_MOVING_ASSIGNMENT_INVALID, ex.getErrorCode());
        }

        @Test
        @DisplayName("Nhân viên bị trùng trong danh sách → ERR_MOVING_ASSIGNMENT_INVALID")
        void duplicateStaff() {
            scheduleLocked(schedule(MovingScheduleStatus.PLANNED));

            AppException ex = assertThrows(AppException.class, () -> service.assignStaff(10,
                    request(item(1, StaffAssignmentRole.SUPERVISOR), item(1, StaffAssignmentRole.MOVER)), 5L));

            assertEquals(ErrorCode.ERR_MOVING_ASSIGNMENT_INVALID, ex.getErrorCode());
        }

        @Test
        @DisplayName("Không phải MOVER đang làm việc → ERR_MOVING_STAFF_INVALID")
        void notMover() {
            scheduleLocked(schedule(MovingScheduleStatus.PLANNED));
            when(staffLookupService.isActiveStaffWithRole(1L, RoleCode.MOVER)).thenReturn(false);

            AppException ex = assertThrows(AppException.class,
                    () -> service.assignStaff(10, request(item(1, StaffAssignmentRole.SUPERVISOR)), 5L));

            assertEquals(ErrorCode.ERR_MOVING_STAFF_INVALID, ex.getErrorCode());
        }

        @Test
        @DisplayName("Trùng chuyến vận chuyển khác → ERR_MOVING_STAFF_CONFLICT")
        void movingConflict() {
            scheduleLocked(schedule(MovingScheduleStatus.PLANNED));
            when(staffLookupService.isActiveStaffWithRole(anyLong(), eq(RoleCode.MOVER))).thenReturn(true);
            when(staffAssignmentRepository.findConflictingStaffIds(anyCollection(), eq(START), eq(END), eq(10)))
                    .thenReturn(List.of(2L));

            AppException ex = assertThrows(AppException.class, () -> service.assignStaff(10,
                    request(item(1, StaffAssignmentRole.SUPERVISOR), item(2, StaffAssignmentRole.MOVER)), 5L));

            assertEquals(ErrorCode.ERR_MOVING_STAFF_CONFLICT, ex.getErrorCode());
        }

        @Test
        @DisplayName("Trùng lịch khảo sát → ERR_MOVING_STAFF_SURVEY_CONFLICT")
        void surveyConflict() {
            scheduleLocked(schedule(MovingScheduleStatus.PLANNED));
            when(staffLookupService.isActiveStaffWithRole(anyLong(), eq(RoleCode.MOVER))).thenReturn(true);
            when(staffAssignmentRepository.findConflictingStaffIds(anyCollection(), any(), any(), any())).thenReturn(List.of());
            when(surveyStaffScheduleService.hasSurveyBetween(1L, START, END)).thenReturn(true);

            AppException ex = assertThrows(AppException.class,
                    () -> service.assignStaff(10, request(item(1, StaffAssignmentRole.SUPERVISOR)), 5L));

            assertEquals(ErrorCode.ERR_MOVING_STAFF_SURVEY_CONFLICT, ex.getErrorCode());
            verify(staffAssignmentRepository, never()).saveAllAndFlush(anyList());
        }

        @Test
        @DisplayName("Đổi giám sát viên: hạ vai trò người cũ ở lượt 1, thêm người mới ở lượt 2")
        @SuppressWarnings("unchecked")
        void changeSupervisor() {
            MovingSchedule schedule = schedule(MovingScheduleStatus.PLANNED);
            scheduleLocked(schedule);
            ScheduleStaffAssignment oldSupervisor = ScheduleStaffAssignment.builder().id(3).movingSchedule(schedule)
                    .staff(Staff.builder().id(1L).build()).role(StaffAssignmentRole.SUPERVISOR)
                    .status(AssignmentStatus.ASSIGNED).build();
            when(staffAssignmentRepository.findAllByMovingScheduleId(10)).thenReturn(List.of(oldSupervisor));
            when(staffLookupService.isActiveStaffWithRole(anyLong(), eq(RoleCode.MOVER))).thenReturn(true);
            when(staffAssignmentRepository.findConflictingStaffIds(anyCollection(), any(), any(), any())).thenReturn(List.of());
            when(staffLookupService.getReference(2L)).thenReturn(Staff.builder().id(2L).build());

            service.assignStaff(10, request(item(1, StaffAssignmentRole.MOVER), item(2, StaffAssignmentRole.SUPERVISOR)), 5L);

            ArgumentCaptor<List<ScheduleStaffAssignment>> saved = ArgumentCaptor.forClass(List.class);
            verify(staffAssignmentRepository, org.mockito.Mockito.times(2)).saveAllAndFlush(saved.capture());
            List<ScheduleStaffAssignment> firstPass = saved.getAllValues().get(0);
            List<ScheduleStaffAssignment> secondPass = saved.getAllValues().get(1);
            assertEquals(List.of(oldSupervisor), firstPass);
            assertEquals(StaffAssignmentRole.MOVER, oldSupervisor.getRole());
            assertEquals(1, secondPass.size());
            assertEquals(2L, secondPass.get(0).getStaff().getId());
            assertEquals(StaffAssignmentRole.SUPERVISOR, secondPass.get(0).getRole());
            verify(movingScheduleRepository).acquireTransactionLock(ManagementMovingScheduleServiceImpl.LOCK_NAMESPACE_STAFF, 1);
            verify(movingScheduleRepository).acquireTransactionLock(ManagementMovingScheduleServiceImpl.LOCK_NAMESPACE_STAFF, 2);
        }
    }

    @Nested
    @DisplayName("confirm")
    class Confirm {

        @Test
        @DisplayName("Chưa có xe → ERR_MOVING_ASSIGNMENT_INVALID")
        void noVehicle() {
            scheduleLocked(schedule(MovingScheduleStatus.PLANNED));
            when(vehicleAssignmentRepository.findAllByMovingScheduleId(10)).thenReturn(List.of());

            AppException ex = assertThrows(AppException.class, () -> service.confirm(10, 5L));

            assertEquals(ErrorCode.ERR_MOVING_ASSIGNMENT_INVALID, ex.getErrorCode());
        }

        @Test
        @DisplayName("Chưa có giám sát viên → ERR_MOVING_ASSIGNMENT_INVALID")
        void noSupervisor() {
            MovingSchedule schedule = schedule(MovingScheduleStatus.PLANNED);
            scheduleLocked(schedule);
            when(vehicleAssignmentRepository.findAllByMovingScheduleId(10)).thenReturn(List.of(
                    ScheduleVehicleAssignment.builder().vehicle(vehicle(1, VehicleStatus.AVAILABLE))
                            .status(AssignmentStatus.ASSIGNED).build()));
            when(staffAssignmentRepository.findAllByMovingScheduleId(10)).thenReturn(List.of(
                    ScheduleStaffAssignment.builder().staff(Staff.builder().id(1L).build())
                            .role(StaffAssignmentRole.MOVER).status(AssignmentStatus.ASSIGNED).build()));

            AppException ex = assertThrows(AppException.class, () -> service.confirm(10, 5L));

            assertEquals(ErrorCode.ERR_MOVING_ASSIGNMENT_INVALID, ex.getErrorCode());
        }

        @Test
        @DisplayName("Đủ xe và giám sát viên → chuyến và phân công chuyển CONFIRMED")
        void success() {
            MovingSchedule schedule = schedule(MovingScheduleStatus.PLANNED);
            scheduleLocked(schedule);
            ScheduleVehicleAssignment v = ScheduleVehicleAssignment.builder().vehicle(vehicle(1, VehicleStatus.AVAILABLE))
                    .status(AssignmentStatus.ASSIGNED).build();
            ScheduleStaffAssignment s = ScheduleStaffAssignment.builder().staff(Staff.builder().id(1L).build())
                    .role(StaffAssignmentRole.SUPERVISOR).status(AssignmentStatus.ASSIGNED).build();
            when(vehicleAssignmentRepository.findAllByMovingScheduleId(10)).thenReturn(List.of(v));
            when(staffAssignmentRepository.findAllByMovingScheduleId(10)).thenReturn(List.of(s));

            ManagementMovingScheduleResponse res = service.confirm(10, 5L);

            assertEquals(MovingScheduleStatus.CONFIRMED, res.schedule().status());
            assertEquals(AssignmentStatus.CONFIRMED, v.getStatus());
            assertEquals(AssignmentStatus.CONFIRMED, s.getStatus());
        }

        @Test
        @DisplayName("Chuyến đã CONFIRMED → ERR_MOVING_INVALID_STATUS")
        void alreadyConfirmed() {
            scheduleLocked(schedule(MovingScheduleStatus.CONFIRMED));

            AppException ex = assertThrows(AppException.class, () -> service.confirm(10, 5L));

            assertEquals(ErrorCode.ERR_MOVING_INVALID_STATUS, ex.getErrorCode());
            verify(movingScheduleRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("reschedule")
    class Reschedule {

        private RescheduleMovingRequest request(LocalDateTime start, LocalDateTime end) {
            return RescheduleMovingRequest.builder().scheduledStartAt(start).scheduledEndAt(end).reason("Khách đổi ngày").build();
        }

        @Test
        @DisplayName("Giờ mới trùng giờ cũ → ERR_MOVING_SCHEDULE_INVALID_TIME")
        void sameTime() {
            scheduleLocked(schedule(MovingScheduleStatus.PLANNED));

            AppException ex = assertThrows(AppException.class, () -> service.reschedule(10, request(START, END), 5L));

            assertEquals(ErrorCode.ERR_MOVING_SCHEDULE_INVALID_TIME, ex.getErrorCode());
        }

        @Test
        @DisplayName("Xe đang phân công bận ở giờ mới → ERR_MOVING_VEHICLE_CONFLICT, giờ chuyến giữ nguyên")
        void vehicleBusyAtNewTime() {
            MovingSchedule schedule = schedule(MovingScheduleStatus.CONFIRMED);
            scheduleLocked(schedule);
            Vehicle v = vehicle(1, VehicleStatus.AVAILABLE);
            when(vehicleAssignmentRepository.findAllByMovingScheduleId(10)).thenReturn(List.of(
                    ScheduleVehicleAssignment.builder().vehicle(v).status(AssignmentStatus.CONFIRMED).build()));
            when(vehicleRepository.findAllById(anyCollection())).thenReturn(List.of(v));
            LocalDateTime newStart = START.plusDays(1);
            LocalDateTime newEnd = END.plusDays(1);
            when(vehicleAssignmentRepository.findConflictingVehicleIds(anyCollection(), eq(newStart), eq(newEnd), eq(10)))
                    .thenReturn(List.of(1));

            AppException ex = assertThrows(AppException.class, () -> service.reschedule(10, request(newStart, newEnd), 5L));

            assertEquals(ErrorCode.ERR_MOVING_VEHICLE_CONFLICT, ex.getErrorCode());
            assertEquals(START, schedule.getScheduledStartAt());
        }

        @Test
        @DisplayName("Xe và nhân viên còn rảnh → đổi giờ, giữ nguyên trạng thái CONFIRMED")
        void success() {
            MovingSchedule schedule = schedule(MovingScheduleStatus.CONFIRMED);
            scheduleLocked(schedule);
            Vehicle v = vehicle(1, VehicleStatus.AVAILABLE);
            when(vehicleAssignmentRepository.findAllByMovingScheduleId(10)).thenReturn(List.of(
                    ScheduleVehicleAssignment.builder().vehicle(v).status(AssignmentStatus.CONFIRMED).build()));
            when(staffAssignmentRepository.findAllByMovingScheduleId(10)).thenReturn(List.of(
                    ScheduleStaffAssignment.builder().staff(Staff.builder().id(3L).build())
                            .role(StaffAssignmentRole.SUPERVISOR).status(AssignmentStatus.CONFIRMED).build()));
            when(vehicleRepository.findAllById(anyCollection())).thenReturn(List.of(v));
            when(vehicleAssignmentRepository.findConflictingVehicleIds(anyCollection(), any(), any(), any())).thenReturn(List.of());
            when(staffLookupService.isActiveStaffWithRole(3L, RoleCode.MOVER)).thenReturn(true);
            when(staffAssignmentRepository.findConflictingStaffIds(anyCollection(), any(), any(), any())).thenReturn(List.of());
            LocalDateTime newStart = START.plusDays(1);

            ManagementMovingScheduleResponse res = service.reschedule(10, request(newStart, END.plusDays(1)), 5L);

            assertEquals(newStart, res.schedule().scheduledStartAt());
            assertEquals(MovingScheduleStatus.CONFIRMED, res.schedule().status());
            verify(surveyStaffScheduleService).hasSurveyBetween(3L, newStart, END.plusDays(1));
        }
    }

    @Nested
    @DisplayName("cancel")
    class Cancel {

        private final CancelMovingScheduleRequest request = CancelMovingScheduleRequest.builder().reason("Mưa bão").build();

        @Test
        @DisplayName("Chuyến đang chạy → ERR_MOVING_INVALID_STATUS")
        void inProgress() {
            scheduleLocked(schedule(MovingScheduleStatus.IN_PROGRESS));

            AppException ex = assertThrows(AppException.class, () -> service.cancel(10, request, 5L));

            assertEquals(ErrorCode.ERR_MOVING_INVALID_STATUS, ex.getErrorCode());
        }

        @Test
        @DisplayName("Chuyến cuối của đơn SCHEDULED → hủy phân công, đơn về CONFIRMED")
        void lastScheduleBackToConfirmed() {
            MovingSchedule schedule = schedule(MovingScheduleStatus.CONFIRMED);
            scheduleLocked(schedule);
            ScheduleVehicleAssignment v = ScheduleVehicleAssignment.builder().vehicle(vehicle(1, VehicleStatus.AVAILABLE))
                    .status(AssignmentStatus.CONFIRMED).build();
            when(vehicleAssignmentRepository.findAllByMovingScheduleId(10)).thenReturn(List.of(v));
            when(movingScheduleRepository.existsByBookingIdAndIdNotAndStatusIn(eq(1), eq(10), any())).thenReturn(false);

            ManagementMovingScheduleResponse res = service.cancel(10, request, 5L);

            assertEquals(MovingScheduleStatus.CANCELLED, res.schedule().status());
            assertEquals(AssignmentStatus.CANCELLED, v.getStatus());
            verify(bookingWorkflowService).changeStatus(eq(1), eq(java.util.EnumSet.of(BookingStatus.SCHEDULED)),
                    eq(BookingStatus.CONFIRMED), eq(5L), any());
        }

        @Test
        @DisplayName("Đơn còn chuyến khác chưa xong → không đổi trạng thái đơn")
        void otherSchedulesRemain() {
            scheduleLocked(schedule(MovingScheduleStatus.PLANNED));
            when(movingScheduleRepository.existsByBookingIdAndIdNotAndStatusIn(eq(1), eq(10), any())).thenReturn(true);

            service.cancel(10, request, 5L);

            verify(bookingWorkflowService, never()).changeStatus(any(), any(), any(), any(), any());
        }
    }

    @Test
    @DisplayName("MovingStaffScheduleChecker: nhân viên có chuyến trùng giờ → bận")
    void staffChecker() {
        MovingStaffScheduleChecker checker = new MovingStaffScheduleChecker(staffAssignmentRepository);
        when(staffAssignmentRepository.findConflictingStaffIds(List.of(1L), START, END, 0)).thenReturn(List.of(1L));

        assertEquals(true, checker.isBusy(1L, START, END));
        assertEquals(false, checker.isBusy(1L, END, START));
    }
}
