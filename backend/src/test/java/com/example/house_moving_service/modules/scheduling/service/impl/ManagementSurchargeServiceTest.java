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
import com.example.house_moving_service.modules.catalog.service.ServiceLookupService;
import com.example.house_moving_service.modules.identity.entity.User;
import com.example.house_moving_service.modules.identity.service.UserLookupService;
import com.example.house_moving_service.modules.scheduling.config.SurchargeProperties;
import com.example.house_moving_service.modules.scheduling.dto.request.CreateSurchargeRequest;
import com.example.house_moving_service.modules.scheduling.dto.request.SendSurchargeRequest;
import com.example.house_moving_service.modules.scheduling.dto.request.SurchargeReviewNoteRequest;
import com.example.house_moving_service.modules.scheduling.dto.response.BookingSurchargeSummaryResponse;
import com.example.house_moving_service.modules.scheduling.dto.response.SurchargeResponse;
import com.example.house_moving_service.modules.scheduling.entity.BookingSurcharge;
import com.example.house_moving_service.modules.scheduling.entity.Incident;
import com.example.house_moving_service.modules.scheduling.entity.MovingSchedule;
import com.example.house_moving_service.modules.scheduling.enums.MovingScheduleStatus;
import com.example.house_moving_service.modules.scheduling.enums.SurchargeStatus;
import com.example.house_moving_service.modules.scheduling.enums.SurchargeType;
import com.example.house_moving_service.modules.scheduling.helper.SurchargePricingResolver;
import com.example.house_moving_service.modules.scheduling.mapper.SurchargeMapper;
import com.example.house_moving_service.modules.scheduling.repository.BookingSurchargeRepository;
import com.example.house_moving_service.modules.scheduling.repository.IncidentRepository;
import com.example.house_moving_service.modules.scheduling.repository.MovingScheduleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ManagementSurchargeServiceTest {

    private static final long DISPATCHER_ID = 2L;
    private static final long SUPERVISOR_ID = 8L;
    private static final int BOOKING_ID = 7;
    private static final int OTHER_BOOKING_ID = 70;
    private static final int SCHEDULE_ID = 50;
    private static final int SURCHARGE_ID = 3;
    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 2, 9, 0);
    private static final Clock CLOCK = Clock.fixed(NOW.atZone(ZONE).toInstant(), ZONE);

    @Mock
    private BookingSurchargeRepository surchargeRepository;
    @Mock
    private MovingScheduleRepository movingScheduleRepository;
    @Mock
    private IncidentRepository incidentRepository;
    @Mock
    private BookingWorkflowService bookingWorkflowService;
    @Mock
    private UserLookupService userLookupService;
    @Mock
    private ServiceLookupService serviceLookupService;

    private ManagementSurchargeServiceImpl service;

    @BeforeEach
    void setUp() {
        SurchargePricingResolver pricingResolver = new SurchargePricingResolver(serviceLookupService, new SurchargeProperties());
        SurchargeSupport support = new SurchargeSupport(surchargeRepository, incidentRepository, bookingWorkflowService,
                userLookupService, pricingResolver);
        service = new ManagementSurchargeServiceImpl(surchargeRepository, movingScheduleRepository, bookingWorkflowService,
                userLookupService, pricingResolver, support, new SurchargeMapper(), CLOCK);

        lenient().when(userLookupService.getUserReference(anyLong()))
                .thenAnswer(inv -> User.builder().id(inv.getArgument(0)).build());
        lenient().when(bookingWorkflowService.getReference(BOOKING_ID)).thenReturn(bookingEntity(BOOKING_ID));
        lenient().when(surchargeRepository.save(any(BookingSurcharge.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    // ---------- fixtures ----------

    private static Booking bookingEntity(int id) {
        return Booking.builder().id(id).bookingCode("BK-" + id).build();
    }

    private static BookingSnapshot booking(BookingStatus status) {
        return BookingSnapshot.builder().id(BOOKING_ID).bookingCode("BK-" + BOOKING_ID).status(status).build();
    }

    private static MovingSchedule schedule(int bookingId, MovingScheduleStatus status) {
        return MovingSchedule.builder().id(SCHEDULE_ID).booking(bookingEntity(bookingId))
                .scheduledStartAt(NOW).scheduledEndAt(NOW.plusHours(4)).status(status).build();
    }

    private static BookingSurcharge surcharge(int id, SurchargeType type, SurchargeStatus status, int quantity, String unitPrice) {
        BigDecimal price = new BigDecimal(unitPrice);
        return BookingSurcharge.builder()
                .id(id).booking(bookingEntity(BOOKING_ID)).surchargeType(type).description("mô tả")
                .quantity(quantity).unitPrice(price).amount(SurchargePricingResolver.amountOf(quantity, price))
                .status(status).requestedBy(User.builder().id(SUPERVISOR_ID).build()).requestedAt(NOW.minusHours(1))
                .build();
    }

    private void givenLocked(BookingSurcharge surcharge) {
        when(surchargeRepository.findByIdForUpdate(surcharge.getId())).thenReturn(Optional.of(surcharge));
    }

    private static CreateSurchargeRequest request(SurchargeType type, Integer scheduleId, Integer serviceId, BigDecimal unitPrice) {
        return CreateSurchargeRequest.builder().scheduleId(scheduleId).surchargeType(type).description("Khai thiếu tủ 3 cánh")
                .quantity(1).serviceId(serviceId).unitPrice(unitPrice).build();
    }

    private static Service catalogService(FeeCategory category, ServiceStatus status, String code) {
        return Service.builder().id(40).code(code).name("Tháo lắp tủ").basePrice(new BigDecimal("300000.00")).status(status)
                .serviceType(ServiceType.builder().id(4).isActive(true).feeCategory(category).build())
                .build();
    }

    private static void assertError(Runnable call, ErrorCode expected) {
        assertThatThrownBy(call::run)
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", expected);
    }

    // ---------- create ----------

    @Test
    @DisplayName("create: đơn CANCELLED → 8207; chuyến không thuộc đơn → 8204; chuyến COMPLETED → 8204")
    void testCreate_InvalidBookingOrSchedule() {
        when(bookingWorkflowService.getSnapshot(BOOKING_ID)).thenReturn(booking(BookingStatus.CANCELLED));
        assertError(() -> service.create(BOOKING_ID, request(SurchargeType.OTHER, null, null, BigDecimal.TEN), DISPATCHER_ID),
                ErrorCode.ERR_SURCHARGE_BOOKING_INVALID_STATUS);

        when(bookingWorkflowService.getSnapshot(BOOKING_ID)).thenReturn(booking(BookingStatus.CONFIRMED));
        when(movingScheduleRepository.findById(SCHEDULE_ID))
                .thenReturn(Optional.of(schedule(OTHER_BOOKING_ID, MovingScheduleStatus.CONFIRMED)));
        assertError(() -> service.create(BOOKING_ID, request(SurchargeType.OTHER, SCHEDULE_ID, null, BigDecimal.TEN), DISPATCHER_ID),
                ErrorCode.ERR_SURCHARGE_SCHEDULE_INVALID);

        when(movingScheduleRepository.findById(SCHEDULE_ID))
                .thenReturn(Optional.of(schedule(BOOKING_ID, MovingScheduleStatus.COMPLETED)));
        assertError(() -> service.create(BOOKING_ID, request(SurchargeType.OTHER, SCHEDULE_ID, null, BigDecimal.TEN), DISPATCHER_ID),
                ErrorCode.ERR_SURCHARGE_SCHEDULE_INVALID);
    }

    @Test
    @DisplayName("create: DISASSEMBLY thiếu serviceId / dịch vụ sai nhóm / ngừng hoạt động / dịch vụ hệ thống → 8206; sự cố khác đơn → 8206")
    void testCreate_InvalidReference() {
        when(bookingWorkflowService.getSnapshot(BOOKING_ID)).thenReturn(booking(BookingStatus.CONFIRMED));

        assertError(() -> service.create(BOOKING_ID, request(SurchargeType.DISASSEMBLY, null, null, null), DISPATCHER_ID),
                ErrorCode.ERR_SURCHARGE_REFERENCE_INVALID);

        when(serviceLookupService.findAllByIds(List.of(40)))
                .thenReturn(List.of(catalogService(FeeCategory.TRANSPORT, ServiceStatus.ACTIVE, "TRUCK_1T")));
        assertError(() -> service.create(BOOKING_ID, request(SurchargeType.DISASSEMBLY, null, 40, null), DISPATCHER_ID),
                ErrorCode.ERR_SURCHARGE_REFERENCE_INVALID);

        when(serviceLookupService.findAllByIds(List.of(40)))
                .thenReturn(List.of(catalogService(FeeCategory.TECHNICAL, ServiceStatus.INACTIVE, "DISASSEMBLE_BED")));
        assertError(() -> service.create(BOOKING_ID, request(SurchargeType.DISASSEMBLY, null, 40, null), DISPATCHER_ID),
                ErrorCode.ERR_SURCHARGE_REFERENCE_INVALID);

        when(serviceLookupService.findAllByIds(List.of(40)))
                .thenReturn(List.of(catalogService(FeeCategory.LABOR, ServiceStatus.ACTIVE, "SURCHARGE_STAIRS")));
        assertError(() -> service.create(BOOKING_ID, request(SurchargeType.DISASSEMBLY, null, 40, null), DISPATCHER_ID),
                ErrorCode.ERR_SURCHARGE_REFERENCE_INVALID);

        CreateSurchargeRequest withIncident = request(SurchargeType.OTHER, null, null, new BigDecimal("50000"));
        withIncident.setIncidentId(12);
        when(incidentRepository.findById(12))
                .thenReturn(Optional.of(Incident.builder().id(12).booking(bookingEntity(OTHER_BOOKING_ID)).build()));
        assertError(() -> service.create(BOOKING_ID, withIncident, DISPATCHER_ID), ErrorCode.ERR_SURCHARGE_REFERENCE_INVALID);
    }

    @Test
    @DisplayName("create: DISASSEMBLY lấy base_price của dịch vụ; → WAITING_CUSTOMER, requestedBy = reviewedBy = điều phối, có sentToCustomerAt")
    void testCreate_Success() {
        when(bookingWorkflowService.getSnapshot(BOOKING_ID)).thenReturn(booking(BookingStatus.IN_PROGRESS));
        when(movingScheduleRepository.findById(SCHEDULE_ID))
                .thenReturn(Optional.of(schedule(BOOKING_ID, MovingScheduleStatus.IN_PROGRESS)));
        when(serviceLookupService.findAllByIds(List.of(40)))
                .thenReturn(List.of(catalogService(FeeCategory.TECHNICAL, ServiceStatus.ACTIVE, "DISASSEMBLE_BED")));

        SurchargeResponse response = service.create(BOOKING_ID, request(SurchargeType.DISASSEMBLY, SCHEDULE_ID, 40, null), DISPATCHER_ID);

        assertThat(response.status()).isEqualTo(SurchargeStatus.WAITING_CUSTOMER);
        assertThat(response.scheduleId()).isEqualTo(SCHEDULE_ID);
        assertThat(response.unitPrice()).isEqualByComparingTo("300000");
        assertThat(response.amount()).isEqualByComparingTo("300000");
        assertThat(response.requestedBy()).isEqualTo(DISPATCHER_ID);
        assertThat(response.reviewedBy()).isEqualTo(DISPATCHER_ID);
        assertThat(response.sentToCustomerAt()).isEqualTo(NOW);
    }

    // ---------- send ----------

    @Test
    @DisplayName("send: không còn PROPOSED → 8202; sửa đơn giá loại có trong danh mục → 8205; quantity vượt giới hạn → 8205")
    void testSend_Invalid() {
        givenLocked(surcharge(SURCHARGE_ID, SurchargeType.NO_ELEVATOR, SurchargeStatus.WAITING_CUSTOMER, 2, "200000"));
        assertError(() -> service.send(SURCHARGE_ID, null, DISPATCHER_ID), ErrorCode.ERR_SURCHARGE_INVALID_STATUS);

        givenLocked(surcharge(SURCHARGE_ID, SurchargeType.NO_ELEVATOR, SurchargeStatus.PROPOSED, 2, "200000"));
        when(bookingWorkflowService.getSnapshot(BOOKING_ID)).thenReturn(booking(BookingStatus.CONFIRMED));
        assertError(() -> service.send(SURCHARGE_ID,
                SendSurchargeRequest.builder().unitPrice(new BigDecimal("1000")).build(), DISPATCHER_ID),
                ErrorCode.ERR_SURCHARGE_INVALID_AMOUNT);
        assertError(() -> service.send(SURCHARGE_ID, SendSurchargeRequest.builder().quantity(101).build(), DISPATCHER_ID),
                ErrorCode.ERR_SURCHARGE_INVALID_AMOUNT);
    }

    @Test
    @DisplayName("send: loại danh mục sửa quantity → tính lại amount giữ đơn giá; WAITING_TIME sửa được đơn giá; → WAITING_CUSTOMER")
    void testSend_Success() {
        when(bookingWorkflowService.getSnapshot(BOOKING_ID)).thenReturn(booking(BookingStatus.CONFIRMED));

        givenLocked(surcharge(SURCHARGE_ID, SurchargeType.NO_ELEVATOR, SurchargeStatus.PROPOSED, 2, "200000"));
        SurchargeResponse stairs = service.send(SURCHARGE_ID,
                SendSurchargeRequest.builder().quantity(3).reviewNote(" Đã gọi xác nhận ").build(), DISPATCHER_ID);
        assertThat(stairs.status()).isEqualTo(SurchargeStatus.WAITING_CUSTOMER);
        assertThat(stairs.quantity()).isEqualTo(3);
        assertThat(stairs.amount()).isEqualByComparingTo("600000");
        assertThat(stairs.reviewedBy()).isEqualTo(DISPATCHER_ID);
        assertThat(stairs.reviewNote()).isEqualTo("Đã gọi xác nhận");
        assertThat(stairs.sentToCustomerAt()).isEqualTo(NOW);

        givenLocked(surcharge(4, SurchargeType.WAITING_TIME, SurchargeStatus.PROPOSED, 1, "150000"));
        SurchargeResponse waiting = service.send(4,
                SendSurchargeRequest.builder().quantity(2).unitPrice(new BigDecimal("120000")).build(), DISPATCHER_ID);
        assertThat(waiting.unitPrice()).isEqualByComparingTo("120000");
        assertThat(waiting.amount()).isEqualByComparingTo("240000");
    }

    // ---------- reject / withdraw ----------

    @Test
    @DisplayName("reject: PROPOSED → REJECTED, ghi người duyệt + lý do; khoản đã gửi khách → 8202")
    void testReject() {
        givenLocked(surcharge(SURCHARGE_ID, SurchargeType.OTHER, SurchargeStatus.WAITING_CUSTOMER, 1, "100000"));
        assertError(() -> service.reject(SURCHARGE_ID, new SurchargeReviewNoteRequest("sai"), DISPATCHER_ID),
                ErrorCode.ERR_SURCHARGE_INVALID_STATUS);

        givenLocked(surcharge(SURCHARGE_ID, SurchargeType.OTHER, SurchargeStatus.PROPOSED, 1, "100000"));
        SurchargeResponse response = service.reject(SURCHARGE_ID, new SurchargeReviewNoteRequest(" Không đúng thực tế "), DISPATCHER_ID);
        assertThat(response.status()).isEqualTo(SurchargeStatus.REJECTED);
        assertThat(response.reviewNote()).isEqualTo("Không đúng thực tế");
        assertThat(response.reviewedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("withdraw: WAITING_CUSTOMER / REJECTED_BY_CUSTOMER → CANCELLED; PROPOSED / ACCEPTED → 8202")
    void testWithdraw() {
        for (SurchargeStatus from : List.of(SurchargeStatus.WAITING_CUSTOMER, SurchargeStatus.REJECTED_BY_CUSTOMER)) {
            givenLocked(surcharge(SURCHARGE_ID, SurchargeType.OTHER, from, 1, "100000"));
            assertThat(service.withdraw(SURCHARGE_ID, new SurchargeReviewNoteRequest("thương lượng lại"), DISPATCHER_ID).status())
                    .isEqualTo(SurchargeStatus.CANCELLED);
        }
        for (SurchargeStatus from : List.of(SurchargeStatus.PROPOSED, SurchargeStatus.ACCEPTED)) {
            givenLocked(surcharge(SURCHARGE_ID, SurchargeType.OTHER, from, 1, "100000"));
            assertError(() -> service.withdraw(SURCHARGE_ID, new SurchargeReviewNoteRequest("x"), DISPATCHER_ID),
                    ErrorCode.ERR_SURCHARGE_INVALID_STATUS);
        }
    }

    // ---------- getByBooking ----------

    @Test
    @DisplayName("getByBooking: tổng theo từng trạng thái, acceptedTotal, uncollectedTotal, id khoản đang chặn")
    void testGetByBooking() {
        when(bookingWorkflowService.getSnapshot(BOOKING_ID)).thenReturn(booking(BookingStatus.IN_PROGRESS));
        BookingSurcharge collected = surcharge(1, SurchargeType.OTHER, SurchargeStatus.ACCEPTED, 1, "100000");
        collected.setCollectedAt(NOW);
        collected.setPaymentId(77);
        when(surchargeRepository.findAllByBookingIdOrderByRequestedAtAscIdAsc(BOOKING_ID)).thenReturn(List.of(
                collected,
                surcharge(2, SurchargeType.OTHER, SurchargeStatus.ACCEPTED, 1, "50000"),
                surcharge(3, SurchargeType.OTHER, SurchargeStatus.WAITING_CUSTOMER, 2, "30000"),
                surcharge(4, SurchargeType.OTHER, SurchargeStatus.REJECTED, 1, "10000")));

        BookingSurchargeSummaryResponse response = service.getByBooking(BOOKING_ID);

        assertThat(response.acceptedTotal()).isEqualByComparingTo("150000");
        assertThat(response.uncollectedTotal()).isEqualByComparingTo("50000");
        assertThat(response.totalsByStatus().get(SurchargeStatus.WAITING_CUSTOMER)).isEqualByComparingTo("60000");
        assertThat(response.totalsByStatus().get(SurchargeStatus.CANCELLED)).isEqualByComparingTo("0");
        assertThat(response.blockingSurchargeIds()).containsExactly(3);
        assertThat(response.surcharges()).hasSize(4);
    }
}
