package com.example.house_moving_service.modules.scheduling.service.impl;

import com.example.house_moving_service.modules.scheduling.entity.MovingSchedule;
import com.example.house_moving_service.modules.scheduling.enums.AssignmentStatus;
import com.example.house_moving_service.modules.scheduling.enums.MovingScheduleStatus;
import com.example.house_moving_service.modules.scheduling.repository.MovingScheduleRepository;
import com.example.house_moving_service.modules.scheduling.repository.ScheduleStaffAssignmentRepository;
import com.example.house_moving_service.modules.scheduling.repository.ScheduleVehicleAssignmentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.Set;

/**
 * Hủy một chuyến chưa bắt đầu cùng mọi phân công còn hiệu lực, ghi nhật ký CANCEL.
 * Dùng chung cho điều phối hủy chuyến và khi đơn hàng bị hủy. Người gọi đã khóa dòng chuyến và kiểm tra trạng thái.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class MovingScheduleCancellation {

    static final String TAG_CANCEL = "CANCEL";
    static final Set<MovingScheduleStatus> CANCELLABLE = EnumSet.of(
            MovingScheduleStatus.PLANNED, MovingScheduleStatus.CONFIRMED, MovingScheduleStatus.RESCHEDULED);

    private final MovingScheduleRepository movingScheduleRepository;
    private final ScheduleStaffAssignmentRepository staffAssignmentRepository;
    private final ScheduleVehicleAssignmentRepository vehicleAssignmentRepository;
    private final Clock clock;

    void cancel(MovingSchedule schedule, Long actorUserId, String reason) {
        schedule.setStatus(MovingScheduleStatus.CANCELLED);
        schedule.appendNote(TAG_CANCEL, actorUserId, reason, LocalDateTime.now(clock));
        movingScheduleRepository.save(schedule);

        staffAssignmentRepository.findAllByMovingScheduleId(schedule.getId()).stream()
                .filter(a -> a.getStatus() != AssignmentStatus.CANCELLED && a.getStatus() != AssignmentStatus.COMPLETED)
                .forEach(a -> a.setStatus(AssignmentStatus.CANCELLED));
        vehicleAssignmentRepository.findAllByMovingScheduleId(schedule.getId()).stream()
                .filter(a -> a.getStatus() != AssignmentStatus.CANCELLED && a.getStatus() != AssignmentStatus.COMPLETED)
                .forEach(a -> a.setStatus(AssignmentStatus.CANCELLED));

        log.info("Cancelled moving schedule id: {} (booking id: {}) by user id: {}",
                schedule.getId(), schedule.getBooking().getId(), actorUserId);
    }
}
