package com.example.house_moving_service.modules.survey.service;

import com.example.house_moving_service.modules.survey.entity.BookingSurvey;
import com.example.house_moving_service.modules.survey.entity.SurveyTimeSlot;
import com.example.house_moving_service.modules.survey.enums.SurveyStatus;
import com.example.house_moving_service.modules.survey.repository.BookingSurveyRepository;
import com.example.house_moving_service.modules.survey.service.impl.SurveyStaffScheduleServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SurveyStaffScheduleServiceTest {

    private static final long STAFF_ID = 8L;
    private static final LocalDate DAY = LocalDate.of(2026, 11, 2);

    @Mock
    private BookingSurveyRepository bookingSurveyRepository;

    private SurveyStaffScheduleServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new SurveyStaffScheduleServiceImpl(bookingSurveyRepository);
    }

    private static BookingSurvey survey(LocalDate date, String start, String end) {
        SurveyTimeSlot slot = SurveyTimeSlot.builder()
                .startTime(LocalTime.parse(start)).endTime(LocalTime.parse(end)).build();
        return BookingSurvey.builder().surveyDate(date).timeSlot(slot).status(SurveyStatus.CONFIRMED).build();
    }

    private void givenSurveys(LocalDate from, LocalDate to, BookingSurvey... surveys) {
        when(bookingSurveyRepository.findByStaffAndDateRange(STAFF_ID, from, to, SurveyStatus.OCCUPYING_SLOT))
                .thenReturn(List.of(surveys));
    }

    @Test
    @DisplayName("Khảo sát 08:00–10:00 giao với khoảng 08:00–12:00 → true")
    void testHasSurveyBetween_Overlap() {
        givenSurveys(DAY, DAY, survey(DAY, "08:00", "10:00"));

        assertThat(service.hasSurveyBetween(STAFF_ID, DAY.atTime(8, 0), DAY.atTime(12, 0))).isTrue();
    }

    @Test
    @DisplayName("Khảo sát 08:00–10:00 vs khoảng 10:00–12:00 (chạm biên) → false")
    void testHasSurveyBetween_TouchingBoundary() {
        givenSurveys(DAY, DAY, survey(DAY, "08:00", "10:00"));

        assertThat(service.hasSurveyBetween(STAFF_ID, DAY.atTime(10, 0), DAY.atTime(12, 0))).isFalse();
    }

    @Test
    @DisplayName("Khoảng kéo qua 2 ngày tìm thấy lịch của ngày thứ 2")
    void testHasSurveyBetween_SpansTwoDays() {
        LocalDate nextDay = DAY.plusDays(1);
        givenSurveys(DAY, nextDay, survey(nextDay, "08:00", "10:00"));

        LocalDateTime start = DAY.atTime(20, 0);
        LocalDateTime end = nextDay.atTime(9, 0);

        assertThat(service.hasSurveyBetween(STAFF_ID, start, end)).isTrue();
    }

    @Test
    @DisplayName("Không có lịch khảo sát → false; chỉ hỏi các trạng thái đang giữ khung giờ")
    void testHasSurveyBetween_NoSurvey() {
        givenSurveys(DAY, DAY);

        assertThat(service.hasSurveyBetween(STAFF_ID, DAY.atTime(8, 0), DAY.atTime(12, 0))).isFalse();
        verify(bookingSurveyRepository).findByStaffAndDateRange(STAFF_ID, DAY, DAY, SurveyStatus.OCCUPYING_SLOT);
    }

    @Test
    @DisplayName("Khoảng rỗng (end <= start) → false, không truy vấn")
    void testHasSurveyBetween_EmptyRange() {
        assertThat(service.hasSurveyBetween(STAFF_ID, DAY.atTime(10, 0), DAY.atTime(10, 0))).isFalse();
        verifyNoInteractions(bookingSurveyRepository);
    }
}
