package com.example.house_moving_service.modules.survey;

import com.example.house_moving_service.common.exception.AppException;
import com.example.house_moving_service.common.exception.ErrorCode;
import com.example.house_moving_service.common.storage.FileStorageService;
import com.example.house_moving_service.common.storage.StoredFile;
import com.example.house_moving_service.modules.booking.dto.RouteAccessInfo;
import com.example.house_moving_service.modules.booking.dto.request.CancelBookingRequest;
import com.example.house_moving_service.modules.booking.entity.Booking;
import com.example.house_moving_service.modules.booking.enums.BookingStatus;
import com.example.house_moving_service.modules.booking.repository.BookingRepository;
import com.example.house_moving_service.modules.booking.repository.BookingRouteRepository;
import com.example.house_moving_service.modules.booking.repository.BookingStatusHistoryRepository;
import com.example.house_moving_service.modules.booking.service.BookingService;
import com.example.house_moving_service.modules.booking.service.BookingWorkflowService;
import com.example.house_moving_service.modules.identity.repository.UserRepository;
import com.example.house_moving_service.modules.quotation.dto.response.QuotationDraftDto;
import com.example.house_moving_service.modules.quotation.dto.response.QuotationItemDto;
import com.example.house_moving_service.modules.quotation.entity.Quotation;
import com.example.house_moving_service.modules.quotation.enums.QuotationStatus;
import com.example.house_moving_service.modules.quotation.repository.QuotationRepository;
import com.example.house_moving_service.modules.quotation.service.QuotationDraftService;
import com.example.house_moving_service.modules.quotation.service.SurveyCompletionChecker;
import com.example.house_moving_service.modules.survey.SurveyTestClockConfig.MutableClock;
import com.example.house_moving_service.modules.survey.dto.request.CancelSurveyRequest;
import com.example.house_moving_service.modules.survey.dto.request.CompleteSurveyRequest;
import com.example.house_moving_service.modules.survey.dto.request.CreateSurveyRequest;
import com.example.house_moving_service.modules.survey.dto.request.FailSurveyRequest;
import com.example.house_moving_service.modules.survey.dto.request.LocationAccessRequest;
import com.example.house_moving_service.modules.survey.dto.request.RescheduleSurveyRequest;
import com.example.house_moving_service.modules.survey.dto.request.ResurveyRequest;
import com.example.house_moving_service.modules.survey.dto.request.RouteAccessRequest;
import com.example.house_moving_service.modules.survey.dto.request.SurveyItemRequest;
import com.example.house_moving_service.modules.survey.dto.response.CustomerSurveyResponse;
import com.example.house_moving_service.modules.survey.dto.response.FieldSurveyResponse;
import com.example.house_moving_service.modules.survey.dto.response.SurveyDetailResponse;
import com.example.house_moving_service.modules.survey.dto.response.SurveySummaryResponse;
import com.example.house_moving_service.modules.survey.entity.BookingSurvey;
import com.example.house_moving_service.modules.survey.entity.SurveyTimeSlot;
import com.example.house_moving_service.modules.survey.enums.SurveyMethod;
import com.example.house_moving_service.modules.survey.enums.SurveyStatus;
import com.example.house_moving_service.modules.survey.repository.BookingSurveyRepository;
import com.example.house_moving_service.modules.survey.repository.SurveyAttachmentRepository;
import com.example.house_moving_service.modules.survey.repository.SurveyTimeSlotRepository;
import com.example.house_moving_service.modules.survey.service.CustomerSurveyService;
import com.example.house_moving_service.modules.survey.service.FieldSurveyService;
import com.example.house_moving_service.modules.survey.service.SurveyAttachmentService;
import com.example.house_moving_service.modules.survey.service.SurveyManagementService;
import com.example.house_moving_service.modules.survey.service.SurveyQueryService;
import com.example.house_moving_service.security.userdetails.CustomUserDetails;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Task 4.10 — kiểm thử tích hợp luồng khảo sát xuyên module (survey, booking, quotation, identity)
 * trên PostgreSQL thật. Đồng hồ chỉnh được (SurveyTestClockConfig), lưu file giả lập; mọi thay đổi rollback sau test.
 * IT10 (chạy đua) cần transaction thật nên nằm ở SurveyConcurrencyIntegrationTest.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Import(SurveyTestClockConfig.class)
