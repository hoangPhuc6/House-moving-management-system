package com.example.house_moving_service.modules.scheduling.service.impl;

import com.example.house_moving_service.modules.scheduling.repository.ScheduleStaffAssignmentRepository;
import com.example.house_moving_service.modules.survey.service.StaffExternalScheduleChecker;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Cài đặt interface của module survey: khi điều phối xếp lịch khảo sát, survey hỏi nhân viên có đang bận chuyến
 * vận chuyển chưa xong nào trùng giờ không. Theo yêu cầu của survey, bean này không phụ thuộc SurveyStaffScheduleService.
 */
@Component
@RequiredArgsConstructor
public class MovingStaffScheduleChecker implements StaffExternalScheduleChecker {

    // ID chuyến luôn >= 1 nên 0 nghĩa là không loại trừ chuyến nào
    private static final int NO_EXCLUDED_SCHEDULE = 0;

    private final ScheduleStaffAssignmentRepository staffAssignmentRepository;

    @Override
    @Transactional(readOnly = true)
    public boolean isBusy(Long staffId, LocalDateTime start, LocalDateTime end) {
        if (staffId == null || start == null || end == null || !end.isAfter(start)) {
            return false;
        }
        return !staffAssignmentRepository.findConflictingStaffIds(List.of(staffId), start, end, NO_EXCLUDED_SCHEDULE).isEmpty();
    }
}
