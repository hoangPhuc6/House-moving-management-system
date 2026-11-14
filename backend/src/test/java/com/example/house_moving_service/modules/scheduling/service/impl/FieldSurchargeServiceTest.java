package com.example.house_moving_service.modules.scheduling.service.impl;

import com.example.house_moving_service.common.exception.AppException;
import com.example.house_moving_service.common.exception.ErrorCode;
import com.example.house_moving_service.modules.booking.dto.response.BookingSnapshot;
import com.example.house_moving_service.modules.booking.entity.Booking;
import com.example.house_moving_service.modules.booking.enums.BookingStatus;
import com.example.house_moving_service.modules.booking.service.BookingWorkflowService;
import com.example.house_moving_service.modules.catalog.entity.Service;
import com.example.house_moving_service.modules.catalog.entity.ServiceType;
import com.example.house_moving_service.modules.catalog.enums.FeeCategory;
import com.example.house_moving_service.modules.catalog.enums.ServiceStatus;
import com.example.house_moving_service.modules.catalog.enums.SystemServiceCode;
import com.example.house_moving_service.modules.catalog.service.ServiceLookupService;
import com.example.house_moving_service.modules.identity.entity.User;
import com.example.house_moving_service.modules.identity.service.UserLookupService;
import com.example.house_moving_service.modules.payment.dto.request.CreatePaymentRequest;
import com.example.house_moving_service.modules.payment.dto.response.PaymentResponse;
import com.example.house_moving_service.modules.payment.enums.PaymentMethod;
import com.example.house_moving_service.modules.payment.enums.PaymentType;
import com.example.house_moving_service.modules.payment.service.PaymentManagementService;
import com.example.house_moving_service.modules.scheduling.config.SurchargeProperties;
import com.example.house_moving_service.modules.scheduling.dto.request.CreateSurchargeRequest;
import com.example.house_moving_service.modules.scheduling.dto.response.FieldScheduleSurchargesResponse;
import com.example.house_moving_service.modules.scheduling.dto.response.SurchargeResponse;
import com.example.house_moving_service.modules.scheduling.entity.BookingSurcharge;
import com.example.house_moving_service.modules.scheduling.entity.MovingSchedule;
import com.example.house_moving_service.modules.scheduling.entity.ScheduleStaffAssignment;
import com.example.house_moving_service.modules.scheduling.enums.AssignmentStatus;
import com.example.house_moving_service.modules.scheduling.enums.MovingScheduleStatus;
import com.example.house_moving_service.modules.scheduling.enums.StaffAssignmentRole;
import com.example.house_moving_service.modules.scheduling.enums.SurchargeStatus;
import com.example.house_moving_service.modules.scheduling.enums.SurchargeType;
import com.example.house_moving_service.modules.scheduling.helper.SurchargePricingResolver;
import com.example.house_moving_service.modules.scheduling.mapper.SurchargeMapper;
import com.example.house_moving_service.modules.scheduling.repository.BookingSurchargeRepository;
import com.example.house_moving_service.modules.scheduling.repository.IncidentRepository;
import com.example.house_moving_service.modules.scheduling.repository.MovingScheduleRepository;
import com.example.house_moving_service.modules.scheduling.repository.ScheduleStaffAssignmentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FieldSurchargeServiceTest {

    private static final long SUPERVISOR_ID = 8L;
    private static final long MOVER_ID = 9L;
    private static final int SCHEDULE_ID = 50;
    private static final int BOOKING_ID = 7;
    private static final int SURCHARGE_ID = 3;
    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 2, 9, 0);
    private static final Clock CLOCK = Clock.fixed(NOW.atZone(ZONE).toInstant(), ZONE);

    @Mock
    private BookingSurchargeRepository surchargeRepository;
    @Mock
    private MovingScheduleRepository movingScheduleRepository;
    @Mock
    private ScheduleStaffAssignmentRepository staffAssignmentRepository;
    @Mock
    private IncidentRepository incidentRepository;
    @Mock
    private BookingWorkflowService bookingWorkflowService;
    @Mock
    private UserLookupService userLookupService;
    @Mock
    private ServiceLookupService serviceLookupService;
    @Mock
    private PaymentManagementService paymentManagementService;

    private FieldSurchargeServiceImpl service;

    @BeforeEach
    void setUp() {
        SurchargePricingResolver pricingResolver = new SurchargePricingResolver(serviceLookupService, new SurchargeProperties());
        SurchargeSupport support = new SurchargeSupport(surchargeRepository, incidentRepository, bookingWorkflowService,
                userLookupService, pricingResolver);
        service = new FieldSurchargeServiceImpl(surchargeRepository, movingScheduleRepository, staffAssignmentRepository,
                bookingWorkflowService, userLookupService, paymentManagementService, support, new SurchargeMapper(), CLOCK);

        lenient().when(userLookupService.getUserReference(anyLong()))
                .thenAnswer(inv -> User.builder().id(inv.getArgument(0)).build());
        lenient().when(bookingWorkflowService.getReference(BOOKING_ID)).thenReturn(bookingEntity());
        lenient().when(surchargeRepository.save(any(BookingSurcharge.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    // ---------- fixtures ----------

    private static Booking bookingEntity() {
        return Booking.builder().id(BOOKING_ID).bookingCode("BK-0007").build();
    }

    private static MovingSchedule schedule(MovingScheduleStatus status) {
        return MovingSchedule.builder().id(SCHEDULE_ID).booking(bookingEntity())
                .scheduledStartAt(NOW).scheduledEndAt(NOW.plusHours(4)).status(status).build();
    }

    private static BookingSnapshot booking(BookingStatus status) {
        return BookingSnapshot.builder().id(BOOKING_ID).bookingCode("BK-0007").status(status).customerUserId(20L).build();
    }

    private static BookingSurcharge surcharge(SurchargeStatus status, long requestedBy, MovingSchedule schedule) {
        return BookingSurcharge.builder()
                .id(SURCHARGE_ID).booking(bookingEntity()).schedule(schedule)
                .surchargeType(SurchargeType.WAITING_TIME).description("chờ 1 giờ × 150.000đ")
                .quantity(1).unitPrice(new BigDecimal("150000.00")).amount(new BigDecimal("150000.00"))
                .status(status).requestedBy(User.builder().id(requestedBy).build()).requestedAt(NOW.minusHours(1))
                .build();
    }

    private void givenAssignment(long userId, StaffAssignmentRole role) {
        when(staffAssignmentRepository.findByMovingScheduleIdAndStaffId(SCHEDULE_ID, userId)).thenReturn(Optional.of(
                ScheduleStaffAssignment.builder().id((int) userId).role(role).status(AssignmentStatus.CONFIRMED).build()));
    }

    private static CreateSurchargeRequest request(SurchargeType type, int quantity, BigDecimal unitPrice, Integer serviceId) {
        return CreateSurchargeRequest.builder().surchargeType(type).description(" Thực tế không có thang máy ")
                .quantity(quantity).unitPrice(unitPrice).serviceId(serviceId).build();
    }

    private static Service stairsService() {
        return Service.builder().id(90).code(SystemServiceCode.SURCHARGE_STAIRS).name("Phụ phí thang bộ")
                .basePrice(new BigDecimal("200000.00")).status(ServiceStatus.ACTIVE)
                .serviceType(ServiceType.builder().id(9).isActive(true).feeCategory(FeeCategory.TERRAIN).build())
                .build();
    }

    private static void assertError(Runnable call, ErrorCode expected) {
        assertThatThrownBy(call::run)
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", expected);
    }

    // ---------- propose ----------

    @Test
    @DisplayName("propose: không phải SUPERVISOR → 8004; chuyến PLANNED → 8204; đơn COMPLETED → 8207")
    void testPropose_Invalid() {
        when(movingScheduleRepository.findById(SCHEDULE_ID)).thenReturn(Optional.of(schedule(MovingScheduleStatus.CONFIRMED)));
        givenAssignment(MOVER_ID, StaffAssignmentRole.MOVER);
        assertError(() -> service.propose(SCHEDULE_ID, request(SurchargeType.NO_ELEVATOR, 2, null, null), MOVER_ID),
                ErrorCode.ERR_MOVING_NOT_SUPERVISOR);

        givenAssignment(SUPERVISOR_ID, StaffAssignmentRole.SUPERVISOR);
        when(movingScheduleRepository.findById(SCHEDULE_ID)).thenReturn(Optional.of(schedule(MovingScheduleStatus.PLANNED)));
        assertError(() -> service.propose(SCHEDULE_ID, request(SurchargeType.NO_ELEVATOR, 2, null, null), SUPERVISOR_ID),
                ErrorCode.ERR_SURCHARGE_SCHEDULE_INVALID);

        when(movingScheduleRepository.findById(SCHEDULE_ID)).thenReturn(Optional.of(schedule(MovingScheduleStatus.IN_PROGRESS)));
        when(bookingWorkflowService.getSnapshot(BOOKING_ID)).thenReturn(booking(BookingStatus.COMPLETED));
        assertError(() -> service.propose(SCHEDULE_ID, request(SurchargeType.NO_ELEVATOR, 2, null, null), SUPERVISOR_ID),
                ErrorCode.ERR_SURCHARGE_BOOKING_INVALID_STATUS);
        verify(surchargeRepository, never()).save(any());
    }

    @Test
    @DisplayName("propose: loại có trong danh mục mà gửi unitPrice → 8205; WAITING_TIME thiếu unitPrice → 8205")
    void testPropose_InvalidAmount() {
        when(movingScheduleRepository.findById(SCHEDULE_ID)).thenReturn(Optional.of(schedule(MovingScheduleStatus.CONFIRMED)));
        givenAssignment(SUPERVISOR_ID, StaffAssignmentRole.SUPERVISOR);
        when(bookingWorkflowService.getSnapshot(BOOKING_ID)).thenReturn(booking(BookingStatus.CONFIRMED));

        assertError(() -> service.propose(SCHEDULE_ID,
                request(SurchargeType.NO_ELEVATOR, 2, new BigDecimal("1000"), null), SUPERVISOR_ID),
                ErrorCode.ERR_SURCHARGE_INVALID_AMOUNT);
        assertError(() -> service.propose(SCHEDULE_ID, request(SurchargeType.WAITING_TIME, 1, null, null), SUPERVISOR_ID),
                ErrorCode.ERR_SURCHARGE_INVALID_AMOUNT);
    }

    @Test
    @DisplayName("propose: NO_ELEVATOR lấy đơn giá SURCHARGE_STAIRS, amount = quantity × base_price, PROPOSED, gắn chuyến")
    void testPropose_Success() {
        when(movingScheduleRepository.findById(SCHEDULE_ID)).thenReturn(Optional.of(schedule(MovingScheduleStatus.CONFIRMED)));
        givenAssignment(SUPERVISOR_ID, StaffAssignmentRole.SUPERVISOR);
        when(bookingWorkflowService.getSnapshot(BOOKING_ID)).thenReturn(booking(BookingStatus.CONFIRMED));
        when(serviceLookupService.findByCode(SystemServiceCode.SURCHARGE_STAIRS)).thenReturn(Optional.of(stairsService()));

        SurchargeResponse response = service.propose(SCHEDULE_ID, request(SurchargeType.NO_ELEVATOR, 2, null, null), SUPERVISOR_ID);

        assertThat(response.status()).isEqualTo(SurchargeStatus.PROPOSED);
        assertThat(response.scheduleId()).isEqualTo(SCHEDULE_ID);
        assertThat(response.serviceId()).isEqualTo(90);
        assertThat(response.unitPrice()).isEqualByComparingTo("200000");
        assertThat(response.amount()).isEqualByComparingTo("400000");
        assertThat(response.requestedBy()).isEqualTo(SUPERVISOR_ID);
        assertThat(response.requestedAt()).isEqualTo(NOW);
        assertThat(response.description()).isEqualTo("Thực tế không có thang máy");
    }

    // ---------- getBySchedule ----------

    @Test
    @DisplayName("getBySchedule: mọi role được phân công xem được; startBlocked tính trên toàn đơn; uncollectedTotal")
    void testGetBySchedule() {
        when(movingScheduleRepository.findById(SCHEDULE_ID)).thenReturn(Optional.of(schedule(MovingScheduleStatus.CONFIRMED)));
        givenAssignment(MOVER_ID, StaffAssignmentRole.MOVER);
        BookingSurcharge accepted = surcharge(SurchargeStatus.ACCEPTED, SUPERVISOR_ID, null);
        when(surchargeRepository.findAllForSchedule(BOOKING_ID, SCHEDULE_ID)).thenReturn(List.of(accepted));
        when(surchargeRepository.findIdsByBookingIdAndStatusIn(BOOKING_ID, SurchargeStatus.BLOCKING_START)).thenReturn(List.of(11));

        FieldScheduleSurchargesResponse response = service.getBySchedule(SCHEDULE_ID, MOVER_ID);

        assertThat(response.startBlocked()).isTrue();
        assertThat(response.blockingSurchargeIds()).containsExactly(11);
        assertThat(response.acceptedTotal()).isEqualByComparingTo("150000");
        assertThat(response.uncollectedTotal()).isEqualByComparingTo("150000");
        assertThat(response.surcharges()).hasSize(1);
    }

    // ---------- cancel ----------

    @Test
    @DisplayName("cancel: người khác → 8203; không còn PROPOSED → 8202; người đề xuất → CANCELLED")
    void testCancel() {
        when(surchargeRepository.findByIdForUpdate(SURCHARGE_ID))
                .thenReturn(Optional.of(surcharge(SurchargeStatus.PROPOSED, SUPERVISOR_ID, schedule(MovingScheduleStatus.CONFIRMED))));
        assertError(() -> service.cancel(SURCHARGE_ID, MOVER_ID), ErrorCode.ERR_SURCHARGE_FORBIDDEN);

        when(surchargeRepository.findByIdForUpdate(SURCHARGE_ID))
                .thenReturn(Optional.of(surcharge(SurchargeStatus.WAITING_CUSTOMER, SUPERVISOR_ID, null)));
        assertError(() -> service.cancel(SURCHARGE_ID, SUPERVISOR_ID), ErrorCode.ERR_SURCHARGE_INVALID_STATUS);

        when(surchargeRepository.findByIdForUpdate(SURCHARGE_ID))
                .thenReturn(Optional.of(surcharge(SurchargeStatus.PROPOSED, SUPERVISOR_ID, null)));
        assertThat(service.cancel(SURCHARGE_ID, SUPERVISOR_ID).status()).isEqualTo(SurchargeStatus.CANCELLED);
    }

    // ---------- collect ----------

    @Test
    @DisplayName("collect: không ACCEPTED → 8202; đã thu → 8202; MOVER → 8004; không có phụ phí → 8201")
    void testCollect_Invalid() {
        when(surchargeRepository.findByIdForUpdate(SURCHARGE_ID)).thenReturn(Optional.empty());
        assertError(() -> service.collect(SURCHARGE_ID, SUPERVISOR_ID), ErrorCode.ERR_SURCHARGE_NOT_FOUND);

        MovingSchedule schedule = schedule(MovingScheduleStatus.IN_PROGRESS);
        givenAssignment(SUPERVISOR_ID, StaffAssignmentRole.SUPERVISOR);
        when(surchargeRepository.findByIdForUpdate(SURCHARGE_ID))
                .thenReturn(Optional.of(surcharge(SurchargeStatus.WAITING_CUSTOMER, SUPERVISOR_ID, schedule)));
        assertError(() -> service.collect(SURCHARGE_ID, SUPERVISOR_ID), ErrorCode.ERR_SURCHARGE_INVALID_STATUS);

        BookingSurcharge collected = surcharge(SurchargeStatus.ACCEPTED, SUPERVISOR_ID, schedule);
        collected.setCollectedAt(NOW.minusMinutes(5));
        collected.setPaymentId(77);
        when(surchargeRepository.findByIdForUpdate(SURCHARGE_ID)).thenReturn(Optional.of(collected));
        assertError(() -> service.collect(SURCHARGE_ID, SUPERVISOR_ID), ErrorCode.ERR_SURCHARGE_INVALID_STATUS);

        givenAssignment(MOVER_ID, StaffAssignmentRole.MOVER);
        assertError(() -> service.collect(SURCHARGE_ID, MOVER_ID), ErrorCode.ERR_MOVING_NOT_SUPERVISOR);
        verify(paymentManagementService, never()).recordManualPayment(any(), anyLong());
    }

    @Test
    @DisplayName("collect: tạo phiếu thu CASH / PARTIAL đúng số tiền, ghi paymentId, collectedBy, collectedAt")
    void testCollect_Success() {
        givenAssignment(SUPERVISOR_ID, StaffAssignmentRole.SUPERVISOR);
        when(surchargeRepository.findByIdForUpdate(SURCHARGE_ID)).thenReturn(Optional.of(
                surcharge(SurchargeStatus.ACCEPTED, SUPERVISOR_ID, schedule(MovingScheduleStatus.IN_PROGRESS))));
        when(paymentManagementService.recordManualPayment(any(CreatePaymentRequest.class), eq(SUPERVISOR_ID)))
                .thenReturn(PaymentResponse.builder().id(77).build());

        SurchargeResponse response = service.collect(SURCHARGE_ID, SUPERVISOR_ID);

        ArgumentCaptor<CreatePaymentRequest> captor = ArgumentCaptor.forClass(CreatePaymentRequest.class);
        verify(paymentManagementService).recordManualPayment(captor.capture(), eq(SUPERVISOR_ID));
        CreatePaymentRequest payment = captor.getValue();
        assertThat(payment.getBookingId()).isEqualTo(BOOKING_ID);
        assertThat(payment.getPaymentMethod()).isEqualTo(PaymentMethod.CASH);
        assertThat(payment.getPaymentType()).isEqualTo(PaymentType.PARTIAL);
        assertThat(payment.getAmount()).isEqualByComparingTo("150000");
        assertThat(payment.getNote()).isEqualTo("Thu phụ phí #" + SURCHARGE_ID + " tại hiện trường");

        assertThat(response.collected()).isTrue();
        assertThat(response.paymentId()).isEqualTo(77);
        assertThat(response.collectedBy()).isEqualTo(SUPERVISOR_ID);
        assertThat(response.collectedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("collect: khoản không gắn chuyến → người gọi phải là SUPERVISOR của một chuyến chưa hủy của đơn")
    void testCollect_BookingLevel() {
        when(surchargeRepository.findByIdForUpdate(SURCHARGE_ID))
                .thenReturn(Optional.of(surcharge(SurchargeStatus.ACCEPTED, SUPERVISOR_ID, null)));
        when(staffAssignmentRepository.existsActiveRoleOnBooking(BOOKING_ID, MOVER_ID, StaffAssignmentRole.SUPERVISOR,
                AssignmentStatus.CANCELLED, MovingScheduleStatus.CANCELLED)).thenReturn(false);

        assertError(() -> service.collect(SURCHARGE_ID, MOVER_ID), ErrorCode.ERR_MOVING_NOT_SUPERVISOR);
    }
}
