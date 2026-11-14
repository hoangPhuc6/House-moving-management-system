package com.example.house_moving_service.modules.scheduling.service.impl;

import com.example.house_moving_service.common.exception.AppException;
import com.example.house_moving_service.common.exception.ErrorCode;
import com.example.house_moving_service.modules.booking.dto.response.BookingSnapshot;
import com.example.house_moving_service.modules.booking.enums.BookingStatus;
import com.example.house_moving_service.modules.booking.service.BookingWorkflowService;
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
import com.example.house_moving_service.modules.scheduling.helper.MovingLogParser;
import com.example.house_moving_service.modules.scheduling.mapper.MovingScheduleMapper;
import com.example.house_moving_service.modules.scheduling.mapper.SurchargeMapper;
import com.example.house_moving_service.modules.scheduling.repository.BookingSurchargeRepository;
import com.example.house_moving_service.modules.scheduling.repository.MovingScheduleRepository;
import com.example.house_moving_service.modules.scheduling.repository.ScheduleStaffAssignmentRepository;
import com.example.house_moving_service.modules.scheduling.repository.ScheduleVehicleAssignmentRepository;
import com.example.house_moving_service.modules.scheduling.service.FieldMovingScheduleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@Slf4j
@RequiredArgsConstructor
public class FieldMovingScheduleServiceImpl implements FieldMovingScheduleService {

    private static final Set<BookingStatus> CLOSED_BOOKING_STATUSES = EnumSet.of(BookingStatus.CANCELLED, BookingStatus.COMPLETED);
    // Cho phép đi từ CONFIRMED vì hiện chưa có bước xếp lịch chuyển đơn sang SCHEDULED
    private static final Set<BookingStatus> STARTABLE_BOOKING_STATUSES = EnumSet.of(BookingStatus.SCHEDULED, BookingStatus.CONFIRMED);

