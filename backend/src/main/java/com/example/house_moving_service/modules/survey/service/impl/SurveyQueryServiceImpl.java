package com.example.house_moving_service.modules.survey.service.impl;

import com.example.house_moving_service.common.dto.PageResponse;
import com.example.house_moving_service.common.exception.AppException;
import com.example.house_moving_service.common.exception.ErrorCode;
import com.example.house_moving_service.modules.booking.dto.response.BookingRouteResponse;
import com.example.house_moving_service.modules.booking.service.BookingWorkflowService;
import com.example.house_moving_service.modules.identity.dto.response.StaffSummary;
import com.example.house_moving_service.modules.identity.enums.RoleCode;
import com.example.house_moving_service.modules.identity.service.StaffLookupService;
import com.example.house_moving_service.modules.quotation.dto.response.QuotationDraftDto;
import com.example.house_moving_service.modules.quotation.dto.response.QuotationItemDto;
import com.example.house_moving_service.modules.quotation.service.QuotationDraftService;
import com.example.house_moving_service.modules.survey.dto.request.SurveyFilterRequest;
import com.example.house_moving_service.modules.survey.dto.response.StaffAvailabilityResponse;
import com.example.house_moving_service.modules.survey.dto.response.StaffAvailabilityResponse.SlotAvailability;
import com.example.house_moving_service.modules.survey.dto.response.SurveyDetailResponse;
import com.example.house_moving_service.modules.survey.dto.response.SurveySummaryResponse;
import com.example.house_moving_service.modules.survey.dto.response.SurveyTimeSlotResponse;
import com.example.house_moving_service.modules.survey.entity.BookingSurvey;
import com.example.house_moving_service.modules.survey.enums.SlotBusyReason;
import com.example.house_moving_service.modules.survey.enums.SurveyStatus;
import com.example.house_moving_service.modules.survey.mapper.SurveyMapper;
import com.example.house_moving_service.modules.survey.repository.BookingSurveyRepository;
import com.example.house_moving_service.modules.survey.repository.specification.BookingSurveySpecification;
import com.example.house_moving_service.modules.survey.service.StaffExternalScheduleChecker;
import com.example.house_moving_service.modules.survey.service.SurveyQueryService;
import com.example.house_moving_service.modules.survey.service.SurveyTimeSlotService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SurveyQueryServiceImpl implements SurveyQueryService {

    static final Sort DEFAULT_SORT = Sort.by(Sort.Order.asc("surveyDate"), Sort.Order.asc("timeSlot.startTime"));

    private final BookingSurveyRepository bookingSurveyRepository;
    private final BookingWorkflowService bookingWorkflowService;
    private final QuotationDraftService quotationDraftService;
    private final StaffLookupService staffLookupService;
    private final SurveyTimeSlotService surveyTimeSlotService;
    private final ObjectProvider<StaffExternalScheduleChecker> externalScheduleChecker;
    private final SurveyMapper surveyMapper;
    private final Clock clock;

    @Override
    public PageResponse<SurveySummaryResponse> search(SurveyFilterRequest filter, Pageable pageable) {
        Pageable sorted = pageable.getSort().isSorted()
                ? pageable
                : PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), DEFAULT_SORT);
        return PageResponse.from(bookingSurveyRepository
                .findAll(BookingSurveySpecification.filterSurveys(filter), sorted)
                .map(surveyMapper::toSummary));
    }

    @Override
    public SurveyDetailResponse getDetail(Integer id) {
        BookingSurvey survey = bookingSurveyRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.ERR_SURVEY_NOT_FOUND, id));
        Integer bookingId = survey.getBooking().getId();

        BookingRouteResponse route = bookingWorkflowService.getRoute(bookingId);
        List<BookingSurvey> allSurveys = bookingSurveyRepository.findAllByBookingIdOrderByCreatedAtDesc(bookingId);

        // Bản nháp chỉ thuộc về lần khảo sát mới nhất; lần cũ xem lại thì không hiện bản nháp của lần mới
        QuotationDraftDto currentDraft = null;
        List<QuotationItemDto> draftItems = null;
        boolean isLatest = !allSurveys.isEmpty() && allSurveys.get(0).getId().equals(id);
        if (isLatest) {
            currentDraft = quotationDraftService.findCurrentDraft(bookingId).orElse(null);
            if (currentDraft != null) {
                draftItems = quotationDraftService.listItems(currentDraft.id());
            }
        }

        List<SurveySummaryResponse> previousSurveys = allSurveys.stream()
                .filter(s -> !s.getId().equals(id))
                .map(surveyMapper::toSummary)
                .toList();

        return surveyMapper.toDetail(survey, route, currentDraft, draftItems, previousSurveys);
    }

    @Override
    public List<SurveySummaryResponse> getByBooking(Integer bookingId) {
        // Ném lỗi không tìm thấy booking thay vì trả danh sách rỗng
        bookingWorkflowService.getSnapshot(bookingId);
        return bookingSurveyRepository.findAllByBookingIdOrderByCreatedAtDesc(bookingId).stream()
                .map(surveyMapper::toSummary)
                .toList();
    }

    @Override
    public List<StaffSummary> listAvailableMovers() {
        return staffLookupService.findActiveByRole(RoleCode.MOVER);
    }

    @Override
    public StaffAvailabilityResponse getStaffAvailability(Long staffId, LocalDate date) {
        if (!staffLookupService.isActiveStaffWithRole(staffId, RoleCode.MOVER)) {
            throw new AppException(ErrorCode.ERR_SURVEY_STAFF_INVALID, staffId);
        }
        String fullName = staffLookupService.findSummary(staffId).map(StaffSummary::fullName).orElse(null);
        LocalDate day = date != null ? date : LocalDate.now(clock);

        // Một truy vấn cho cả ngày rồi so trong bộ nhớ, thay vì hỏi DB từng khung giờ.
        // Mỗi nhân viên tối đa 1 lịch / ngày / khung giờ (uq_booking_surveys_staff_slot) nên key không trùng
        Map<Integer, Integer> surveyIdBySlotId = bookingSurveyRepository
                .findByStaffAndDateRange(staffId, day, day, SurveyStatus.OCCUPYING_SLOT).stream()
                .collect(Collectors.toMap(s -> s.getTimeSlot().getId(), BookingSurvey::getId, (first, second) -> first));
        StaffExternalScheduleChecker checker = externalScheduleChecker.getIfAvailable();

        List<SlotAvailability> slots = surveyTimeSlotService.getAll(true).stream()
                .map(slot -> toSlotAvailability(slot, staffId, day, surveyIdBySlotId, checker))
                .toList();

        return StaffAvailabilityResponse.builder()
                .staffId(staffId)
                .fullName(fullName)
                .date(day)
                .slots(slots)
                .build();
    }

    private SlotAvailability toSlotAvailability(SurveyTimeSlotResponse slot, Long staffId, LocalDate date,
                                                Map<Integer, Integer> surveyIdBySlotId,
                                                StaffExternalScheduleChecker checker) {
        Integer surveyId = surveyIdBySlotId.get(slot.getId());
        if (surveyId != null) {
            return SlotAvailability.builder().slot(slot).available(false)
                    .reason(SlotBusyReason.SURVEY).surveyId(surveyId).build();
        }
        if (checker != null && checker.isBusy(staffId, date.atTime(slot.getStartTime()), date.atTime(slot.getEndTime()))) {
            return SlotAvailability.builder().slot(slot).available(false)
                    .reason(SlotBusyReason.OTHER_SCHEDULE).build();
        }
        return SlotAvailability.builder().slot(slot).available(true).build();
    }
}
