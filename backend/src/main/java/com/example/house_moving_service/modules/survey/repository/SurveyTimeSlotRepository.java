package com.example.house_moving_service.modules.survey.repository;

import com.example.house_moving_service.modules.survey.entity.SurveyTimeSlot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalTime;
import java.util.List;

@Repository
public interface SurveyTimeSlotRepository extends JpaRepository<SurveyTimeSlot, Integer> {

    List<SurveyTimeSlot> findAllByOrderByStartTimeAsc();

    List<SurveyTimeSlot> findAllByIsActiveTrueOrderByStartTimeAsc();

    boolean existsByStartTimeAndEndTime(LocalTime startTime, LocalTime endTime);

    // Trùng y hệt với khung giờ khác (kể cả đã tắt) khi sửa; id luôn có giá trị nên dùng ...AndIdNot được
    boolean existsByStartTimeAndEndTimeAndIdNot(LocalTime startTime, LocalTime endTime, Integer id);

    // Chồng lấn với một khung giờ đang dùng khác; chạm biên (10:00-12:00 và 12:00-13:00) không tính.
    // excludeId: id của chính khung giờ đang sửa, null khi tạo mới
    @Query("""
            SELECT COUNT(s) > 0 FROM SurveyTimeSlot s
            WHERE s.isActive = true
              AND (:excludeId IS NULL OR s.id <> :excludeId)
              AND s.startTime < :endTime AND s.endTime > :startTime
            """)
    boolean existsOverlapping(@Param("startTime") LocalTime startTime,
                              @Param("endTime") LocalTime endTime,
                              @Param("excludeId") Integer excludeId);
}
