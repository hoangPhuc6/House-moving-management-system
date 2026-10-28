package com.example.house_moving_service.modules.survey.service.impl;

import com.example.house_moving_service.common.exception.AppException;
import com.example.house_moving_service.common.exception.ErrorCode;
import com.example.house_moving_service.modules.booking.dto.response.BookingSnapshot;
import com.example.house_moving_service.modules.booking.enums.BookingStatus;
import com.example.house_moving_service.modules.booking.service.BookingWorkflowService;
import com.example.house_moving_service.modules.identity.service.StaffLookupService;
import com.example.house_moving_service.modules.quotation.service.QuotationDraftService;
import com.example.house_moving_service.modules.survey.dto.request.AssignSurveyStaffRequest;
import com.example.house_moving_service.modules.survey.dto.request.CancelSurveyRequest;
import com.example.house_moving_service.modules.survey.dto.request.CreateSurveyRequest;
import com.example.house_moving_service.modules.survey.dto.request.FailSurveyRequest;
import com.example.house_moving_service.modules.survey.dto.request.RescheduleSurveyRequest;
import com.example.house_moving_service.modules.survey.dto.request.ResurveyRequest;
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
import java.time.format.DateTimeFormatter;
import java.util.EnumSet;
import java.util.Set;

@Service
@Slf4j
@RequiredArgsConstructor
public class SurveyManagementServiceImpl implements SurveyManagementService {

