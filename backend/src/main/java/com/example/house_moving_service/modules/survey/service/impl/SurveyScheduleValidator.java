package com.example.house_moving_service.modules.survey.service.impl;

import com.example.house_moving_service.common.exception.AppException;
import com.example.house_moving_service.common.exception.ErrorCode;
import com.example.house_moving_service.modules.identity.enums.RoleCode;
import com.example.house_moving_service.modules.identity.service.StaffLookupService;
import com.example.house_moving_service.modules.survey.entity.SurveyTimeSlot;
import com.example.house_moving_service.modules.survey.enums.SurveyStatus;
import com.example.house_moving_service.modules.survey.repository.BookingSurveyRepository;
import com.example.house_moving_service.modules.survey.service.StaffExternalScheduleChecker;
import com.example.house_moving_service.modules.survey.service.SurveyTimeSlotService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * Kiểm tra lịch khảo sát dùng chung cho tạo / phân công / đổi lịch (task 4.6 mục 1.2).
 * Chỉ chạy những bước đủ dữ liệu: thiếu ngày / khung giờ / nhân viên thì bỏ qua bước cần field đó.
 * Lớp chặn cuối cho trùng lịch khảo sát là unique index uq_booking_surveys_staff_slot;
 * trùng với lịch chuyển nhà thì DB không chặn được, chỉ dựa vào StaffExternalScheduleChecker.
 */
@Component
@RequiredArgsConstructor
class SurveyScheduleValidator {

    private final SurveyTimeSlotService surveyTimeSlotService;
    private final StaffLookupService staffLookupService;
    private final BookingSurveyRepository bookingSurveyRepository;
    private final ObjectProvider<StaffExternalScheduleChecker> externalScheduleChecker;
    private final Clock clock;

    /** Ném AppException nếu lịch không hợp lệ; trả về khung giờ đã kiểm tra (null nếu không có timeSlotId). */
    SurveyTimeSlot validate(ScheduleCheck check) {
        // Lấy khung giờ trước vì bước kiểm tra ngày cần giờ bắt đầu của khung giờ
        SurveyTimeSlot slot = check.timeSlotId() != null
                ? surveyTimeSlotService.getActiveSlot(check.timeSlotId())
                : null;

        if (check.surveyDate() != null) {
            ensureNotInPast(check.surveyDate(), slot);
            ensureBeforeMovingDate(check.surveyDate(), check.bookingDesiredStartAt());
        }

        if (check.staffId() != null) {
            ensureActiveMover(check.staffId());
            if (check.surveyDate() != null && slot != null) {
                ensureNoSurveyConflict(check, slot);
                ensureNotBusyElsewhere(check.staffId(), check.surveyDate(), slot);
            }
        }
        return slot;
    }

    private void ensureNotInPast(LocalDate surveyDate, SurveyTimeSlot slot) {
        LocalDate today = LocalDate.now(clock);
        if (surveyDate.isBefore(today)) {
            throw new AppException(ErrorCode.ERR_SURVEY_INVALID_DATE, "ngày khảo sát đã qua");
        }
        if (surveyDate.equals(today) && slot != null && !slot.getStartTime().isAfter(LocalTime.now(clock))) {
            throw new AppException(ErrorCode.ERR_SURVEY_INVALID_DATE, "khung giờ hôm nay đã bắt đầu");
        }
    }

    private void ensureBeforeMovingDate(LocalDate surveyDate, LocalDateTime desiredStartAt) {
        if (desiredStartAt != null && !surveyDate.isBefore(desiredStartAt.toLocalDate())) {
            throw new AppException(ErrorCode.ERR_SURVEY_INVALID_DATE, "ngày khảo sát phải trước ngày chuyển nhà");
        }
    }

    private void ensureActiveMover(Long staffId) {
        if (!staffLookupService.isActiveStaffWithRole(staffId, RoleCode.MOVER)) {
            throw new AppException(ErrorCode.ERR_SURVEY_STAFF_INVALID, staffId);
        }
    }

    private void ensureNoSurveyConflict(ScheduleCheck check, SurveyTimeSlot slot) {
        if (bookingSurveyRepository.existsSurveySlotConflict(check.staffId(), check.surveyDate(), slot.getId(),
                SurveyStatus.OCCUPYING_SLOT, check.excludeSurveyId())) {
            throw new AppException(ErrorCode.ERR_SURVEY_STAFF_CONFLICT);
        }
    }

    private void ensureNotBusyElsewhere(Long staffId, LocalDate surveyDate, SurveyTimeSlot slot) {
        StaffExternalScheduleChecker checker = externalScheduleChecker.getIfAvailable();
        if (checker == null) {
            return;
        }
        LocalDateTime start = surveyDate.atTime(slot.getStartTime());
        LocalDateTime end = surveyDate.atTime(slot.getEndTime());
        if (checker.isBusy(staffId, start, end)) {
            throw new AppException(ErrorCode.ERR_SURVEY_STAFF_BUSY);
        }
    }
}