    private final MovingScheduleRepository movingScheduleRepository;
    private final ScheduleStaffAssignmentRepository staffAssignmentRepository;
    private final ScheduleVehicleAssignmentRepository vehicleAssignmentRepository;
    private final BookingWorkflowService bookingWorkflowService;
    private final BookingSurchargeRepository surchargeRepository;
    private final MovingScheduleMapper movingScheduleMapper;
    private final MovingProperties movingProperties;
    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    public FieldMovingScheduleResponse getDetail(Integer id, Long userId) {
        MovingSchedule schedule = movingScheduleRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.ERR_MOVING_SCHEDULE_NOT_FOUND, id));
        ScheduleStaffAssignment mine = requireAssigned(id, userId);
        return toFieldResponse(schedule, mine.getRole());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FieldMovingScheduleResponse start(Integer id, MovingNoteRequest request, Long userId) {
        MovingSchedule schedule = loadForUpdate(id);
        requireSupervisor(id, userId);
        if (schedule.getStatus() != MovingScheduleStatus.CONFIRMED) {
            throw new AppException(ErrorCode.ERR_MOVING_INVALID_STATUS);
        }
        Integer bookingId = schedule.getBooking().getId();
        BookingSnapshot booking = bookingWorkflowService.getSnapshot(bookingId);
        if (CLOSED_BOOKING_STATUSES.contains(booking.status())) {
            throw new AppException(ErrorCode.ERR_MOVING_BOOKING_INVALID_STATUS);
        }
        LocalDateTime now = LocalDateTime.now(clock);
        ensureCanStartAt(schedule, now);
        ensureNoPendingAdjustment(bookingId);

        schedule.setStatus(MovingScheduleStatus.IN_PROGRESS);
        schedule.setActualStartAt(now);
        schedule.appendNote(MovingLogParser.TAG_START, userId, noteOrDefault(request, "Bắt đầu chuyến"), now);
        movingScheduleRepository.saveAndFlush(schedule);

        // Chỉ xe đang rảnh mới chuyển IN_USE; xe MAINTENANCE / INACTIVE giữ nguyên và không chặn thao tác
        activeVehicleAssignments(id).stream()
                .map(ScheduleVehicleAssignment::getVehicle)
                .filter(v -> v.getStatus() == VehicleStatus.AVAILABLE)
                .forEach(v -> v.setStatus(VehicleStatus.IN_USE));

        // Đơn đã IN_PROGRESS (chuyến thứ hai trở đi) thì changeStatus bỏ qua
        bookingWorkflowService.changeStatus(bookingId, STARTABLE_BOOKING_STATUSES, BookingStatus.IN_PROGRESS, userId,
                "Bắt đầu vận chuyển (chuyến #" + id + ")");

        log.info("Moving schedule id: {} started by supervisor id: {} at {}", id, userId, now);
        return toFieldResponse(schedule, StaffAssignmentRole.SUPERVISOR);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FieldMovingScheduleResponse reportProgress(Integer id, MovingProgressRequest request, Long userId) {
        MovingSchedule schedule = loadInProgressAsSupervisor(id, userId);
        ProgressMilestone milestone = request.getMilestone();
        String note = request.getNote() != null && !request.getNote().isBlank() ? request.getNote().trim() : null;
        if (milestone == ProgressMilestone.OTHER && note == null) {
            throw new AppException(ErrorCode.ERR_INVALID_INPUT, "mốc OTHER phải có ghi chú");
        }

        LocalDateTime now = LocalDateTime.now(clock);
        String content = milestone == ProgressMilestone.OTHER ? note
                : note != null ? milestone.getLabel() + ": " + note : milestone.getLabel();
        schedule.appendNote(milestone.name(), userId, content, now);
        movingScheduleRepository.saveAndFlush(schedule);

        log.info("Moving schedule id: {} progress {} reported by supervisor id: {}", id, milestone, userId);
        return toFieldResponse(schedule, StaffAssignmentRole.SUPERVISOR);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FieldMovingScheduleResponse complete(Integer id, MovingNoteRequest request, Long userId) {
        MovingSchedule schedule = loadInProgressAsSupervisor(id, userId);
        LocalDateTime now = LocalDateTime.now(clock);

        schedule.setStatus(MovingScheduleStatus.COMPLETED);
        schedule.setActualEndAt(now);
        schedule.appendNote(MovingLogParser.TAG_COMPLETE, userId, noteOrDefault(request, "Hoàn tất chuyến"), now);
        movingScheduleRepository.saveAndFlush(schedule);

        activeStaffAssignments(id).forEach(a -> a.setStatus(AssignmentStatus.COMPLETED));
        for (ScheduleVehicleAssignment assignment : activeVehicleAssignments(id)) {
            assignment.setStatus(AssignmentStatus.COMPLETED);
            Vehicle vehicle = assignment.getVehicle();
            if (vehicle.getStatus() == VehicleStatus.IN_USE) {
                vehicle.setStatus(VehicleStatus.AVAILABLE);
            }
        }

        Integer bookingId = schedule.getBooking().getId();
        boolean hasUnfinished = movingScheduleRepository.existsByBookingIdAndIdNotAndStatusIn(
                bookingId, id, MovingScheduleStatus.UNFINISHED);
        if (!hasUnfinished && bookingWorkflowService.getSnapshot(bookingId).status() == BookingStatus.IN_PROGRESS) {
            bookingWorkflowService.changeStatus(bookingId, EnumSet.of(BookingStatus.IN_PROGRESS), BookingStatus.COMPLETED,
                    userId, "Hoàn tất vận chuyển (chuyến #" + id + ")");
        }

        log.info("Moving schedule id: {} completed by supervisor id: {} at {} (booking completed: {})",
                id, userId, now, !hasUnfinished);
        return toFieldResponse(schedule, StaffAssignmentRole.SUPERVISOR);
    }

    // Chuyến đã khóa, người gọi là SUPERVISOR, đang IN_PROGRESS: điều kiện chung của báo mốc và hoàn tất
    private MovingSchedule loadInProgressAsSupervisor(Integer id, Long userId) {
        MovingSchedule schedule = loadForUpdate(id);
        requireSupervisor(id, userId);
        if (schedule.getStatus() != MovingScheduleStatus.IN_PROGRESS) {
            throw new AppException(ErrorCode.ERR_MOVING_INVALID_STATUS);
        }
        return schedule;
    }

    private MovingSchedule loadForUpdate(Integer id) {
        return movingScheduleRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new AppException(ErrorCode.ERR_MOVING_SCHEDULE_NOT_FOUND, id));
    }

    // Có dòng phân công chưa hủy trên chuyến
    private ScheduleStaffAssignment requireAssigned(Integer scheduleId, Long userId) {
        return staffAssignmentRepository.findByMovingScheduleIdAndStaffId(scheduleId, userId)
                .filter(a -> a.getStatus() != AssignmentStatus.CANCELLED)
                .orElseThrow(() -> new AppException(ErrorCode.ERR_MOVING_FORBIDDEN));
    }

    private void requireSupervisor(Integer scheduleId, Long userId) {
        if (requireAssigned(scheduleId, userId).getRole() != StaffAssignmentRole.SUPERVISOR) {
            throw new AppException(ErrorCode.ERR_MOVING_NOT_SUPERVISOR);
        }
    }

    // Chỉ trong ngày của giờ hẹn, sớm nhất earlyStartMinutes phút trước giờ hẹn; bắt đầu muộn thì không chặn
    private void ensureCanStartAt(MovingSchedule schedule, LocalDateTime now) {
        LocalDateTime scheduledStart = schedule.getScheduledStartAt();
        if (!scheduledStart.toLocalDate().equals(now.toLocalDate())) {
            throw new AppException(ErrorCode.ERR_MOVING_INVALID_TIME, "chỉ bắt đầu được trong ngày của giờ hẹn");
        }
        if (now.isBefore(scheduledStart.minusMinutes(movingProperties.getEarlyStartMinutes()))) {
            throw new AppException(ErrorCode.ERR_MOVING_INVALID_TIME, "quá sớm so với giờ hẹn");
        }
    }

    // Khóa luồng: còn báo giá điều chỉnh chưa được khách xác nhận thì chưa được bắt đầu; trả kèm id các khoản đang chặn
    private void ensureNoPendingAdjustment(Integer bookingId) {
        List<Integer> blockingIds = surchargeRepository.findIdsByBookingIdAndStatusIn(bookingId, SurchargeStatus.BLOCKING_START);
        if (!blockingIds.isEmpty()) {
            throw AppException.withDetails(ErrorCode.ERR_MOVING_PENDING_ADJUSTMENT, Map.of("blockingSurchargeIds", blockingIds));
        }
    }

    private List<ScheduleStaffAssignment> activeStaffAssignments(Integer scheduleId) {
        return staffAssignmentRepository.findAllWithStaffByScheduleIds(List.of(scheduleId)).stream()
                .filter(a -> a.getStatus() != AssignmentStatus.CANCELLED)
                .toList();
    }

    private List<ScheduleVehicleAssignment> activeVehicleAssignments(Integer scheduleId) {
        return vehicleAssignmentRepository.findAllWithVehicleByScheduleIds(List.of(scheduleId)).stream()
                .filter(a -> a.getStatus() != AssignmentStatus.CANCELLED)
                .toList();
    }

    private FieldMovingScheduleResponse toFieldResponse(MovingSchedule schedule, StaffAssignmentRole myRole) {
        Integer bookingId = schedule.getBooking().getId();
        return movingScheduleMapper.toFieldResponse(schedule,
                bookingWorkflowService.getSnapshot(bookingId),
                bookingWorkflowService.getRoute(bookingId),
                myRole,
                activeStaffAssignments(schedule.getId()),
                activeVehicleAssignments(schedule.getId()),
                SurchargeMapper.uncollectedTotal(surchargeRepository.findAllByBookingIdOrderByRequestedAtAscIdAsc(bookingId)));
    }

    private static String noteOrDefault(MovingNoteRequest request, String defaultContent) {
        String note = request != null ? request.getNote() : null;
        return note != null && !note.isBlank() ? note.trim() : defaultContent;
    }
}
