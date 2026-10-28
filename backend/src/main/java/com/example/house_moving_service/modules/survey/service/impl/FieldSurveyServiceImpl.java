package com.example.house_moving_service.modules.survey.service.impl;

import com.example.house_moving_service.common.exception.AppException;
import com.example.house_moving_service.common.exception.ErrorCode;
import com.example.house_moving_service.modules.booking.dto.response.BookingItemSnapshot;
import com.example.house_moving_service.modules.booking.dto.response.BookingSnapshot;
import com.example.house_moving_service.modules.booking.enums.BookingStatus;
import com.example.house_moving_service.modules.booking.service.BookingWorkflowService;
import com.example.house_moving_service.modules.quotation.dto.request.DraftItemSeed;
import com.example.house_moving_service.modules.quotation.dto.response.QuotationItemDto;
import com.example.house_moving_service.modules.quotation.service.QuotationDraftService;
import com.example.house_moving_service.modules.survey.config.SurveyProperties;
import com.example.house_moving_service.modules.survey.dto.response.FieldSurveyResponse;
import com.example.house_moving_service.modules.survey.dto.response.SurveySummaryResponse;
import com.example.house_moving_service.modules.survey.entity.BookingSurvey;
import com.example.house_moving_service.modules.survey.enums.SurveyStatus;
import com.example.house_moving_service.modules.survey.mapper.SurveyMapper;
import com.example.house_moving_service.modules.survey.repository.BookingSurveyRepository;
import com.example.house_moving_service.modules.survey.service.FieldSurveyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

@Service
@Slf4j
@RequiredArgsConstructor
public class FieldSurveyServiceImpl implements FieldSurveyService {

    private static final int DEFAULT_RANGE_DAYS = 7;
    private static final int MAX_RANGE_DAYS = 31;
    private static final Set<BookingStatus> CLOSED_BOOKING_STATUSES = EnumSet.of(BookingStatus.CANCELLED, BookingStatus.COMPLETED);
    private static final Set<SurveyStatus> STATUSES_WITH_DRAFT = EnumSet.of(SurveyStatus.IN_PROGRESS, SurveyStatus.COMPLETED);

    private final BookingSurveyRepository bookingSurveyRepository;
    private final BookingWorkflowService bookingWorkflowService;
    private final QuotationDraftService quotationDraftService;
    private final SurveyMapper surveyMapper;
    private final SurveyProperties surveyProperties;
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
        return toFieldResponse(survey, bookingWorkflowService.getSnapshot(survey.getBooking().getId()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FieldSurveyResponse start(Integer id, Long userId) {
        BookingSurvey survey = loadOwnedForUpdate(id, userId);
        if (survey.getStatus() != SurveyStatus.CONFIRMED) {
            throw new AppException(ErrorCode.ERR_SURVEY_INVALID_STATUS);
        }
        Integer bookingId = survey.getBooking().getId();
        BookingSnapshot booking = bookingWorkflowService.getSnapshot(bookingId);
        if (CLOSED_BOOKING_STATUSES.contains(booking.status())) {
            throw new AppException(ErrorCode.ERR_SURVEY_BOOKING_INVALID_STATUS);
        }
        LocalDateTime now = LocalDateTime.now(clock);
        ensureCanStartAt(survey, now);

        survey.setStatus(SurveyStatus.IN_PROGRESS);
        survey.setActualStartAt(now);
        bookingSurveyRepository.saveAndFlush(survey);

        // Đang SURVEYING sẵn thì changeStatus bỏ qua
        bookingWorkflowService.changeStatus(bookingId, EnumSet.of(BookingStatus.PENDING, BookingStatus.PENDING_SURVEY),
                BookingStatus.SURVEYING, userId, "Bắt đầu khảo sát (lịch #" + id + ")");
        // Lần đầu: đồ đạc copy từ khách khai; khảo sát lại: quotation tự copy từ báo giá gần nhất
        List<DraftItemSeed> seeds = bookingWorkflowService.getItems(bookingId).stream()
                .map(FieldSurveyServiceImpl::toSeed)
                .toList();
        quotationDraftService.openDraftForSurvey(bookingId, id, userId, seeds);

        log.info("Survey id: {} started by mover id: {} at {}", id, userId, now);
        return toFieldResponse(survey, bookingWorkflowService.getSnapshot(bookingId));
    }

    // Chỉ trong ngày khảo sát, sớm nhất earlyStartMinutes phút trước giờ bắt đầu khung; bắt đầu muộn thì không chặn
    private void ensureCanStartAt(BookingSurvey survey, LocalDateTime now) {
        if (!survey.getSurveyDate().equals(now.toLocalDate())) {
            throw new AppException(ErrorCode.ERR_SURVEY_INVALID_DATE, "chỉ bắt đầu được trong ngày khảo sát");
        }
        LocalDateTime earliest = survey.getSurveyDate().atTime(survey.getTimeSlot().getStartTime())
                .minusMinutes(surveyProperties.getEarlyStartMinutes());
        if (now.isBefore(earliest)) {
            throw new AppException(ErrorCode.ERR_SURVEY_INVALID_DATE, "quá sớm so với giờ hẹn");
        }
    }

    private FieldSurveyResponse toFieldResponse(BookingSurvey survey, BookingSnapshot booking) {
        Integer bookingId = booking.id();
        List<QuotationItemDto> draftItems = null;
        if (STATUSES_WITH_DRAFT.contains(survey.getStatus()) && isLatestSurvey(survey)) {
            draftItems = quotationDraftService.findCurrentDraft(bookingId)
                    .map(draft -> quotationDraftService.listItems(draft.id()))
                    .orElse(null);
        }
        return surveyMapper.toFieldResponse(survey, booking, bookingWorkflowService.getRoute(bookingId),
                bookingWorkflowService.getItems(bookingId), draftItems);
    }

    private static DraftItemSeed toSeed(BookingItemSnapshot item) {
        return new DraftItemSeed(item.id(), item.itemName(), item.quantity(), item.estimatedVolumeM3(),
                item.isFragile(), item.packingRequired(), item.disassemblyRequired(), item.note());
    }

    private BookingSurvey loadOwned(Integer id, Long userId) {
        BookingSurvey survey = bookingSurveyRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.ERR_SURVEY_NOT_FOUND, id));
        requireOwner(survey, userId);
        return survey;
    }

    private BookingSurvey loadOwnedForUpdate(Integer id, Long userId) {
        BookingSurvey survey = bookingSurveyRepository.findByIdForUpdate(id)
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
