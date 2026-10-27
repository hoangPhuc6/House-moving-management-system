package com.example.house_moving_service.modules.survey.service.impl;

import com.example.house_moving_service.common.exception.AppException;
import com.example.house_moving_service.common.exception.ErrorCode;
import com.example.house_moving_service.modules.booking.dto.response.BookingSnapshot;
import com.example.house_moving_service.modules.booking.enums.BookingStatus;
import com.example.house_moving_service.modules.booking.service.BookingWorkflowService;
import com.example.house_moving_service.modules.identity.service.StaffLookupService;
import com.example.house_moving_service.modules.survey.dto.request.CreateSurveyRequest;
import com.example.house_moving_service.modules.survey.dto.response.SurveyDetailResponse;
import com.example.house_moving_service.modules.survey.entity.BookingSurvey;
import com.example.house_moving_service.modules.survey.entity.SurveyTimeSlot;
import com.example.house_moving_service.modules.survey.enums.SurveyStatus;
import com.example.house_moving_service.modules.survey.repository.BookingSurveyRepository;
import com.example.house_moving_service.modules.survey.service.SurveyManagementService;
import com.example.house_moving_service.modules.survey.service.SurveyQueryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.Set;

@Service
@Slf4j
@RequiredArgsConstructor
public class SurveyManagementServiceImpl implements SurveyManagementService {

    private static final Set<BookingStatus> BOOKING_STATUSES_FOR_CREATE =
            EnumSet.of(BookingStatus.PENDING, BookingStatus.PENDING_SURVEY);

    private final BookingSurveyRepository bookingSurveyRepository;
    private final BookingWorkflowService bookingWorkflowService;
    private final StaffLookupService staffLookupService;
    private final SurveyScheduleValidator surveyScheduleValidator;
    private final SurveyQueryService surveyQueryService;
    private final Clock clock;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SurveyDetailResponse create(CreateSurveyRequest request, Long actorId) {
        BookingSnapshot booking = bookingWorkflowService.getSnapshot(request.getBookingId());
        if (!BOOKING_STATUSES_FOR_CREATE.contains(booking.status())) {
            throw new AppException(ErrorCode.ERR_SURVEY_BOOKING_INVALID_STATUS);
        }
        if (bookingSurveyRepository.findFirstByBookingIdAndStatusIn(booking.id(), SurveyStatus.ACTIVE).isPresent()) {
            throw new AppException(ErrorCode.ERR_SURVEY_ACTIVE_EXISTS);
        }

        SurveyTimeSlot slot = surveyScheduleValidator.validate(new ScheduleCheck(
                request.getSurveyDate(), request.getTimeSlotId(), request.getStaffId(), booking.desiredStartAt(), null));

        BookingSurvey survey = BookingSurvey.builder()
                .booking(bookingWorkflowService.getReference(booking.id()))
                .surveyMethod(request.getSurveyMethod())
                .surveyDate(request.getSurveyDate())
                .timeSlot(slot)
                .surveyStaff(request.getStaffId() != null ? staffLookupService.getReference(request.getStaffId()) : null)
                .build();
        survey.setStatus(resolveScheduledStatus(survey));
        if (request.getNote() != null && !request.getNote().isBlank()) {
            survey.appendNote("CREATE", actorId, request.getNote(), LocalDateTime.now(clock));
        }

        BookingSurvey saved = bookingSurveyRepository.saveAndFlush(survey);

        log.info("Created survey id: {} for booking id: {} with status {} by user id: {}",
                saved.getId(), booking.id(), saved.getStatus(), actorId);
        return surveyQueryService.getDetail(saved.getId());
    }

    // Đủ ngày + khung giờ + nhân viên mới là lịch đã chốt; dùng lại khi phân công / đổi lịch
    private SurveyStatus resolveScheduledStatus(BookingSurvey survey) {
        boolean scheduled = survey.getSurveyDate() != null
                && survey.getTimeSlot() != null
                && survey.getSurveyStaff() != null;
        return scheduled ? SurveyStatus.CONFIRMED : SurveyStatus.PENDING;
    }
}
