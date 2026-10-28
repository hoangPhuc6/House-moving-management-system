package com.example.house_moving_service.modules.survey.service.impl;

import com.example.house_moving_service.modules.quotation.service.SurveyCompletionChecker;
import com.example.house_moving_service.modules.survey.enums.SurveyStatus;
import com.example.house_moving_service.modules.survey.repository.BookingSurveyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cài đặt contract của module quotation: chỉ lập báo giá chính thức khi lần khảo sát mới nhất đã COMPLETED.
 * Chỉ phụ thuộc repository của survey (quotation sẽ inject bean này, giữ nhỏ để không tạo vòng bean).
 */
@Service
@RequiredArgsConstructor
public class SurveyCompletionCheckerImpl implements SurveyCompletionChecker {

    private final BookingSurveyRepository bookingSurveyRepository;

    @Override
    @Transactional(readOnly = true)
    public boolean isLatestSurveyCompleted(Integer bookingId) {
        // Đang khảo sát lại thì lần mới nhất chưa COMPLETED, dù có lần cũ đã COMPLETED
        return bookingSurveyRepository.findTopByBookingIdOrderByCreatedAtDesc(bookingId)
                .map(survey -> survey.getStatus() == SurveyStatus.COMPLETED)
                .orElse(false);
    }
}
