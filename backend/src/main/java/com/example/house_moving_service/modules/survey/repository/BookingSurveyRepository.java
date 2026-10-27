package com.example.house_moving_service.modules.survey.repository;

import com.example.house_moving_service.modules.survey.entity.BookingSurvey;
import com.example.house_moving_service.modules.survey.enums.SurveyStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface BookingSurveyRepository extends JpaRepository<BookingSurvey, Integer> {

    List<BookingSurvey> findAllByBookingIdOrderByCreatedAtDesc(Integer bookingId);

    Optional<BookingSurvey> findByIdAndBookingId(Integer id, Integer bookingId);

    boolean existsByBookingIdAndStatus(Integer bookingId, SurveyStatus status);

    boolean existsByBookingIdAndStatusIn(Integer bookingId, Collection<SurveyStatus> statuses);

    boolean existsBySurveyStaffIdAndSurveyDateAndTimeSlotIdAndStatusIn(
            Long staffId, LocalDate surveyDate, Integer timeSlotId, Collection<SurveyStatus> statuses);
}
