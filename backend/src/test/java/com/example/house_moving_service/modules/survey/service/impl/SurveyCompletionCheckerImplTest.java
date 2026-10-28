package com.example.house_moving_service.modules.survey.service.impl;

import com.example.house_moving_service.modules.survey.entity.BookingSurvey;
import com.example.house_moving_service.modules.survey.enums.SurveyStatus;
import com.example.house_moving_service.modules.survey.repository.BookingSurveyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SurveyCompletionCheckerImplTest {

    private static final int BOOKING_ID = 7;

    @Mock
    private BookingSurveyRepository bookingSurveyRepository;

    private SurveyCompletionCheckerImpl checker;

    @BeforeEach
    void setUp() {
        checker = new SurveyCompletionCheckerImpl(bookingSurveyRepository);
    }

    private void givenLatest(SurveyStatus status) {
        when(bookingSurveyRepository.findTopByBookingIdOrderByCreatedAtDesc(BOOKING_ID))
                .thenReturn(Optional.of(BookingSurvey.builder().id(1).status(status).build()));
    }

    @Test
    @DisplayName("Đơn chưa có lịch khảo sát nào → false")
    void testNoSurvey() {
        when(bookingSurveyRepository.findTopByBookingIdOrderByCreatedAtDesc(BOOKING_ID)).thenReturn(Optional.empty());

        assertThat(checker.isLatestSurveyCompleted(BOOKING_ID)).isFalse();
    }

    @Test
    @DisplayName("Lần mới nhất COMPLETED → true")
    void testLatestCompleted() {
        givenLatest(SurveyStatus.COMPLETED);

        assertThat(checker.isLatestSurveyCompleted(BOOKING_ID)).isTrue();
    }

    @Test
    @DisplayName("Lần mới nhất ở mọi trạng thái khác COMPLETED → false (kể cả đang khảo sát lại sau một lần COMPLETED cũ)")
    void testLatestNotCompleted() {
        for (SurveyStatus status : SurveyStatus.values()) {
            if (status == SurveyStatus.COMPLETED) {
                continue;
            }
            givenLatest(status);

            assertThat(checker.isLatestSurveyCompleted(BOOKING_ID)).as("status %s", status).isFalse();
        }
    }
}
