package com.example.house_moving_service.modules.survey.repository;

import com.example.house_moving_service.modules.survey.entity.SurveyTimeSlot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SurveyTimeSlotRepository extends JpaRepository<SurveyTimeSlot, Integer> {

    List<SurveyTimeSlot> findAllByIsActiveTrueOrderByStartTimeAsc();
}
