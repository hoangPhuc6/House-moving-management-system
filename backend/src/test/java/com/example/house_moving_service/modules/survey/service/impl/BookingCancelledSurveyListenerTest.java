package com.example.house_moving_service.modules.survey.service.impl;

import com.example.house_moving_service.modules.booking.entity.Booking;
import com.example.house_moving_service.modules.booking.event.BookingCancelledEvent;
import com.example.house_moving_service.modules.quotation.service.QuotationDraftService;
import com.example.house_moving_service.modules.survey.config.SurveyClockConfig;
import com.example.house_moving_service.modules.survey.entity.BookingSurvey;
import com.example.house_moving_service.modules.survey.enums.SurveyMethod;
import com.example.house_moving_service.modules.survey.enums.SurveyStatus;
import com.example.house_moving_service.modules.survey.repository.BookingSurveyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookingCancelledSurveyListenerTest {

    private static final int BOOKING_ID = 7;
    private static final int SURVEY_ID = 40;
    private static final long ACTOR_ID = 12L;
    private static final Clock CLOCK = Clock.fixed(
            ZonedDateTime.of(LocalDate.of(2026, 11, 2), LocalTime.of(14, 0), SurveyClockConfig.BUSINESS_ZONE).toInstant(),
            SurveyClockConfig.BUSINESS_ZONE);

    @Mock
    private BookingSurveyRepository bookingSurveyRepository;
    @Mock
    private QuotationDraftService quotationDraftService;

    private BookingCancelledSurveyListener listener;

    @BeforeEach
    void setUp() {
        listener = new BookingCancelledSurveyListener(bookingSurveyRepository, quotationDraftService, CLOCK);
    }

    private BookingSurvey survey(SurveyStatus status) {
        return BookingSurvey.builder().id(SURVEY_ID).booking(Booking.builder().id(BOOKING_ID).build())
                .surveyMethod(SurveyMethod.ONSITE).status(status).build();
    }

    // Lần tìm đầu thấy lịch đang hoạt động; lần đọc có khóa trả trạng thái mới nhất (lockedState)
    private BookingSurvey givenActiveSurvey(SurveyStatus foundState, SurveyStatus lockedState) {
        when(bookingSurveyRepository.findFirstByBookingIdAndStatusIn(BOOKING_ID, SurveyStatus.ACTIVE))
                .thenReturn(Optional.of(survey(foundState)));
        BookingSurvey locked = survey(lockedState);
        when(bookingSurveyRepository.findByIdForUpdate(SURVEY_ID)).thenReturn(Optional.of(locked));
        return locked;
    }

    @Test
    @DisplayName("Có lịch CONFIRMED → CANCELLED, note 'Đơn hàng bị hủy: lý do'; báo giá đang mở (DRAFT + SENT) hết hiệu lực")
    void testConfirmedSurvey_Cancelled() {
        BookingSurvey locked = givenActiveSurvey(SurveyStatus.CONFIRMED, SurveyStatus.CONFIRMED);

        listener.onBookingCancelled(new BookingCancelledEvent(BOOKING_ID, ACTOR_ID, " Khách đổi ý "));

        assertThat(locked.getStatus()).isEqualTo(SurveyStatus.CANCELLED);
        assertThat(locked.getNote()).isEqualTo("[2026-11-02 14:00][CANCEL][user#12] Đơn hàng bị hủy: Khách đổi ý");
        verify(bookingSurveyRepository).save(locked);
        verify(quotationDraftService).expireOpenQuotations(BOOKING_ID, "Đơn hàng bị hủy: Khách đổi ý");
    }

    @Test
    @DisplayName("Lịch đang IN_PROGRESS vẫn bị hủy; note ghi rõ 'đang khảo sát dở'")
    void testInProgressSurvey_Cancelled() {
        BookingSurvey locked = givenActiveSurvey(SurveyStatus.IN_PROGRESS, SurveyStatus.IN_PROGRESS);

        listener.onBookingCancelled(new BookingCancelledEvent(BOOKING_ID, ACTOR_ID, "Khách đổi ý"));

        assertThat(locked.getStatus()).isEqualTo(SurveyStatus.CANCELLED);
        assertThat(locked.getNote()).endsWith("Đơn hàng bị hủy: Khách đổi ý (đang khảo sát dở)");
    }

    @Test
    @DisplayName("Không có lịch đang hoạt động → không lỗi, không khóa; vẫn cho báo giá hết hiệu lực")
    void testNoActiveSurvey() {
        when(bookingSurveyRepository.findFirstByBookingIdAndStatusIn(BOOKING_ID, SurveyStatus.ACTIVE))
                .thenReturn(Optional.empty());

        listener.onBookingCancelled(new BookingCancelledEvent(BOOKING_ID, ACTOR_ID, "Khách đổi ý"));

        verify(bookingSurveyRepository, never()).findByIdForUpdate(any());
        verify(bookingSurveyRepository, never()).save(any());
        verify(quotationDraftService).expireOpenQuotations(BOOKING_ID, "Đơn hàng bị hủy: Khách đổi ý");
    }

    @Test
    @DisplayName("Lịch vừa được hoàn tất ngay trước khi khóa → không ghi đè trạng thái COMPLETED")
    void testSurveyFinishedBeforeLock_NotOverwritten() {
        BookingSurvey locked = givenActiveSurvey(SurveyStatus.IN_PROGRESS, SurveyStatus.COMPLETED);

        listener.onBookingCancelled(new BookingCancelledEvent(BOOKING_ID, ACTOR_ID, "Khách đổi ý"));

        assertThat(locked.getStatus()).isEqualTo(SurveyStatus.COMPLETED);
        assertThat(locked.getNote()).isNull();
        verify(bookingSurveyRepository, never()).save(any());
    }

    @Test
    @DisplayName("Event không có lý do → note chỉ ghi 'Đơn hàng bị hủy'")
    void testNoReason() {
        BookingSurvey locked = givenActiveSurvey(SurveyStatus.PENDING, SurveyStatus.PENDING);

        listener.onBookingCancelled(new BookingCancelledEvent(BOOKING_ID, ACTOR_ID, null));

        assertThat(locked.getNote()).endsWith("[CANCEL][user#12] Đơn hàng bị hủy");
        verify(quotationDraftService).expireOpenQuotations(BOOKING_ID, "Đơn hàng bị hủy");
    }
}
