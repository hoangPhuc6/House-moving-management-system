package com.example.house_moving_service.modules.survey.repository;

import com.example.house_moving_service.modules.survey.entity.SurveyTimeSlot;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// Dựa trên 4 khung giờ seed V12: 08:00-10:00, 10:00-12:00, 13:30-15:30, 15:30-17:30
@SpringBootTest
@Transactional
class SurveyTimeSlotRepositoryTest {

    @Autowired
    private SurveyTimeSlotRepository surveyTimeSlotRepository;

    private SurveyTimeSlot slot(String start) {
        return surveyTimeSlotRepository.findAllByOrderByStartTimeAsc().stream()
                .filter(s -> s.getStartTime().equals(LocalTime.parse(start)))
                .findFirst().orElseThrow();
    }

    @Test
    @DisplayName("Seed V12: 4 khung giờ sắp theo giờ bắt đầu, đều đang dùng")
    void testSeedSlots() {
        List<SurveyTimeSlot> active = surveyTimeSlotRepository.findAllByIsActiveTrueOrderByStartTimeAsc();

        assertThat(active).extracting(SurveyTimeSlot::getStartTime)
                .containsSubsequence(LocalTime.of(8, 0), LocalTime.of(10, 0), LocalTime.of(13, 30), LocalTime.of(15, 30));
        assertThat(surveyTimeSlotRepository.existsByStartTimeAndEndTime(LocalTime.of(8, 0), LocalTime.of(10, 0))).isTrue();
    }

    @Test
    @DisplayName("existsOverlapping: chồng giữa 2 khung giờ → true; chạm biên → false")
    void testExistsOverlapping() {
        assertThat(surveyTimeSlotRepository.existsOverlapping(LocalTime.of(9, 0), LocalTime.of(11, 0), null)).isTrue();
        assertThat(surveyTimeSlotRepository.existsOverlapping(LocalTime.of(12, 0), LocalTime.of(13, 0), null)).isFalse();
        assertThat(surveyTimeSlotRepository.existsOverlapping(LocalTime.of(17, 30), LocalTime.of(19, 0), null)).isFalse();
    }

    @Test
    @DisplayName("existsOverlapping: khi sửa khung giờ thì bỏ qua chính nó (excludeId)")
    void testExistsOverlapping_ExcludeSelf() {
        SurveyTimeSlot morning = slot("08:00");

        // Thu hẹp 08:00-10:00 thành 08:00-09:30: chỉ chồng với chính nó
        assertThat(surveyTimeSlotRepository.existsOverlapping(LocalTime.of(8, 0), LocalTime.of(9, 30), morning.getId())).isFalse();
        assertThat(surveyTimeSlotRepository.existsOverlapping(LocalTime.of(8, 0), LocalTime.of(9, 30), null)).isTrue();
    }

    @Test
    @DisplayName("existsOverlapping: khung giờ đã tắt không tính")
    void testExistsOverlapping_IgnoresInactive() {
        SurveyTimeSlot morning = slot("08:00");
        morning.setIsActive(false);
        surveyTimeSlotRepository.saveAndFlush(morning);

        assertThat(surveyTimeSlotRepository.existsOverlapping(LocalTime.of(8, 30), LocalTime.of(9, 30), null)).isFalse();
    }

    @Test
    @DisplayName("Giờ kết thúc không sau giờ bắt đầu vi phạm chk_survey_time_slots_range")
    void testEndNotAfterStart_ViolatesCheck() {
        assertThatThrownBy(() -> surveyTimeSlotRepository.saveAndFlush(SurveyTimeSlot.builder()
                .startTime(LocalTime.of(19, 0)).endTime(LocalTime.of(18, 0)).build()))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("chk_survey_time_slots_range");
    }
}