class SurveyFlowIntegrationTest {

    // Ngày khảo sát xa trong tương lai: không đụng dữ liệu thật; ngày chuyển nhà sau đó 5 ngày
    private static final LocalDate DAY = LocalDate.of(2099, 4, 10);
    private static final byte[] PNG = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};

    @MockitoBean
    private FileStorageService fileStorageService;

    @Autowired private MutableClock clock;
    @Autowired private MockMvc mockMvc;
    @Autowired private SurveyManagementService managementService;
    @Autowired private FieldSurveyService fieldSurveyService;
    @Autowired private SurveyAttachmentService attachmentService;
    @Autowired private SurveyQueryService surveyQueryService;
    @Autowired private CustomerSurveyService customerSurveyService;
    @Autowired private BookingService bookingService;
    @Autowired private BookingWorkflowService bookingWorkflowService;
    @Autowired private QuotationDraftService quotationDraftService;
    @Autowired private SurveyCompletionChecker surveyCompletionChecker;
    @Autowired private BookingRepository bookingRepository;
    @Autowired private BookingRouteRepository bookingRouteRepository;
    @Autowired private BookingStatusHistoryRepository historyRepository;
    @Autowired private BookingSurveyRepository bookingSurveyRepository;
    @Autowired private SurveyAttachmentRepository attachmentRepository;
    @Autowired private SurveyTimeSlotRepository timeSlotRepository;
    @Autowired private QuotationRepository quotationRepository;
    @Autowired private UserRepository userRepository;

    private Booking bookingA;
    private Booking bookingB;
    private SurveyTimeSlot slot1;
    private SurveyTimeSlot slot2;
    private Long adminId;
    private Long dispatcherId;
    private Long mover1Id;
    private Long mover2Id;

    @BeforeEach
    void setUp() {
        // "Bây giờ" = hôm trước ngày khảo sát → tạo lịch hợp lệ
        clock.set(DAY.minusDays(1).atTime(9, 0));

        bookingA = resetBooking(1);
        bookingB = resetBooking(2);
        List<SurveyTimeSlot> slots = timeSlotRepository.findAllByIsActiveTrueOrderByStartTimeAsc();
        slot1 = slots.get(0);
        slot2 = slots.get(1);

        adminId = userId("admin@housemoving.vn");
        dispatcherId = userId("dispatcher@housemoving.vn");
        mover1Id = userId("mover1@housemoving.vn");
        mover2Id = userId("mover2@housemoving.vn");

        when(fileStorageService.upload(anyString(), any(), anyLong(), anyString()))
                .thenAnswer(inv -> new StoredFile(inv.getArgument(0), inv.getArgument(3), inv.getArgument(2)));
    }

    // Booking seed về trạng thái sạch: PENDING_SURVEY, chưa có khảo sát / báo giá, ngày chuyển sau ngày khảo sát
    private Booking resetBooking(int id) {
        Booking booking = bookingRepository.findById(id).orElseThrow();
        bookingSurveyRepository.deleteAll(bookingSurveyRepository.findAllByBookingIdOrderByCreatedAtDesc(id));
        quotationRepository.deleteAll(quotationRepository.findAllByBookingIdOrderByVersionDesc(id));
        booking.setStatus(BookingStatus.PENDING_SURVEY);
        booking.setDesiredStartAt(DAY.plusDays(5).atTime(8, 0));
        booking.setDesiredEndAt(DAY.plusDays(5).atTime(12, 0));
        return bookingRepository.saveAndFlush(booking);
    }

    private Long userId(String email) {
        return userRepository.findByEmail(email).orElseThrow().getId();
    }

    private SurveyDetailResponse createSurvey(Booking booking, SurveyTimeSlot slot, Long staffId, Long actorId) {
        return managementService.create(CreateSurveyRequest.builder()
                .bookingId(booking.getId()).surveyMethod(SurveyMethod.ONSITE)
                .surveyDate(DAY).timeSlotId(slot.getId()).staffId(staffId).note("Khách hẹn trước")
                .build(), actorId);
    }

    // Tua đồng hồ tới 30 phút sau giờ bắt đầu khung của lịch, rồi MOVER bắt đầu
    private FieldSurveyResponse startAt(Integer surveyId, SurveyTimeSlot slot, Long moverId) {
        clock.set(DAY.atTime(slot.getStartTime()).plusMinutes(30));
        return fieldSurveyService.start(surveyId, moverId);
    }

    private FieldSurveyResponse completeWithOneExtraItem(Integer surveyId, Long moverId) {
        fieldSurveyService.addItem(surveyId, SurveyItemRequest.builder().itemName("Sofa góc").quantity(1).build(), moverId);
        return fieldSurveyService.complete(surveyId, CompleteSurveyRequest.builder().resultNote("Đủ dữ liệu").build(), moverId);
    }

    private static void assertErrorCode(Runnable call, ErrorCode expected) {
        assertThatThrownBy(call::run)
                .isInstanceOf(AppException.class)
                .extracting(ex -> ((AppException) ex).getErrorCode())
                .isEqualTo(expected);
    }

    private BookingStatus bookingStatus(Booking booking) {
        return bookingRepository.findById(booking.getId()).orElseThrow().getStatus();
    }

    private SurveyStatus surveyStatus(Integer surveyId) {
        return bookingSurveyRepository.findById(surveyId).orElseThrow().getStatus();
    }

    @Test
    @DisplayName("IT1 — Luồng chuẩn: dispatcher tạo → MOVER bắt đầu → sửa/thêm/xóa đồ → ra vào → ảnh → hoàn tất")
    void it1_happyPath() {
        int historyBefore = historyRepository.findAllByBookingIdOrderByChangedAtAsc(bookingA.getId()).size();
        var declaredBefore = bookingWorkflowService.getItems(bookingA.getId());

        SurveyDetailResponse created = createSurvey(bookingA, slot1, mover1Id, dispatcherId);
        assertThat(created.getStatus()).isEqualTo(SurveyStatus.CONFIRMED);
        Integer id = created.getId();

        FieldSurveyResponse started = startAt(id, slot1, mover1Id);
        assertThat(started.getDraftItems()).hasSize(declaredBefore.size());

        // Sửa 1, thêm 1, xóa 1 (đồ vừa thêm thứ hai)
        QuotationItemDto first = started.getDraftItems().getFirst();
        fieldSurveyService.updateItem(id, first.id(), SurveyItemRequest.builder()
                .itemName(first.itemName()).quantity(first.quantity() + 1).build(), mover1Id);
        fieldSurveyService.addItem(id, SurveyItemRequest.builder().itemName("Tủ quần áo").quantity(2).build(), mover1Id);
        QuotationItemDto toDelete = fieldSurveyService.addItem(id,
                SurveyItemRequest.builder().itemName("Ghế nhựa").quantity(4).build(), mover1Id);
        fieldSurveyService.deleteItem(id, toDelete.id(), mover1Id);

        fieldSurveyService.updateRouteAccess(id, RouteAccessRequest.builder()
                .pickup(LocationAccessRequest.builder().floorNumber(4).hasElevator(false).walkingDistanceMeters(60).build())
                .dropoff(LocationAccessRequest.builder().floorNumber(1).hasElevator(true).walkingDistanceMeters(0).build())
                .build(), mover1Id);
        attachmentService.upload(id, new MockMultipartFile("file", "phong-khach.png", "image/png", PNG), mover1Id, false);

        clock.set(DAY.atTime(slot1.getStartTime()).plusHours(1));
        FieldSurveyResponse done = fieldSurveyService.complete(id, CompleteSurveyRequest.builder().resultNote("OK").build(), mover1Id);

        // Survey
        assertThat(done.getStatus()).isEqualTo(SurveyStatus.COMPLETED);
        assertThat(done.getActualStartAt()).isBefore(done.getActualEndAt());
        assertThat(attachmentRepository.findAllBySurveyIdOrderByCreatedAtAsc(id)).hasSize(1);
        // Booking: SURVEYING + 1 dòng lịch sử (PENDING_SURVEY → SURVEYING)
        assertThat(bookingStatus(bookingA)).isEqualTo(BookingStatus.SURVEYING);
        assertThat(historyRepository.findAllByBookingIdOrderByChangedAtAsc(bookingA.getId())).hasSize(historyBefore + 1);
        // Quotation v1 DRAFT gắn với lịch, đồ đạc đúng, giá 0
        QuotationDraftDto draft = quotationDraftService.findCurrentDraft(bookingA.getId()).orElseThrow();
        assertThat(draft.version()).isEqualTo(1);
        assertThat(draft.status()).isEqualTo(QuotationStatus.DRAFT);
        assertThat(draft.note()).contains("[SURVEY#" + id + "]");
        List<QuotationItemDto> items = quotationDraftService.listItems(draft.id());
        assertThat(items).hasSize(declaredBefore.size() + 1);
        assertThat(items).extracting(QuotationItemDto::itemName).contains("Tủ quần áo").doesNotContain("Ghế nhựa");
        assertThat(items).filteredOn(i -> i.id().equals(first.id())).singleElement()
                .extracting(QuotationItemDto::quantity).isEqualTo(first.quantity() + 1);
        assertThat(items).allSatisfy(i -> assertThat(i.unitPrice()).isEqualByComparingTo("0"));
        // Đồ khách khai giữ nguyên; điều kiện ra vào lưu đúng định dạng
        assertThat(bookingWorkflowService.getItems(bookingA.getId())).isEqualTo(declaredBefore);
        assertThat(bookingRouteRepository.findByBookingId(bookingA.getId()).orElseThrow().getPickupNote())
                .isEqualTo("Tầng: 4, Thang máy: Không, Cự ly hẻm: 60m");
        assertThat(bookingWorkflowService.getRoute(bookingA.getId()).getPickupAccess())
                .isEqualTo(new RouteAccessInfo(4, false, 60, null));
        // Đã đủ điều kiện lập báo giá chính thức
        assertThat(surveyCompletionChecker.isLatestSurveyCompleted(bookingA.getId())).isTrue();
    }

    @Test
    @DisplayName("IT2 — Khảo sát đang làm dở: chưa được lập báo giá chính thức")
    void it2_inProgressNotCompleted() {
        Integer id = createSurvey(bookingA, slot1, mover1Id, dispatcherId).getId();
        assertThat(surveyCompletionChecker.isLatestSurveyCompleted(bookingA.getId())).isFalse();

        startAt(id, slot1, mover1Id);

        assertThat(surveyStatus(id)).isEqualTo(SurveyStatus.IN_PROGRESS);
        assertThat(surveyCompletionChecker.isLatestSurveyCompleted(bookingA.getId())).isFalse();
        // Chưa có đồ thì không hoàn tất được (bản nháp có đồ khách khai thì bỏ hết trước)
        quotationDraftService.listItems(quotationDraftService.findCurrentDraft(bookingA.getId()).orElseThrow().id())
                .forEach(item -> fieldSurveyService.deleteItem(id, item.id(), mover1Id));
        assertErrorCode(() -> fieldSurveyService.complete(id, null, mover1Id), ErrorCode.ERR_SURVEY_RESULT_INCOMPLETE);
    }

    @Test
    @DisplayName("IT3 — Trùng lịch (ADMIN): cùng MOVER, cùng ngày, cùng khung cho 2 booking → ERR_SURVEY_STAFF_CONFLICT")
    void it3_staffConflict() {
        createSurvey(bookingA, slot1, mover1Id, adminId);

        assertErrorCode(() -> createSurvey(bookingB, slot1, mover1Id, adminId), ErrorCode.ERR_SURVEY_STAFF_CONFLICT);
        // Khung khác hoặc nhân viên khác thì được
        assertThat(createSurvey(bookingB, slot1, mover2Id, adminId).getStatus()).isEqualTo(SurveyStatus.CONFIRMED);
    }

    @Test
    @DisplayName("IT4 — Đổi lịch: sang khung khác, note ghi lịch cũ → mới, khung cũ trống cho lịch khác")
    void it4_reschedule() {
        Integer id = createSurvey(bookingA, slot1, mover1Id, dispatcherId).getId();

        SurveyDetailResponse moved = managementService.reschedule(id, RescheduleSurveyRequest.builder()
                .surveyDate(DAY).timeSlotId(slot2.getId()).reason("Khách bận buổi sáng").build(), dispatcherId);

        assertThat(moved.getTimeSlot().getId()).isEqualTo(slot2.getId());
        assertThat(moved.getStatus()).isEqualTo(SurveyStatus.CONFIRMED);
        assertThat(moved.getNote()).contains("[RESCHEDULE]").contains("→").contains("Khách bận buổi sáng");
        // Khung cũ của mover1 đã trống
        assertThat(createSurvey(bookingB, slot1, mover1Id, dispatcherId).getStatus()).isEqualTo(SurveyStatus.CONFIRMED);
    }

    @Test
    @DisplayName("IT5 — Hủy khảo sát: CANCELLED, tạo được lịch mới cho cùng booking")
    void it5_cancel() {
        Integer id = createSurvey(bookingA, slot1, mover1Id, dispatcherId).getId();

        SurveyDetailResponse cancelled = managementService.cancel(id,
                CancelSurveyRequest.builder().reason("Khách xin dời sang tuần sau").build(), dispatcherId);

        assertThat(cancelled.getStatus()).isEqualTo(SurveyStatus.CANCELLED);
        assertThat(createSurvey(bookingA, slot2, mover1Id, dispatcherId).getStatus()).isEqualTo(SurveyStatus.CONFIRMED);
        assertThat(surveyQueryService.getByBooking(bookingA.getId())).hasSize(2);
    }

    @Test
    @DisplayName("IT6 — Khảo sát thất bại: RESURVEY_REQUIRED, báo giá v1 EXPIRED, booking về PENDING_SURVEY")
    void it6_fail() {
        Integer id = createSurvey(bookingA, slot1, mover1Id, dispatcherId).getId();
        startAt(id, slot1, mover1Id);

        fieldSurveyService.fail(id, FailSurveyRequest.builder().reason("Khách vắng nhà").build(), mover1Id);

        assertThat(surveyStatus(id)).isEqualTo(SurveyStatus.RESURVEY_REQUIRED);
        assertThat(quotationRepository.findTopByBookingIdOrderByVersionDesc(bookingA.getId()).orElseThrow())
                .satisfies(q -> {
                    assertThat(q.getVersion()).isEqualTo(1);
                    assertThat(q.getStatus()).isEqualTo(QuotationStatus.EXPIRED);
                });
        assertThat(bookingStatus(bookingA)).isEqualTo(BookingStatus.PENDING_SURVEY);
        assertThat(surveyCompletionChecker.isLatestSurveyCompleted(bookingA.getId())).isFalse();
    }

    @Test
    @DisplayName("IT7 — Khảo sát lại sau khi hoàn tất: lịch cũ RESURVEY_REQUIRED, v1 EXPIRED, v2 DRAFT copy đồ từ v1")
    void it7_resurvey() {
        Integer firstId = createSurvey(bookingA, slot1, mover1Id, dispatcherId).getId();
        startAt(firstId, slot1, mover1Id);
        completeWithOneExtraItem(firstId, mover1Id);
        Integer v1Id = quotationDraftService.findCurrentDraft(bookingA.getId()).orElseThrow().id();
        List<String> v1Items = quotationDraftService.listItems(v1Id).stream().map(QuotationItemDto::itemName).toList();

        // Dispatcher yêu cầu khảo sát lại vào khung 2 cùng ngày
        clock.set(DAY.atTime(slot1.getStartTime()).plusHours(1));
        SurveyDetailResponse second = managementService.resurvey(firstId, ResurveyRequest.builder()
                .reason("Khách thêm đồ").surveyDate(DAY).timeSlotId(slot2.getId()).staffId(mover1Id).build(), dispatcherId);
        assertThat(bookingStatus(bookingA)).isEqualTo(BookingStatus.PENDING_SURVEY);

        FieldSurveyResponse restarted = startAt(second.getId(), slot2, mover1Id);

        assertThat(surveyStatus(firstId)).isEqualTo(SurveyStatus.RESURVEY_REQUIRED);
        assertThat(restarted.getStatus()).isEqualTo(SurveyStatus.IN_PROGRESS);
        assertThat(quotationRepository.findById(v1Id).orElseThrow().getStatus()).isEqualTo(QuotationStatus.EXPIRED);
        QuotationDraftDto v2 = quotationDraftService.findCurrentDraft(bookingA.getId()).orElseThrow();
        assertThat(v2.version()).isEqualTo(2);
        assertThat(restarted.getDraftItems()).extracting(QuotationItemDto::itemName).containsExactlyElementsOf(v1Items);
        assertThat(surveyQueryService.getByBooking(bookingA.getId())).extracting(SurveySummaryResponse::getId)
                .containsExactly(second.getId(), firstId);
        // Khách thấy cả 2 lần khảo sát
        assertThat(customerSurveyService.getByBooking(bookingA.getId(), bookingA.getCustomer().getId()))
                .extracting(CustomerSurveyResponse::getId).containsExactly(second.getId(), firstId);
    }

    @Test
    @DisplayName("IT8 — Đã chấp nhận báo giá thì không khảo sát lại được → ERR_QUOTATION_ALREADY_ACCEPTED")
    void it8_resurveyBlockedAfterAccepted() {
        Integer id = createSurvey(bookingA, slot1, mover1Id, dispatcherId).getId();
        startAt(id, slot1, mover1Id);
        completeWithOneExtraItem(id, mover1Id);
        Quotation v1 = quotationRepository.findTopByBookingIdOrderByVersionDesc(bookingA.getId()).orElseThrow();
        v1.setStatus(QuotationStatus.ACCEPTED);
        quotationRepository.saveAndFlush(v1);

        assertErrorCode(() -> managementService.resurvey(id, ResurveyRequest.builder()
                        .reason("Khách đổi ý").surveyDate(DAY).timeSlotId(slot2.getId()).staffId(mover1Id).build(), dispatcherId),
                ErrorCode.ERR_QUOTATION_ALREADY_ACCEPTED);
        assertThat(surveyStatus(id)).isEqualTo(SurveyStatus.COMPLETED);
    }

    @Test
    @DisplayName("IT9 — Khách hủy đơn: lịch khảo sát CONFIRMED tự chuyển CANCELLED trong cùng transaction")
    void it9_bookingCancelledCascades() {
        Integer id = createSurvey(bookingA, slot1, mover1Id, dispatcherId).getId();

        bookingService.cancelBooking(bookingA.getId(), CancelBookingRequest.builder().reason("Không chuyển nữa").build(),
                bookingA.getCustomer().getId());

        assertThat(bookingStatus(bookingA)).isEqualTo(BookingStatus.CANCELLED);
        BookingSurvey survey = bookingSurveyRepository.findById(id).orElseThrow();
        assertThat(survey.getStatus()).isEqualTo(SurveyStatus.CANCELLED);
        assertThat(survey.getNote()).contains("[CANCEL]");
        assertThat(quotationDraftService.findCurrentDraft(bookingA.getId())).isEmpty();
    }

    @Test
    @DisplayName("IT11 — Chống IDOR qua API: mover2 bắt đầu lịch của mover1, customer2 xem khảo sát đơn customer1 → 403")
    void it11_idor() throws Exception {
        Integer id = createSurvey(bookingA, slot1, mover1Id, dispatcherId).getId();
        clock.set(DAY.atTime(slot1.getStartTime()).plusMinutes(30));

        mockMvc.perform(patch("/api/v1/field/surveys/{id}/start", id)
                        .with(user(new CustomUserDetails(userRepository.findByEmail("mover2@housemoving.vn").orElseThrow()))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(ErrorCode.ERR_SURVEY_FORBIDDEN.getCode()));
        assertThat(surveyStatus(id)).isEqualTo(SurveyStatus.CONFIRMED);

        mockMvc.perform(get("/api/v1/bookings/{bookingId}/surveys", bookingA.getId())
                        .with(user(new CustomUserDetails(userRepository.findByEmail("customer2@gmail.com").orElseThrow()))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(ErrorCode.ERR_BOOKING_FORBIDDEN.getCode()));

        // Chủ đơn xem được, và không thấy ghi chú nội bộ
        mockMvc.perform(get("/api/v1/bookings/{bookingId}/surveys", bookingA.getId())
                        .with(user(new CustomUserDetails(userRepository.findByEmail("customer1@gmail.com").orElseThrow()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(id))
                .andExpect(jsonPath("$.data[0].note").doesNotExist());
    }
}
