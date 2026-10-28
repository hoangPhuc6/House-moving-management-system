package com.example.house_moving_service.modules.survey.service.impl;

import com.example.house_moving_service.common.exception.AppException;
import com.example.house_moving_service.common.exception.ErrorCode;
import com.example.house_moving_service.modules.booking.dto.response.BookingSnapshot;
import com.example.house_moving_service.modules.booking.service.BookingWorkflowService;
import com.example.house_moving_service.modules.quotation.dto.response.QuotationItemDto;
import com.example.house_moving_service.modules.quotation.service.QuotationDraftService;
import com.example.house_moving_service.modules.survey.dto.response.FieldSurveyResponse;
import com.example.house_moving_service.modules.survey.dto.response.SurveySummaryResponse;
import com.example.house_moving_service.modules.survey.entity.BookingSurvey;
import com.example.house_moving_service.modules.survey.enums.SurveyStatus;
import com.example.house_moving_service.modules.survey.mapper.SurveyMapper;
import com.example.house_moving_service.modules.survey.repository.BookingSurveyRepository;
import com.example.house_moving_service.modules.survey.service.FieldSurveyService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class FieldSurveyServiceImpl implements FieldSurveyService {

    private static final int DEFAULT_RANGE_DAYS = 7;
    private static final int MAX_RANGE_DAYS = 31;
    private static final Set<SurveyStatus> STATUSES_WITH_DRAFT = EnumSet.of(SurveyStatus.IN_PROGRESS, SurveyStatus.COMPLETED);

    private final BookingSurveyRepository bookingSurveyRepository;
    private final BookingWorkflowService bookingWorkflowService;
    private final QuotationDraftService quotationDraftService;
    private final SurveyMapper surveyMapper;
    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    public List<SurveySummaryResponse> getMySurveys(Long userId, LocalDate from, LocalDate to, SurveyStatus status) {
        LocalDate fromDate = from != null ? from : LocalDate.now(clock);
        LocalDate toDate = to != null ? to : fromDate.plusDays(DEFAULT_RANGE_DAYS);
        if (toDate.isBefore(fromDate)) {
            throw new AppException(ErrorCode.ERR_INVALID_INPUT, "ngày kết thúc phải từ ngày bắt đầu trở đi");
        }
        if (ChronoUnit.DAYS.between(fromDate, toDate) > MAX_RANGE_DAYS) {
            throw new AppException(ErrorCode.ERR_INVALID_INPUT, "chỉ xem tối đa " + MAX_RANGE_DAYS + " ngày mỗi lần");
        }

        Set<SurveyStatus> statuses = status != null ? EnumSet.of(status) : EnumSet.allOf(SurveyStatus.class);
        return bookingSurveyRepository.findByStaffAndDateRange(userId, fromDate, toDate, statuses).stream()
                .map(surveyMapper::toSummary)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public FieldSurveyResponse getMySurvey(Integer id, Long userId) {
        BookingSurvey survey = loadOwned(id, userId);
        Integer bookingId = survey.getBooking().getId();

        BookingSnapshot booking = bookingWorkflowService.getSnapshot(bookingId);
        List<QuotationItemDto> draftItems = null;
        if (STATUSES_WITH_DRAFT.contains(survey.getStatus()) && isLatestSurvey(survey)) {
            draftItems = quotationDraftService.findCurrentDraft(bookingId)
                    .map(draft -> quotationDraftService.listItems(draft.id()))
                    .orElse(null);
        }

        return surveyMapper.toFieldResponse(survey, booking, bookingWorkflowService.getRoute(bookingId),
                bookingWorkflowService.getItems(bookingId), draftItems);
    }

    private BookingSurvey loadOwned(Integer id, Long userId) {
        BookingSurvey survey = bookingSurveyRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.ERR_SURVEY_NOT_FOUND, id));
        requireOwner(survey, userId);
        return survey;
    }

    private void requireOwner(BookingSurvey survey, Long userId) {
        if (survey.getSurveyStaff() == null || !survey.getSurveyStaff().getId().equals(userId)) {
            throw new AppException(ErrorCode.ERR_SURVEY_FORBIDDEN);
        }
    }


    private boolean isLatestSurvey(BookingSurvey survey) {
        return bookingSurveyRepository.findTopByBookingIdOrderByCreatedAtDesc(survey.getBooking().getId())
                .map(latest -> latest.getId().equals(survey.getId()))
                .orElse(false);
    }
}
