package com.example.house_moving_service.modules.survey.mapper;

import com.example.house_moving_service.modules.survey.dto.response.SurveyTimeSlotResponse;
import com.example.house_moving_service.modules.survey.entity.SurveyTimeSlot;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;

@Component
public class SurveyMapper {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    public SurveyTimeSlotResponse toTimeSlotResponse(SurveyTimeSlot slot) {
        if (slot == null) {
            return null;
        }
        return SurveyTimeSlotResponse.builder()
                .id(slot.getId())
                .startTime(slot.getStartTime())
                .endTime(slot.getEndTime())
                .isActive(slot.getIsActive())
                .label(slot.getStartTime().format(TIME_FORMAT) + " - " + slot.getEndTime().format(TIME_FORMAT))
                .build();
    }
}
