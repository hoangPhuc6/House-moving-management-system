package com.example.house_moving_service.modules.scheduling.service.impl;

import com.example.house_moving_service.common.exception.AppException;
import com.example.house_moving_service.common.exception.ErrorCode;
import com.example.house_moving_service.modules.booking.dto.response.BookingSnapshot;
import com.example.house_moving_service.modules.booking.entity.Booking;
import com.example.house_moving_service.modules.booking.enums.BookingStatus;
import com.example.house_moving_service.modules.booking.service.BookingWorkflowService;
import com.example.house_moving_service.modules.identity.entity.User;
import com.example.house_moving_service.modules.scheduling.dto.request.CustomerRejectSurchargeRequest;
import com.example.house_moving_service.modules.scheduling.dto.response.CustomerSurchargeResponse;
import com.example.house_moving_service.modules.scheduling.dto.response.CustomerSurchargeSummaryResponse;
import com.example.house_moving_service.modules.scheduling.entity.BookingSurcharge;
import com.example.house_moving_service.modules.scheduling.enums.SurchargeStatus;
import com.example.house_moving_service.modules.scheduling.enums.SurchargeType;
import com.example.house_moving_service.modules.scheduling.mapper.SurchargeMapper;
import com.example.house_moving_service.modules.scheduling.repository.BookingSurchargeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
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
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomerSurchargeServiceTest {

    private static final long CUSTOMER_ID = 20L;
    private static final long OTHER_CUSTOMER_ID = 21L;
    private static final int BOOKING_ID = 7;
    private static final int OTHER_BOOKING_ID = 70;
    private static final int SURCHARGE_ID = 3;
    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 2, 9, 0);
    private static final Clock CLOCK = Clock.fixed(NOW.atZone(ZONE).toInstant(), ZONE);

    @Mock
    private BookingSurchargeRepository surchargeRepository;
    @Mock
    private BookingWorkflowService bookingWorkflowService;

    private CustomerSurchargeServiceImpl service;

    @BeforeEach
    void setUp() {
        // Khách không tạo khoản mới nên SurchargeSupport chỉ cần repository (các phụ thuộc khác không dùng tới)
        SurchargeSupport support = new SurchargeSupport(surchargeRepository, null, bookingWorkflowService, null, null);
        service = new CustomerSurchargeServiceImpl(surchargeRepository, bookingWorkflowService, support, new SurchargeMapper(), CLOCK);

        lenient().when(bookingWorkflowService.getSnapshot(BOOKING_ID)).thenReturn(booking(BookingStatus.CONFIRMED));
        lenient().when(surchargeRepository.save(any(BookingSurcharge.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private static BookingSnapshot booking(BookingStatus status) {
        return BookingSnapshot.builder().id(BOOKING_ID).bookingCode("BK-0007").status(status).customerUserId(CUSTOMER_ID).build();
    }

    private static BookingSurcharge surcharge(int id, int bookingId, SurchargeStatus status, String amount) {
        BigDecimal value = new BigDecimal(amount);
        return BookingSurcharge.builder()
                .id(id).booking(Booking.builder().id(bookingId).build()).surchargeType(SurchargeType.OTHER)
                .description("mô tả").quantity(1).unitPrice(value).amount(value).status(status)
                .requestedBy(User.builder().id(8L).build()).reviewNote("ghi chú nội bộ")
                .build();
    }

    private void givenLocked(BookingSurcharge surcharge) {
        when(surchargeRepository.findByIdForUpdate(surcharge.getId())).thenReturn(Optional.of(surcharge));
    }

    private static void assertError(Runnable call, ErrorCode expected) {
        assertThatThrownBy(call::run)
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", expected);
    }

    @Test
    @DisplayName("getSurcharges: xem đơn của người khác → 4004; chỉ trả WAITING_CUSTOMER / ACCEPTED / REJECTED_BY_CUSTOMER kèm tổng")
    void testGetSurcharges() {
        assertError(() -> service.getSurcharges(BOOKING_ID, OTHER_CUSTOMER_ID), ErrorCode.ERR_BOOKING_FORBIDDEN);

        BookingSurcharge collected = surcharge(1, BOOKING_ID, SurchargeStatus.ACCEPTED, "100000");
        collected.setCollectedAt(NOW);
        when(surchargeRepository.findAllByBookingIdOrderByRequestedAtAscIdAsc(BOOKING_ID)).thenReturn(List.of(
                collected,
                surcharge(2, BOOKING_ID, SurchargeStatus.ACCEPTED, "50000"),
                surcharge(3, BOOKING_ID, SurchargeStatus.WAITING_CUSTOMER, "30000"),
                surcharge(4, BOOKING_ID, SurchargeStatus.REJECTED_BY_CUSTOMER, "20000"),
                surcharge(5, BOOKING_ID, SurchargeStatus.PROPOSED, "10000"),
                surcharge(6, BOOKING_ID, SurchargeStatus.REJECTED, "10000"),
                surcharge(7, BOOKING_ID, SurchargeStatus.CANCELLED, "10000")));

        CustomerSurchargeSummaryResponse response = service.getSurcharges(BOOKING_ID, CUSTOMER_ID);

        assertThat(response.surcharges()).extracting(CustomerSurchargeResponse::id).containsExactly(1, 2, 3, 4);
        assertThat(response.waitingCount()).isEqualTo(1);
        assertThat(response.waitingTotal()).isEqualByComparingTo("30000");
        assertThat(response.acceptedTotal()).isEqualByComparingTo("150000");
        assertThat(response.uncollectedTotal()).isEqualByComparingTo("50000");
        assertThat(response.surcharges().get(0).collected()).isTrue();
    }

    @Test
    @DisplayName("accept: khoản đơn khác / PROPOSED (khách không thấy) → 8201; đã ACCEPTED → 8202; đơn CANCELLED → 8207")
    void testAccept_Invalid() {
        givenLocked(surcharge(SURCHARGE_ID, OTHER_BOOKING_ID, SurchargeStatus.WAITING_CUSTOMER, "100000"));
        assertError(() -> service.accept(BOOKING_ID, SURCHARGE_ID, CUSTOMER_ID), ErrorCode.ERR_SURCHARGE_NOT_FOUND);

        givenLocked(surcharge(SURCHARGE_ID, BOOKING_ID, SurchargeStatus.PROPOSED, "100000"));
        assertError(() -> service.accept(BOOKING_ID, SURCHARGE_ID, CUSTOMER_ID), ErrorCode.ERR_SURCHARGE_NOT_FOUND);

        givenLocked(surcharge(SURCHARGE_ID, BOOKING_ID, SurchargeStatus.ACCEPTED, "100000"));
        assertError(() -> service.accept(BOOKING_ID, SURCHARGE_ID, CUSTOMER_ID), ErrorCode.ERR_SURCHARGE_INVALID_STATUS);

        givenLocked(surcharge(SURCHARGE_ID, BOOKING_ID, SurchargeStatus.WAITING_CUSTOMER, "100000"));
        when(bookingWorkflowService.lockForUpdate(BOOKING_ID)).thenReturn(booking(BookingStatus.CANCELLED));
        assertError(() -> service.accept(BOOKING_ID, SURCHARGE_ID, CUSTOMER_ID), ErrorCode.ERR_SURCHARGE_BOOKING_INVALID_STATUS);

        verify(bookingWorkflowService, never()).addToTotalAmount(any(), any());
    }

    @Test
    @DisplayName("accept: khóa phụ phí rồi khóa đơn, cộng amount vào tổng tiền đơn, → ACCEPTED + customerRespondedAt")
    void testAccept_Success() {
        givenLocked(surcharge(SURCHARGE_ID, BOOKING_ID, SurchargeStatus.WAITING_CUSTOMER, "100000"));
        when(bookingWorkflowService.lockForUpdate(BOOKING_ID)).thenReturn(booking(BookingStatus.CONFIRMED));
        when(bookingWorkflowService.addToTotalAmount(BOOKING_ID, new BigDecimal("100000"))).thenReturn(new BigDecimal("5100000"));

        CustomerSurchargeResponse response = service.accept(BOOKING_ID, SURCHARGE_ID, CUSTOMER_ID);

        InOrder order = inOrder(surchargeRepository, bookingWorkflowService);
        order.verify(surchargeRepository).findByIdForUpdate(SURCHARGE_ID);
        order.verify(bookingWorkflowService).lockForUpdate(BOOKING_ID);
        order.verify(bookingWorkflowService).addToTotalAmount(BOOKING_ID, new BigDecimal("100000"));
        assertThat(response.status()).isEqualTo(SurchargeStatus.ACCEPTED);
        assertThat(response.customerRespondedAt()).isEqualTo(NOW);
        assertThat(response.collected()).isFalse();
    }

    @Test
    @DisplayName("reject: → REJECTED_BY_CUSTOMER, ghi lý do + thời điểm; không cộng tiền")
    void testReject() {
        givenLocked(surcharge(SURCHARGE_ID, BOOKING_ID, SurchargeStatus.WAITING_CUSTOMER, "100000"));

        CustomerSurchargeResponse response = service.reject(BOOKING_ID, SURCHARGE_ID,
                new CustomerRejectSurchargeRequest(" Giá quá cao "), CUSTOMER_ID);

        assertThat(response.status()).isEqualTo(SurchargeStatus.REJECTED_BY_CUSTOMER);
        assertThat(response.customerNote()).isEqualTo("Giá quá cao");
        assertThat(response.customerRespondedAt()).isEqualTo(NOW);
        verify(bookingWorkflowService, never()).addToTotalAmount(any(), any());
    }

    @Test
    @DisplayName("accept / reject: đơn của người khác → 8203, không khóa phụ phí")
    void testNotOwner() {
        assertError(() -> service.accept(BOOKING_ID, SURCHARGE_ID, OTHER_CUSTOMER_ID), ErrorCode.ERR_SURCHARGE_FORBIDDEN);
        assertError(() -> service.reject(BOOKING_ID, SURCHARGE_ID, new CustomerRejectSurchargeRequest("x"), OTHER_CUSTOMER_ID),
                ErrorCode.ERR_SURCHARGE_FORBIDDEN);
        verify(surchargeRepository, never()).findByIdForUpdate(any());
    }
}
