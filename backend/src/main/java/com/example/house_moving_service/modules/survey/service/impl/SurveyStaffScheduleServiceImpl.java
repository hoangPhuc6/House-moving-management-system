package com.example.house_moving_service.modules.survey.service.impl;

import com.example.house_moving_service.modules.survey.entity.BookingSurvey;
import com.example.house_moving_service.modules.survey.enums.SurveyStatus;
import com.example.house_moving_service.modules.survey.repository.BookingSurveyRepository;
import com.example.house_moving_service.modules.survey.service.SurveyStaffScheduleService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

// Chỉ phụ thuộc repository: module Lập lịch gọi vào đây, giữ bean nhỏ để không tạo vòng bean
@Service
@RequiredArgsConstructor
public class SurveyStaffScheduleServiceImpl implements SurveyStaffScheduleService {

    private final BookingSurveyRepository bookingSurveyRepository;

    @Override
    @Transactional(readOnly = true)
    public boolean hasSurveyBetween(Long staffId, LocalDateTime start, LocalDateTime end) {
        if (!end.isAfter(start)) {
            return false;
        }
        return bookingSurveyRepository.findByStaffAndDateRange(
                        staffId, start.toLocalDate(), end.toLocalDate(), SurveyStatus.OCCUPYING_SLOT)
                .stream()
                .anyMatch(survey -> overlaps(survey, start, end));
    }

    private boolean overlaps(BookingSurvey survey, LocalDateTime start, LocalDateTime end) {
        LocalDateTime surveyStart = survey.getSurveyDate().atTime(survey.getTimeSlot().getStartTime());
        LocalDateTime surveyEnd = survey.getSurveyDate().atTime(survey.getTimeSlot().getEndTime());
        return surveyStart.isBefore(end) && surveyEnd.isAfter(start);
    }
}
