package com.example.house_moving_service.modules.survey.service.impl;

import com.example.house_moving_service.common.exception.AppException;
import com.example.house_moving_service.common.exception.ErrorCode;
import com.example.house_moving_service.modules.survey.dto.request.SurveyTimeSlotRequest;
import com.example.house_moving_service.modules.survey.dto.response.SurveyTimeSlotResponse;
import com.example.house_moving_service.modules.survey.entity.SurveyTimeSlot;
import com.example.house_moving_service.modules.survey.enums.SurveyStatus;
import com.example.house_moving_service.modules.survey.mapper.SurveyMapper;
import com.example.house_moving_service.modules.survey.repository.BookingSurveyRepository;
import com.example.house_moving_service.modules.survey.repository.SurveyTimeSlotRepository;
import com.example.house_moving_service.modules.survey.service.SurveyTimeSlotService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class SurveyTimeSlotServiceImpl implements SurveyTimeSlotService {

    private final SurveyTimeSlotRepository surveyTimeSlotRepository;
    private final BookingSurveyRepository bookingSurveyRepository;
    private final SurveyMapper surveyMapper;

    @Override
    @Transactional(readOnly = true)
    public List<SurveyTimeSlotResponse> getAll(Boolean activeOnly) {
        List<SurveyTimeSlot> slots = Boolean.TRUE.equals(activeOnly)
                ? surveyTimeSlotRepository.findAllByIsActiveTrueOrderByStartTimeAsc()
                : surveyTimeSlotRepository.findAllByOrderByStartTimeAsc();
        return slots.stream().map(surveyMapper::toTimeSlotResponse).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SurveyTimeSlotResponse create(SurveyTimeSlotRequest request) {
        validateRange(request.getStartTime(), request.getEndTime());
        boolean active = !Boolean.FALSE.equals(request.getIsActive());

        // Trùng y hệt (kể cả khung giờ đã tắt) sẽ vi phạm uq_survey_time_slots_range: báo lỗi rõ ràng thay vì 500
        if (surveyTimeSlotRepository.existsByStartTimeAndEndTime(request.getStartTime(), request.getEndTime())) {
            throw new AppException(ErrorCode.ERR_SURVEY_TIME_SLOT_OVERLAP);
        }
        if (active) {
            ensureNoOverlap(request.getStartTime(), request.getEndTime(), null);
        }

        SurveyTimeSlot slot = surveyTimeSlotRepository.save(SurveyTimeSlot.builder()
                .startTime(request.getStartTime())
                .endTime(request.getEndTime())
                .isActive(active)
                .build());

        log.info("Created survey time slot id: {} ({} - {})", slot.getId(), slot.getStartTime(), slot.getEndTime());
        return surveyMapper.toTimeSlotResponse(slot);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SurveyTimeSlotResponse update(Integer id, SurveyTimeSlotRequest request) {
        SurveyTimeSlot slot = findSlot(id);
        validateRange(request.getStartTime(), request.getEndTime());

        boolean timeChanged = !slot.getStartTime().equals(request.getStartTime())
                || !slot.getEndTime().equals(request.getEndTime());
        // Đổi giờ khung giờ đã hẹn với khách sẽ làm lệch giờ các lịch đó
        if (timeChanged && bookingSurveyRepository.existsByTimeSlotIdAndStatusInAndSurveyDateGreaterThanEqual(
                id, SurveyStatus.OCCUPYING_SLOT, LocalDate.now())) {
            throw new AppException(ErrorCode.ERR_SURVEY_TIME_SLOT_IN_USE);
        }
        if (surveyTimeSlotRepository.existsByStartTimeAndEndTimeAndIdNot(request.getStartTime(), request.getEndTime(), id)) {
            throw new AppException(ErrorCode.ERR_SURVEY_TIME_SLOT_OVERLAP);
        }

        boolean active = request.getIsActive() != null ? request.getIsActive() : slot.getIsActive();
        if (active) {
            ensureNoOverlap(request.getStartTime(), request.getEndTime(), id);
        }

        slot.setStartTime(request.getStartTime());
        slot.setEndTime(request.getEndTime());
        slot.setIsActive(active);
        SurveyTimeSlot saved = surveyTimeSlotRepository.save(slot);

        log.info("Updated survey time slot id: {} ({} - {}, active: {})", id, saved.getStartTime(), saved.getEndTime(), active);
        return surveyMapper.toTimeSlotResponse(saved);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SurveyTimeSlotResponse toggle(Integer id) {
        SurveyTimeSlot slot = findSlot(id);
        boolean turningOn = !Boolean.TRUE.equals(slot.getIsActive());

        if (turningOn) {
            ensureNoOverlap(slot.getStartTime(), slot.getEndTime(), id);
        }
        slot.setIsActive(turningOn);
        SurveyTimeSlot saved = surveyTimeSlotRepository.save(slot);

        log.info("Toggled survey time slot id: {} -> active: {}", id, turningOn);
        return surveyMapper.toTimeSlotResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public SurveyTimeSlot getActiveSlot(Integer id) {
        SurveyTimeSlot slot = findSlot(id);
        if (!Boolean.TRUE.equals(slot.getIsActive())) {
            throw new AppException(ErrorCode.ERR_SURVEY_TIME_SLOT_INACTIVE);
        }
        return slot;
    }

    private SurveyTimeSlot findSlot(Integer id) {
        return surveyTimeSlotRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.ERR_SURVEY_TIME_SLOT_NOT_FOUND, id));
    }

    private void validateRange(LocalTime startTime, LocalTime endTime) {
        if (!endTime.isAfter(startTime)) {
            throw new AppException(ErrorCode.ERR_INVALID_INPUT, "Giờ kết thúc phải sau giờ bắt đầu");
        }
    }

    private void ensureNoOverlap(LocalTime startTime, LocalTime endTime, Integer excludeId) {
        if (surveyTimeSlotRepository.existsOverlapping(startTime, endTime, excludeId)) {
            throw new AppException(ErrorCode.ERR_SURVEY_TIME_SLOT_OVERLAP);
        }
    }
}
