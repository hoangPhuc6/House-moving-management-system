package com.example.house_moving_service.modules.scheduling.service.impl;

import com.example.house_moving_service.common.exception.AppException;
import com.example.house_moving_service.common.exception.ErrorCode;
import com.example.house_moving_service.modules.booking.dto.response.BookingSnapshot;
import com.example.house_moving_service.modules.booking.enums.BookingStatus;
import com.example.house_moving_service.modules.booking.service.BookingWorkflowService;
import com.example.house_moving_service.modules.identity.dto.response.StaffSummary;
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
import com.example.house_moving_service.modules.scheduling.enums.AssignmentStatus;
import com.example.house_moving_service.modules.scheduling.enums.MovingScheduleStatus;
import com.example.house_moving_service.modules.scheduling.enums.StaffAssignmentRole;
import com.example.house_moving_service.modules.scheduling.mapper.MovingScheduleMapper;
import com.example.house_moving_service.modules.scheduling.repository.MovingScheduleRepository;
import com.example.house_moving_service.modules.scheduling.repository.ScheduleStaffAssignmentRepository;
import com.example.house_moving_service.modules.scheduling.repository.ScheduleVehicleAssignmentRepository;
import com.example.house_moving_service.modules.scheduling.repository.VehicleRepository;
import com.example.house_moving_service.modules.scheduling.service.ManagementMovingScheduleService;
import com.example.house_moving_service.modules.survey.service.SurveyStaffScheduleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ManagementMovingScheduleServiceImpl implements ManagementMovingScheduleService {

    // CONFIRMED = đã đặt cọc, lập chuyến đầu tiên; SCHEDULED = đã có chuyến, thêm chuyến (chuyển nhà nhiều ngày)
    static final Set<BookingStatus> SCHEDULABLE_BOOKING_STATUSES = EnumSet.of(BookingStatus.CONFIRMED, BookingStatus.SCHEDULED);
    // Chỉ sửa phân công khi chuyến chưa chạy
    static final Set<MovingScheduleStatus> EDITABLE_SCHEDULE_STATUSES = EnumSet.of(MovingScheduleStatus.PLANNED, MovingScheduleStatus.CONFIRMED);

    static final String TAG_CREATE = "CREATE";
    static final String TAG_ASSIGN_VEHICLE = "ASSIGN_VEHICLE";
    static final String TAG_ASSIGN_STAFF = "ASSIGN_STAFF";
    static final String TAG_CONFIRM = "CONFIRM";
    static final String TAG_RESCHEDULE = "RESCHEDULE";

    // Namespace cho advisory lock (xem MovingScheduleRepository#acquireTransactionLock)
    static final int LOCK_NAMESPACE_VEHICLE = 8201;
    static final int LOCK_NAMESPACE_STAFF = 8202;

    private final MovingScheduleRepository movingScheduleRepository;
    private final ScheduleStaffAssignmentRepository staffAssignmentRepository;
    private final ScheduleVehicleAssignmentRepository vehicleAssignmentRepository;
    private final VehicleRepository vehicleRepository;
    private final BookingWorkflowService bookingWorkflowService;
    private final StaffLookupService staffLookupService;
    private final UserLookupService userLookupService;
    private final SurveyStaffScheduleService surveyStaffScheduleService;
    private final MovingScheduleMapper movingScheduleMapper;
    private final MovingScheduleCancellation movingScheduleCancellation;
    private final MovingProperties movingProperties;
    private final Clock clock;

    // ===================================================================== tạo chuyến

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ManagementMovingScheduleResponse create(Integer bookingId, CreateMovingScheduleRequest request, Long actorUserId) {
        BookingSnapshot booking = bookingWorkflowService.getSnapshot(bookingId);
        if (!SCHEDULABLE_BOOKING_STATUSES.contains(booking.status())) {
            throw new AppException(ErrorCode.ERR_MOVING_BOOKING_NOT_SCHEDULABLE, booking.status());
        }

        LocalDateTime start = request.getScheduledStartAt();
        LocalDateTime end = request.getScheduledEndAt();
        LocalDateTime now = LocalDateTime.now(clock);
        validateTimeRange(start, end, now);

        // Khóa dòng đơn và kiểm tra lại trạng thái (đơn có thể vừa bị hủy); đơn đã SCHEDULED thì không ghi lịch sử lần nữa
        bookingWorkflowService.changeStatus(bookingId, SCHEDULABLE_BOOKING_STATUSES, BookingStatus.SCHEDULED, actorUserId,
                "Lập lịch vận chuyển " + describeRange(start, end));

        MovingSchedule schedule = MovingSchedule.builder()
                .booking(bookingWorkflowService.getReference(bookingId))
                .scheduledStartAt(start)
                .scheduledEndAt(end)
                .status(MovingScheduleStatus.PLANNED)
                .createdBy(actorUserId != null ? userLookupService.getUserReference(actorUserId) : null)
                .build();
        String note = request.getNote() != null && !request.getNote().isBlank() ? ". Ghi chú: " + request.getNote().trim() : "";
        schedule.appendNote(TAG_CREATE, actorUserId, "Lập lịch " + describeRange(start, end) + note, now);

        MovingSchedule saved = movingScheduleRepository.save(schedule);
        log.info("Moving schedule id: {} created for booking id: {} ({}) by user id: {}",
                saved.getId(), bookingId, describeRange(start, end), actorUserId);
        return toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public ManagementMovingScheduleResponse getDetail(Integer scheduleId) {
        MovingSchedule schedule = movingScheduleRepository.findById(scheduleId)
                .orElseThrow(() -> new AppException(ErrorCode.ERR_MOVING_SCHEDULE_NOT_FOUND, scheduleId));
        return toResponse(schedule);
    }

    // ===================================================================== phân công xe

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ManagementMovingScheduleResponse assignVehicles(Integer scheduleId, AssignVehiclesRequest request, Long actorUserId) {
        MovingSchedule schedule = loadEditable(scheduleId);
        List<Integer> vehicleIds = request.getVehicleIds();
        if (new HashSet<>(vehicleIds).size() != vehicleIds.size()) {
            throw new AppException(ErrorCode.ERR_MOVING_ASSIGNMENT_INVALID, "danh sách xe bị trùng");
        }

        Map<Integer, Vehicle> vehicles = lockAndCheckVehicles(vehicleIds,
                schedule.getScheduledStartAt(), schedule.getScheduledEndAt(), schedule.getId());

        // Ghi đè: hủy xe không còn trong danh sách, dùng lại dòng cũ nếu xe từng được phân công (unique chuyến + xe)
        AssignmentStatus activeStatus = activeAssignmentStatus(schedule);
        Map<Integer, ScheduleVehicleAssignment> existing = vehicleAssignmentRepository.findAllByMovingScheduleId(scheduleId).stream()
                .collect(Collectors.toMap(a -> a.getVehicle().getId(), Function.identity()));
        Set<Integer> wanted = new HashSet<>(vehicleIds);
        List<ScheduleVehicleAssignment> toSave = new ArrayList<>();
        existing.values().stream()
                .filter(a -> !wanted.contains(a.getVehicle().getId()) && a.getStatus() != AssignmentStatus.CANCELLED)
                .forEach(a -> {
                    a.setStatus(AssignmentStatus.CANCELLED);
                    toSave.add(a);
                });
        for (Integer id : vehicleIds) {
            ScheduleVehicleAssignment row = existing.get(id);
            if (row == null) {
                toSave.add(ScheduleVehicleAssignment.builder()
                        .movingSchedule(schedule)
                        .vehicle(vehicles.get(id))
                        .status(activeStatus)
                        .build());
            } else if (row.getStatus() == AssignmentStatus.CANCELLED) {
                row.setStatus(activeStatus);
                toSave.add(row);
            }
        }
        vehicleAssignmentRepository.saveAllAndFlush(toSave);

        String plates = vehicleIds.stream().map(id -> vehicles.get(id).getLicensePlate()).collect(Collectors.joining(", "));
        schedule.appendNote(TAG_ASSIGN_VEHICLE, actorUserId, "Phân công xe: " + plates, LocalDateTime.now(clock));
        movingScheduleRepository.save(schedule);
        log.info("Moving schedule id: {} vehicles set to {} by user id: {}", scheduleId, vehicleIds, actorUserId);
        return toResponse(schedule);
    }

    // ===================================================================== phân công nhân viên

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ManagementMovingScheduleResponse assignStaff(Integer scheduleId, AssignStaffRequest request, Long actorUserId) {
        MovingSchedule schedule = loadEditable(scheduleId);

        // staffId → vai trò, giữ thứ tự người dùng gửi
        Map<Long, StaffAssignmentRole> wanted = new LinkedHashMap<>();
        for (AssignStaffRequest.Item item : request.getAssignments()) {
            if (wanted.put(item.getStaffId(), item.getRole()) != null) {
                throw new AppException(ErrorCode.ERR_MOVING_ASSIGNMENT_INVALID, "nhân viên #" + item.getStaffId() + " bị trùng");
            }
        }
        long supervisors = wanted.values().stream().filter(r -> r == StaffAssignmentRole.SUPERVISOR).count();
        if (supervisors != 1) {
            throw new AppException(ErrorCode.ERR_MOVING_ASSIGNMENT_INVALID, "mỗi chuyến cần đúng 1 giám sát viên (SUPERVISOR)");
        }

        lockAndCheckStaff(wanted.keySet(), schedule.getScheduledStartAt(), schedule.getScheduledEndAt(), schedule.getId());

        applyStaffAssignments(schedule, wanted);

        String summary = wanted.entrySet().stream()
                .map(e -> describeStaff(List.of(e.getKey())) + " (" + e.getValue() + ")")
                .collect(Collectors.joining(", "));
        schedule.appendNote(TAG_ASSIGN_STAFF, actorUserId, "Phân công nhân viên: " + summary, LocalDateTime.now(clock));
        movingScheduleRepository.save(schedule);
        log.info("Moving schedule id: {} staff set to {} by user id: {}", scheduleId, wanted, actorUserId);
        return toResponse(schedule);
    }

    /**
     * Ghi đè phân công, làm 2 lượt vì unique index uq_ssa_one_supervisor (1 SUPERVISOR chưa hủy mỗi chuyến):
     * lượt 1 hủy / hạ vai trò giám sát viên cũ rồi flush, lượt 2 mới thêm / kích hoạt / gán giám sát viên mới.
     */
    private void applyStaffAssignments(MovingSchedule schedule, Map<Long, StaffAssignmentRole> wanted) {
        AssignmentStatus activeStatus = activeAssignmentStatus(schedule);
        Map<Long, ScheduleStaffAssignment> existing = staffAssignmentRepository.findAllByMovingScheduleId(schedule.getId()).stream()
                .collect(Collectors.toMap(a -> a.getStaff().getId(), Function.identity()));

        List<ScheduleStaffAssignment> firstPass = new ArrayList<>();
        for (ScheduleStaffAssignment row : existing.values()) {
            StaffAssignmentRole newRole = wanted.get(row.getStaff().getId());
            if (newRole == null && row.getStatus() != AssignmentStatus.CANCELLED) {
                row.setStatus(AssignmentStatus.CANCELLED);
                firstPass.add(row);
            } else if (newRole != null && row.getRole() == StaffAssignmentRole.SUPERVISOR && newRole != StaffAssignmentRole.SUPERVISOR) {
                row.setRole(newRole);
                firstPass.add(row);
            }
        }
        staffAssignmentRepository.saveAllAndFlush(firstPass);

        List<ScheduleStaffAssignment> secondPass = new ArrayList<>();
        wanted.forEach((staffId, role) -> {
            ScheduleStaffAssignment row = existing.get(staffId);
            if (row == null) {
                secondPass.add(ScheduleStaffAssignment.builder()
                        .movingSchedule(schedule)
                        .staff(staffLookupService.getReference(staffId))
                        .role(role)
                        .status(activeStatus)
                        .build());
            } else if (row.getRole() != role || row.getStatus() == AssignmentStatus.CANCELLED) {
                row.setRole(role);
                if (row.getStatus() == AssignmentStatus.CANCELLED) {
                    row.setStatus(activeStatus);
                }
                secondPass.add(row);
            }
        });
        staffAssignmentRepository.saveAllAndFlush(secondPass);
    }

    // ===================================================================== xác nhận chuyến

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ManagementMovingScheduleResponse confirm(Integer scheduleId, Long actorUserId) {
        MovingSchedule schedule = loadForUpdate(scheduleId);
        if (schedule.getStatus() != MovingScheduleStatus.PLANNED) {
            throw new AppException(ErrorCode.ERR_MOVING_INVALID_STATUS);
        }

        List<ScheduleVehicleAssignment> vehicles = vehicleAssignmentRepository.findAllByMovingScheduleId(scheduleId).stream()
                .filter(a -> a.getStatus() != AssignmentStatus.CANCELLED)
                .toList();
        if (vehicles.isEmpty()) {
            throw new AppException(ErrorCode.ERR_MOVING_ASSIGNMENT_INVALID, "chuyến chưa được phân công xe");
        }
        List<ScheduleStaffAssignment> staff = staffAssignmentRepository.findAllByMovingScheduleId(scheduleId).stream()
                .filter(a -> a.getStatus() != AssignmentStatus.CANCELLED)
                .toList();
        if (staff.stream().noneMatch(a -> a.getRole() == StaffAssignmentRole.SUPERVISOR)) {
            throw new AppException(ErrorCode.ERR_MOVING_ASSIGNMENT_INVALID, "chuyến chưa có giám sát viên (SUPERVISOR)");
        }

        schedule.setStatus(MovingScheduleStatus.CONFIRMED);
        vehicles.forEach(a -> a.setStatus(AssignmentStatus.CONFIRMED));
        staff.forEach(a -> a.setStatus(AssignmentStatus.CONFIRMED));
        vehicleAssignmentRepository.saveAll(vehicles);
        staffAssignmentRepository.saveAll(staff);
        schedule.appendNote(TAG_CONFIRM, actorUserId, "Xác nhận chuyến: " + vehicles.size() + " xe, "
                + staff.size() + " nhân viên", LocalDateTime.now(clock));
        movingScheduleRepository.save(schedule);
        log.info("Moving schedule id: {} confirmed by user id: {}", scheduleId, actorUserId);
        return toResponse(schedule);
    }

    // ===================================================================== đổi lịch / hủy chuyến

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ManagementMovingScheduleResponse reschedule(Integer scheduleId, RescheduleMovingRequest request, Long actorUserId) {
        MovingSchedule schedule = loadEditable(scheduleId);
        LocalDateTime start = request.getScheduledStartAt();
        LocalDateTime end = request.getScheduledEndAt();
        LocalDateTime now = LocalDateTime.now(clock);
        validateTimeRange(start, end, now);
        if (start.equals(schedule.getScheduledStartAt()) && end.equals(schedule.getScheduledEndAt())) {
            throw new AppException(ErrorCode.ERR_MOVING_SCHEDULE_INVALID_TIME, "giờ mới trùng với giờ hiện tại của chuyến");
        }

        // Xe / nhân viên đang phân công phải còn rảnh ở khung giờ mới (bỏ qua chính chuyến này)
        List<Integer> vehicleIds = vehicleAssignmentRepository.findAllByMovingScheduleId(scheduleId).stream()
                .filter(a -> a.getStatus() != AssignmentStatus.CANCELLED)
                .map(a -> a.getVehicle().getId())
                .toList();
        List<Long> staffIds = staffAssignmentRepository.findAllByMovingScheduleId(scheduleId).stream()
                .filter(a -> a.getStatus() != AssignmentStatus.CANCELLED)
                .map(a -> a.getStaff().getId())
                .toList();
        if (!vehicleIds.isEmpty()) {
            lockAndCheckVehicles(vehicleIds, start, end, scheduleId);
        }
        if (!staffIds.isEmpty()) {
            lockAndCheckStaff(staffIds, start, end, scheduleId);
        }

        String before = describeRange(schedule.getScheduledStartAt(), schedule.getScheduledEndAt());
        schedule.setScheduledStartAt(start);
        schedule.setScheduledEndAt(end);
        // Giữ nguyên PLANNED / CONFIRMED (không dùng RESCHEDULED) để giám sát viên vẫn bắt đầu được chuyến đã xác nhận
        schedule.appendNote(TAG_RESCHEDULE, actorUserId,
                before + " → " + describeRange(start, end) + ". Lý do: " + request.getReason().trim(), now);
        movingScheduleRepository.save(schedule);
        log.info("Moving schedule id: {} rescheduled {} -> {} by user id: {}", scheduleId, before, describeRange(start, end), actorUserId);
        return toResponse(schedule);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ManagementMovingScheduleResponse cancel(Integer scheduleId, CancelMovingScheduleRequest request, Long actorUserId) {
        MovingSchedule schedule = loadForUpdate(scheduleId);
        if (!MovingScheduleCancellation.CANCELLABLE.contains(schedule.getStatus())) {
            throw new AppException(ErrorCode.ERR_MOVING_INVALID_STATUS);
        }
        movingScheduleCancellation.cancel(schedule, actorUserId, request.getReason().trim());

        // Đơn không còn chuyến nào chưa xong: SCHEDULED → về CONFIRMED để lập lịch lại;
        // IN_PROGRESS (các chuyến còn lại đều đã xong) → COMPLETED
        Integer bookingId = schedule.getBooking().getId();
        boolean hasUnfinished = movingScheduleRepository.existsByBookingIdAndIdNotAndStatusIn(
                bookingId, scheduleId, MovingScheduleStatus.UNFINISHED);
        if (!hasUnfinished) {
            BookingStatus bookingStatus = bookingWorkflowService.getSnapshot(bookingId).status();
            if (bookingStatus == BookingStatus.SCHEDULED) {
                bookingWorkflowService.changeStatus(bookingId, EnumSet.of(BookingStatus.SCHEDULED), BookingStatus.CONFIRMED,
                        actorUserId, "Hủy chuyến #" + scheduleId + ", đơn không còn chuyến nào: chờ lập lịch lại");
            } else if (bookingStatus == BookingStatus.IN_PROGRESS) {
                bookingWorkflowService.changeStatus(bookingId, EnumSet.of(BookingStatus.IN_PROGRESS), BookingStatus.COMPLETED,
                        actorUserId, "Hủy chuyến #" + scheduleId + ", các chuyến còn lại đã hoàn tất");
            }
        }
        return toResponse(schedule);
    }

    // ===================================================================== kiểm tra trùng (dùng chung)

    /**
     * Khóa từng xe (thứ tự ID tăng dần để tránh deadlock), kiểm tra xe tồn tại, không MAINTENANCE / INACTIVE và
     * không nằm trong chuyến chưa xong khác giao với [start, end). Trả về map id → xe.
     */
    private Map<Integer, Vehicle> lockAndCheckVehicles(Collection<Integer> vehicleIds, LocalDateTime start,
                                                       LocalDateTime end, Integer excludeScheduleId) {
        vehicleIds.stream().sorted().forEach(id -> movingScheduleRepository.acquireTransactionLock(LOCK_NAMESPACE_VEHICLE, id));

        Map<Integer, Vehicle> vehicles = vehicleRepository.findAllById(vehicleIds).stream()
                .collect(Collectors.toMap(Vehicle::getId, Function.identity()));
        for (Integer id : vehicleIds) {
            Vehicle vehicle = vehicles.get(id);
            if (vehicle == null) {
                throw new AppException(ErrorCode.ERR_VEHICLE_NOT_FOUND, id);
            }
            // IN_USE vẫn nhận: xe đang chạy chuyến khác lúc này nhưng có thể rảnh vào giờ của chuyến này
            if (!MovingScheduleQueryServiceImpl.ASSIGNABLE_VEHICLE_STATUSES.contains(vehicle.getStatus())) {
                throw new AppException(ErrorCode.ERR_MOVING_VEHICLE_UNAVAILABLE,
                        vehicle.getLicensePlate() + " đang " + vehicle.getStatus());
            }
        }

        List<Integer> conflicts = vehicleAssignmentRepository.findConflictingVehicleIds(vehicleIds, start, end, excludeScheduleId);
        if (!conflicts.isEmpty()) {
            throw new AppException(ErrorCode.ERR_MOVING_VEHICLE_CONFLICT,
                    conflicts.stream().map(id -> vehicles.get(id).getLicensePlate()).collect(Collectors.joining(", ")));
        }
        return vehicles;
    }

    /** Khóa từng nhân viên, kiểm tra là MOVER đang làm việc, không trùng chuyến chưa xong khác và lịch khảo sát. */
    private void lockAndCheckStaff(Collection<Long> staffIds, LocalDateTime start, LocalDateTime end, Integer excludeScheduleId) {
        staffIds.stream().sorted()
                .forEach(id -> movingScheduleRepository.acquireTransactionLock(LOCK_NAMESPACE_STAFF, Math.toIntExact(id)));

        for (Long staffId : staffIds) {
            // Nhân viên hiện trường (giám sát / bốc xếp / tài xế) đều mang role MOVER, giống phân công khảo sát
            if (!staffLookupService.isActiveStaffWithRole(staffId, RoleCode.MOVER)) {
                throw new AppException(ErrorCode.ERR_MOVING_STAFF_INVALID, staffId);
            }
        }

        List<Long> movingConflicts = staffAssignmentRepository.findConflictingStaffIds(staffIds, start, end, excludeScheduleId);
        if (!movingConflicts.isEmpty()) {
            throw new AppException(ErrorCode.ERR_MOVING_STAFF_CONFLICT, describeStaff(movingConflicts));
        }
        List<Long> surveyConflicts = staffIds.stream()
                .filter(id -> surveyStaffScheduleService.hasSurveyBetween(id, start, end))
                .toList();
        if (!surveyConflicts.isEmpty()) {
            throw new AppException(ErrorCode.ERR_MOVING_STAFF_SURVEY_CONFLICT, describeStaff(surveyConflicts));
        }
    }

    // ===================================================================== helper

    private void validateTimeRange(LocalDateTime start, LocalDateTime end, LocalDateTime now) {
        if (!end.isAfter(start)) {
            throw new AppException(ErrorCode.ERR_MOVING_SCHEDULE_INVALID_TIME, "giờ kết thúc phải sau giờ bắt đầu");
        }
        if (!start.isAfter(now)) {
            throw new AppException(ErrorCode.ERR_MOVING_SCHEDULE_INVALID_TIME, "giờ bắt đầu phải ở tương lai");
        }
        int maxHours = movingProperties.getMaxScheduleHours();
        if (Duration.between(start, end).compareTo(Duration.ofHours(maxHours)) > 0) {
            throw new AppException(ErrorCode.ERR_MOVING_SCHEDULE_INVALID_TIME,
                    "một chuyến tối đa " + maxHours + " giờ, chuyển nhiều ngày thì tạo nhiều chuyến");
        }
    }

    private MovingSchedule loadForUpdate(Integer id) {
        return movingScheduleRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new AppException(ErrorCode.ERR_MOVING_SCHEDULE_NOT_FOUND, id));
    }

    private MovingSchedule loadEditable(Integer id) {
        MovingSchedule schedule = loadForUpdate(id);
        if (!EDITABLE_SCHEDULE_STATUSES.contains(schedule.getStatus())) {
            throw new AppException(ErrorCode.ERR_MOVING_INVALID_STATUS);
        }
        return schedule;
    }

    // Chuyến đã xác nhận thì phân công mới cũng ở trạng thái xác nhận
    private static AssignmentStatus activeAssignmentStatus(MovingSchedule schedule) {
        return schedule.getStatus() == MovingScheduleStatus.CONFIRMED ? AssignmentStatus.CONFIRMED : AssignmentStatus.ASSIGNED;
    }

    private String describeStaff(Collection<Long> staffIds) {
        return staffIds.stream()
                .map(id -> staffLookupService.findSummary(id).map(StaffSummary::fullName).orElse("#" + id))
                .collect(Collectors.joining(", "));
    }

    private static String describeRange(LocalDateTime start, LocalDateTime end) {
        return start.format(MovingSchedule.NOTE_TIME_FORMAT) + " → " + end.format(MovingSchedule.NOTE_TIME_FORMAT);
    }

    private ManagementMovingScheduleResponse toResponse(MovingSchedule schedule) {
        BookingSnapshot booking = bookingWorkflowService.getSnapshot(schedule.getBooking().getId());
        List<Integer> ids = List.of(schedule.getId());
        return ManagementMovingScheduleResponse.builder()
                .bookingId(booking.id())
                .bookingCode(booking.bookingCode())
                .bookingStatus(booking.status())
                .schedule(movingScheduleMapper.toManagementTrip(schedule,
                        staffAssignmentRepository.findAllWithStaffByScheduleIds(ids),
                        vehicleAssignmentRepository.findAllWithVehicleByScheduleIds(ids)))
                .build();
    }
}
