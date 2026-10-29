package com.example.house_moving_service.modules.survey.service.impl;

import com.example.house_moving_service.common.exception.AppException;
import com.example.house_moving_service.common.exception.ErrorCode;
import com.example.house_moving_service.modules.booking.dto.response.BookingSnapshot;
import com.example.house_moving_service.modules.booking.service.BookingWorkflowService;
import com.example.house_moving_service.modules.survey.dto.response.CustomerSurveyResponse;
import com.example.house_moving_service.modules.survey.mapper.SurveyMapper;
import com.example.house_moving_service.modules.survey.repository.BookingSurveyRepository;
import com.example.house_moving_service.modules.survey.service.CustomerSurveyService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CustomerSurveyServiceImpl implements CustomerSurveyService {

    private final BookingWorkflowService bookingWorkflowService;
    private final BookingSurveyRepository bookingSurveyRepository;
    private final SurveyMapper surveyMapper;

    @Override
    @Transactional(readOnly = true)
    public List<CustomerSurveyResponse> getByBooking(Integer bookingId, Long currentUserId) {
        BookingSnapshot booking = bookingWorkflowService.getSnapshot(bookingId);

        if (booking.customerUserId() == null || !booking.customerUserId().equals(currentUserId)) {
            throw new AppException(ErrorCode.ERR_BOOKING_FORBIDDEN);
        }
        return bookingSurveyRepository.findAllByBookingIdOrderByCreatedAtDesc(bookingId).stream()
                .map(surveyMapper::toCustomerResponse)
                .toList();
    }
}