    private static final Set<BookingStatus> BOOKING_STATUSES_FOR_CREATE =
            EnumSet.of(BookingStatus.PENDING, BookingStatus.PENDING_SURVEY);
    // Đã khảo sát (chờ / đã gửi báo giá); khách chưa chấp nhận báo giá thì còn khảo sát lại được
    private static final Set<BookingStatus> BOOKING_STATUSES_FOR_RESURVEY =
            EnumSet.of(BookingStatus.SURVEYING, BookingStatus.QUOTED);
    private static final Set<BookingStatus> CLOSED_BOOKING_STATUSES =
            EnumSet.of(BookingStatus.CANCELLED, BookingStatus.COMPLETED);
    // Chưa bắt đầu khảo sát: còn phân công / dời lịch / hủy được
    private static final Set<SurveyStatus> EDITABLE_STATUSES = EnumSet.of(SurveyStatus.PENDING, SurveyStatus.CONFIRMED);
    // Đã tới nơi (chưa kịp bấm bắt đầu) hoặc đang khảo sát: báo thất bại được
    private static final Set<SurveyStatus> FAILABLE_STATUSES = EnumSet.of(SurveyStatus.CONFIRMED, SurveyStatus.IN_PROGRESS);
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    private final BookingSurveyRepository bookingSurveyRepository;
    private final BookingWorkflowService bookingWorkflowService;
    private final StaffLookupService staffLookupService;
    private final SurveyScheduleValidator surveyScheduleValidator;
    private final SurveyQueryService surveyQueryService;
    private final QuotationDraftService quotationDraftService;
    private final Clock clock;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SurveyDetailResponse create(CreateSurveyRequest request, Long actorId) {
        BookingSnapshot booking = bookingWorkflowService.getSnapshot(request.getBookingId());
        if (!BOOKING_STATUSES_FOR_CREATE.contains(booking.status())) {
            throw new AppException(ErrorCode.ERR_SURVEY_BOOKING_INVALID_STATUS);
        }
        BookingSurvey saved = createInternal(booking, request, "CREATE", actorId);
        return surveyQueryService.getDetail(saved.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SurveyDetailResponse resurvey(Integer id, ResurveyRequest request, Long actorId) {
        BookingSurvey oldSurvey = loadForUpdate(id);
        requireStatus(oldSurvey, EnumSet.of(SurveyStatus.COMPLETED));
        Integer bookingId = oldSurvey.getBooking().getId();
        // Chỉ khảo sát lại từ lần mới nhất: lần cũ hơn đã có lần sau thay thế
        boolean isLatest = bookingSurveyRepository.findTopByBookingIdOrderByCreatedAtDesc(bookingId)
                .map(latest -> latest.getId().equals(id))
                .orElse(false);
        if (!isLatest) {
            throw new AppException(ErrorCode.ERR_SURVEY_INVALID_STATUS);
        }
        BookingSnapshot booking = bookingWorkflowService.getSnapshot(bookingId);
        if (!BOOKING_STATUSES_FOR_RESURVEY.contains(booking.status())) {
            throw new AppException(ErrorCode.ERR_SURVEY_BOOKING_INVALID_STATUS);
        }
        // Khách đã chốt báo giá thì kết quả khảo sát đã được dùng, không làm lại được
        if (quotationDraftService.hasAcceptedQuotation(bookingId)) {
            throw new AppException(ErrorCode.ERR_QUOTATION_ALREADY_ACCEPTED);
        }

        String reason = request.getReason().trim();
        oldSurvey.setStatus(SurveyStatus.RESURVEY_REQUIRED);
        oldSurvey.appendNote("RESURVEY", actorId, reason, LocalDateTime.now(clock));
        bookingSurveyRepository.saveAndFlush(oldSurvey);

        // Báo giá nháp / đã gửi khách lập từ kết quả cũ hết hiệu lực; lần khảo sát mới sẽ mở version mới
        quotationDraftService.expireOpenQuotations(bookingId, "Khảo sát lại: " + reason);
        bookingWorkflowService.changeStatus(bookingId, BOOKING_STATUSES_FOR_RESURVEY, BookingStatus.PENDING_SURVEY,
                actorId, "Khảo sát lại: " + reason);

        CreateSurveyRequest next = CreateSurveyRequest.builder()
                .bookingId(bookingId)
                .surveyMethod(request.getSurveyMethod() != null ? request.getSurveyMethod() : oldSurvey.getSurveyMethod())
                .surveyDate(request.getSurveyDate())
                .timeSlotId(request.getTimeSlotId())
                .staffId(request.getStaffId())
                .note("Khảo sát lại từ lịch #" + id + ": " + reason)
                .build();
        BookingSurvey newSurvey = createInternal(booking, next, "RESURVEY", actorId);

        log.info("Resurvey: survey id: {} -> new survey id: {} for booking id: {} by user id: {}",
                id, newSurvey.getId(), bookingId, actorId);
        return surveyQueryService.getDetail(newSurvey.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SurveyDetailResponse assign(Integer id, AssignSurveyStaffRequest request, Long actorId) {
        BookingSurvey survey = loadForUpdate(id);
        requireStatus(survey, EDITABLE_STATUSES);
        BookingSnapshot booking = bookingWorkflowService.getSnapshot(survey.getBooking().getId());
        requireBookingOpen(booking);

        Long newStaffId = request.getStaffId();
        Long oldStaffId = survey.getSurveyStaff() != null ? survey.getSurveyStaff().getId() : null;
        if (newStaffId.equals(oldStaffId)) {
            return surveyQueryService.getDetail(id);
        }

        // Chưa có ngày / khung giờ thì không có gì để so trùng lịch: chỉ kiểm tra người hợp lệ
        boolean hasSchedule = survey.getSurveyDate() != null && survey.getTimeSlot() != null;
        surveyScheduleValidator.validate(hasSchedule
                ? new ScheduleCheck(survey.getSurveyDate(), survey.getTimeSlot().getId(), newStaffId, booking.desiredStartAt(), id)
                : new ScheduleCheck(null, null, newStaffId, null, id));

        survey.setSurveyStaff(staffLookupService.getReference(newStaffId));
        survey.setStatus(resolveScheduledStatus(survey));
        survey.appendNote("ASSIGN", actorId,
                "Phân công: " + describeStaff(oldStaffId) + " → " + describeStaff(newStaffId), LocalDateTime.now(clock));
        bookingSurveyRepository.saveAndFlush(survey);

        log.info("Assigned survey id: {} from staff {} to {} by user id: {}", id, oldStaffId, newStaffId, actorId);
        return surveyQueryService.getDetail(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SurveyDetailResponse reschedule(Integer id, RescheduleSurveyRequest request, Long actorId) {
        BookingSurvey survey = loadForUpdate(id);
        requireStatus(survey, EDITABLE_STATUSES);
        BookingSnapshot booking = bookingWorkflowService.getSnapshot(survey.getBooking().getId());
        requireBookingOpen(booking);

        Long staffId = request.getStaffId() != null
                ? request.getStaffId()
                : (survey.getSurveyStaff() != null ? survey.getSurveyStaff().getId() : null);
        // excludeSurveyId = id: dời sang khung giờ mà chính lịch này đang giữ không bị tính là trùng
        SurveyTimeSlot slot = surveyScheduleValidator.validate(new ScheduleCheck(
                request.getSurveyDate(), request.getTimeSlotId(), staffId, booking.desiredStartAt(), id));

        String before = describeSchedule(survey);
        survey.setSurveyDate(request.getSurveyDate());
        survey.setTimeSlot(slot);
        if (request.getStaffId() != null) {
            survey.setSurveyStaff(staffLookupService.getReference(request.getStaffId()));
        }
        if (request.getSurveyMethod() != null) {
            survey.setSurveyMethod(request.getSurveyMethod());
        }
        survey.setStatus(resolveScheduledStatus(survey));
        survey.appendNote("RESCHEDULE", actorId,
                before + " → " + describeSchedule(survey) + ". Lý do: " + request.getReason().trim(), LocalDateTime.now(clock));
        bookingSurveyRepository.saveAndFlush(survey);

        log.info("Rescheduled survey id: {} to {} slot {} by user id: {}", id, request.getSurveyDate(), slot.getId(), actorId);
        return surveyQueryService.getDetail(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SurveyDetailResponse cancel(Integer id, CancelSurveyRequest request, Long actorId) {
        BookingSurvey survey = loadForUpdate(id);
        requireStatus(survey, EDITABLE_STATUSES);

        survey.setStatus(SurveyStatus.CANCELLED);
        survey.appendNote("CANCEL", actorId, request.getReason(), LocalDateTime.now(clock));
        bookingSurveyRepository.saveAndFlush(survey);

        log.info("Cancelled survey id: {} by user id: {}", id, actorId);
        return surveyQueryService.getDetail(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SurveyDetailResponse fail(Integer id, FailSurveyRequest request, Long actorId) {
        BookingSurvey survey = loadForUpdate(id);
        requireStatus(survey, FAILABLE_STATUSES);
        Integer bookingId = survey.getBooking().getId();
        BookingSnapshot booking = bookingWorkflowService.getSnapshot(bookingId);
        requireBookingOpen(booking);

        String reason = request.getReason().trim();
        survey.setStatus(SurveyStatus.RESURVEY_REQUIRED);
        survey.appendNote("FAIL", actorId, reason, LocalDateTime.now(clock));
        bookingSurveyRepository.saveAndFlush(survey);

        // Đồ đạc ghi dở không dùng để báo giá được; lần khảo sát sau mở bản nháp mới
        quotationDraftService.expireDraft(bookingId, "Khảo sát thất bại: " + reason);
        // Lịch CONFIRMED (chưa bắt đầu) thì booking chưa sang SURVEYING, không cần đổi
        if (booking.status() == BookingStatus.SURVEYING) {
            bookingWorkflowService.changeStatus(bookingId, EnumSet.of(BookingStatus.SURVEYING), BookingStatus.PENDING_SURVEY,
                    actorId, "Khảo sát thất bại: " + reason);
        }

        log.info("Survey id: {} failed, booking id: {} back to PENDING_SURVEY by user id: {}", id, bookingId, actorId);
        return surveyQueryService.getDetail(id);
    }

    /**
     * Phần lõi dùng chung cho create và resurvey: chưa có lịch active → validator → dựng entity → lưu.
     * Không kiểm tra trạng thái booking: create kiểm tra trước khi gọi; resurvey vừa tự chuyển booking về PENDING_SURVEY
     * (snapshot trong tay đã cũ nên không kiểm tra lại ở đây).
     */
    private BookingSurvey createInternal(BookingSnapshot booking, CreateSurveyRequest request, String noteTag, Long actorId) {
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
            survey.appendNote(noteTag, actorId, request.getNote(), LocalDateTime.now(clock));
        }

        BookingSurvey saved = bookingSurveyRepository.saveAndFlush(survey);
        log.info("Created survey id: {} for booking id: {} with status {} by user id: {}",
                saved.getId(), booking.id(), saved.getStatus(), actorId);
        return saved;
    }

    // Lấy lịch và khóa dòng đến hết transaction: 2 người cùng sửa một lịch thì người sau chờ người trước xong
    private BookingSurvey loadForUpdate(Integer id) {
        return bookingSurveyRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new AppException(ErrorCode.ERR_SURVEY_NOT_FOUND, id));
    }

    private void requireStatus(BookingSurvey survey, Set<SurveyStatus> allowed) {
        if (!allowed.contains(survey.getStatus())) {
            throw new AppException(ErrorCode.ERR_SURVEY_INVALID_STATUS);
        }
    }

    // Đơn đã hủy / đã hoàn tất thì không sửa lịch khảo sát của đơn đó nữa
    private void requireBookingOpen(BookingSnapshot booking) {
        if (CLOSED_BOOKING_STATUSES.contains(booking.status())) {
            throw new AppException(ErrorCode.ERR_SURVEY_BOOKING_INVALID_STATUS);
        }
    }

    // Mô tả lịch để ghi note, ví dụ "2026-11-03 08:00-10:00, user#5, ONSITE"
    private String describeSchedule(BookingSurvey survey) {
        String date = survey.getSurveyDate() != null ? survey.getSurveyDate().toString() : "(chưa có ngày)";
        String slot = survey.getTimeSlot() != null
                ? survey.getTimeSlot().getStartTime().format(TIME_FORMAT) + "-" + survey.getTimeSlot().getEndTime().format(TIME_FORMAT)
                : "(chưa có khung giờ)";
        Long staffId = survey.getSurveyStaff() != null ? survey.getSurveyStaff().getId() : null;
        return date + " " + slot + ", " + describeStaff(staffId) + ", " + survey.getSurveyMethod();
    }

    private String describeStaff(Long staffId) {
        return staffId != null ? "user#" + staffId : "(chưa có)";
    }

    // Đủ ngày + khung giờ + nhân viên mới là lịch đã chốt; dùng lại khi phân công / đổi lịch
    private SurveyStatus resolveScheduledStatus(BookingSurvey survey) {
        boolean scheduled = survey.getSurveyDate() != null
                && survey.getTimeSlot() != null
                && survey.getSurveyStaff() != null;
        return scheduled ? SurveyStatus.CONFIRMED : SurveyStatus.PENDING;
    }
}
