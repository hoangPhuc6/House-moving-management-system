package com.example.house_moving_service.modules.survey.service.impl;

import com.example.house_moving_service.modules.booking.event.BookingCancelledEvent;
import com.example.house_moving_service.modules.quotation.service.QuotationDraftService;
import com.example.house_moving_service.modules.survey.entity.BookingSurvey;
import com.example.house_moving_service.modules.survey.enums.SurveyStatus;
import com.example.house_moving_service.modules.survey.repository.BookingSurveyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

/**
 * Đơn hàng bị hủy → hủy lịch khảo sát đang hoạt động (kể cả IN_PROGRESS) và cho báo giá đang mở hết hiệu lực.
 * Chạy đồng bộ trong transaction hủy đơn: lỗi ở đây làm thao tác hủy đơn rollback, không để dữ liệu lệch nhau.
 * Chỉ phụ thuộc repository + QuotationDraftService (không dùng SurveyManagementService) để bean nhỏ, không vòng.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class BookingCancelledSurveyListener {

    private final BookingSurveyRepository bookingSurveyRepository;
    private final QuotationDraftService quotationDraftService;
    private final Clock clock;

    @EventListener
    @Transactional(rollbackFor = Exception.class)
    public void onBookingCancelled(BookingCancelledEvent event) {
        String reason = event.reason() != null && !event.reason().isBlank()
                ? "Đơn hàng bị hủy: " + event.reason().trim()
                : "Đơn hàng bị hủy";

        bookingSurveyRepository.findFirstByBookingIdAndStatusIn(event.bookingId(), SurveyStatus.ACTIVE)
                // Khóa rồi kiểm tra lại: MOVER có thể vừa hoàn tất / báo thất bại đúng lúc đơn bị hủy
                .flatMap(found -> bookingSurveyRepository.findByIdForUpdate(found.getId()))
                .filter(survey -> survey.getStatus().isActive())
                .ifPresent(survey -> cancel(survey, event.actorUserId(), reason));

        // Gồm cả báo giá đã gửi khách (SENT), không chỉ bản nháp
        quotationDraftService.expireOpenQuotations(event.bookingId(), reason);
    }

    private void cancel(BookingSurvey survey, Long actorUserId, String reason) {
        SurveyStatus oldStatus = survey.getStatus();
        survey.setStatus(SurveyStatus.CANCELLED);
        survey.appendNote("CANCEL", actorUserId,
                oldStatus == SurveyStatus.IN_PROGRESS ? reason + " (đang khảo sát dở)" : reason,
                LocalDateTime.now(clock));
        bookingSurveyRepository.save(survey);
        log.info("Cancelled survey id: {} ({}) because booking id: {} was cancelled",
                survey.getId(), oldStatus, survey.getBooking().getId());
    }
}
