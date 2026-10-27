package com.example.house_moving_service.modules.survey.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class BookingSurveyTest {

    private static final LocalDateTime AT = LocalDateTime.of(2026, 10, 26, 10, 5);

    @Test
    @DisplayName("appendNote: note trống thì ghi dòng đầu tiên đúng định dạng")
    void testAppendNote_FirstLine() {
        BookingSurvey survey = new BookingSurvey();

        survey.appendNote("CANCEL", 5L, "  khách bận  ", AT);

        assertThat(survey.getNote()).isEqualTo("[2026-10-26 10:05][CANCEL][user#5] khách bận");
    }

    @Test
    @DisplayName("appendNote: nối thêm dòng mới, không ghi đè nội dung cũ")
    void testAppendNote_KeepsExistingNote() {
        BookingSurvey survey = BookingSurvey.builder().note("Khách dặn gọi trước").build();

        survey.appendNote("RESCHEDULE", 3L, "08:00 -> 10:00", AT);
        survey.appendNote("FAIL", null, "Khách vắng nhà", AT.plusHours(2));

        assertThat(survey.getNote()).isEqualTo("""
                Khách dặn gọi trước
                [2026-10-26 10:05][RESCHEDULE][user#3] 08:00 -> 10:00
                [2026-10-26 12:05][FAIL][system] Khách vắng nhà""");
    }
}
