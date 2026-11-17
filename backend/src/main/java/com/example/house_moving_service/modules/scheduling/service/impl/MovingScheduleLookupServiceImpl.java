package com.example.house_moving_service.modules.scheduling.service.impl;

import com.example.house_moving_service.common.exception.AppException;
import com.example.house_moving_service.common.exception.ErrorCode;
import com.example.house_moving_service.modules.scheduling.dto.response.MovingScheduleSnapshot;
import com.example.house_moving_service.modules.scheduling.entity.MovingSchedule;
import com.example.house_moving_service.modules.scheduling.enums.AssignmentStatus;
import com.example.house_moving_service.modules.scheduling.repository.MovingScheduleRepository;
import com.example.house_moving_service.modules.scheduling.repository.ScheduleStaffAssignmentRepository;
import com.example.house_moving_service.modules.scheduling.service.MovingScheduleLookupService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MovingScheduleLookupServiceImpl implements MovingScheduleLookupService {

    private final MovingScheduleRepository movingScheduleRepository;
    private final ScheduleStaffAssignmentRepository staffAssignmentRepository;

    @Override
    public MovingSchedule getReference(Integer scheduleId) {
        if (scheduleId == null || !movingScheduleRepository.existsById(scheduleId)) {
            throw new AppException(ErrorCode.ERR_MOVING_SCHEDULE_NOT_FOUND, scheduleId);
        }
        return movingScheduleRepository.getReferenceById(scheduleId);
    }

    @Override
    @Transactional(readOnly = true)
    public MovingScheduleSnapshot getSnapshot(Integer scheduleId) {
        if (scheduleId == null) {
            throw new AppException(ErrorCode.ERR_MOVING_SCHEDULE_NOT_FOUND, (Object) null);
        }
        return toSnapshot(movingScheduleRepository.findById(scheduleId)
                .orElseThrow(() -> new AppException(ErrorCode.ERR_MOVING_SCHEDULE_NOT_FOUND, scheduleId)));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MovingScheduleSnapshot lockForUpdate(Integer scheduleId) {
        if (scheduleId == null) {
            throw new AppException(ErrorCode.ERR_MOVING_SCHEDULE_NOT_FOUND, (Object) null);
        }
        return toSnapshot(movingScheduleRepository.findByIdForUpdate(scheduleId)
                .orElseThrow(() -> new AppException(ErrorCode.ERR_MOVING_SCHEDULE_NOT_FOUND, scheduleId)));
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isAssigned(Integer scheduleId, Long userId) {
        if (scheduleId == null || userId == null) {
            return false;
        }
        // staff.id = users.id; dòng phân công unique theo chuyến + nhân viên
        return staffAssignmentRepository.findByMovingScheduleIdAndStaffId(scheduleId, userId)
                .map(a -> a.getStatus() != AssignmentStatus.CANCELLED)
                .orElse(false);
    }

    private static MovingScheduleSnapshot toSnapshot(MovingSchedule schedule) {
        return MovingScheduleSnapshot.builder()
                .id(schedule.getId())
                .bookingId(schedule.getBooking() != null ? schedule.getBooking().getId() : null)
                .status(schedule.getStatus())
                .build();
    }
}
