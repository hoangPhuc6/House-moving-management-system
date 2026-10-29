package com.example.house_moving_service.modules.booking.service;

import com.example.house_moving_service.modules.booking.dto.request.BookingManagementFilterRequest;
import com.example.house_moving_service.modules.booking.dto.request.CancelBookingRequest;
import com.example.house_moving_service.common.dto.PageResponse;
import com.example.house_moving_service.modules.booking.dto.response.BookingDetailResponse;
import com.example.house_moving_service.modules.booking.dto.response.BookingSummaryResponse;
import com.example.house_moving_service.modules.booking.entity.Booking;
import com.example.house_moving_service.modules.booking.entity.BookingItem;
import com.example.house_moving_service.modules.booking.entity.BookingRoute;
import com.example.house_moving_service.modules.booking.entity.BookingStatusHistory;
import com.example.house_moving_service.modules.booking.entity.BookingVoucher;
import com.example.house_moving_service.modules.identity.entity.Customer;
import com.example.house_moving_service.modules.identity.entity.User;
import com.example.house_moving_service.modules.voucher.entity.Voucher;
import com.example.house_moving_service.modules.booking.enums.BookingStatus;
import com.example.house_moving_service.common.exception.AppException;
import com.example.house_moving_service.common.exception.ErrorCode;
import com.example.house_moving_service.modules.booking.mapper.BookingMapper;
import com.example.house_moving_service.modules.booking.repository.BookingItemRepository;
import com.example.house_moving_service.modules.booking.repository.BookingRepository;
import com.example.house_moving_service.modules.booking.repository.BookingRouteRepository;
import com.example.house_moving_service.modules.booking.repository.BookingServiceRepository;
import com.example.house_moving_service.modules.booking.repository.BookingStatusHistoryRepository;
import com.example.house_moving_service.modules.booking.repository.BookingVoucherRepository;
import com.example.house_moving_service.modules.identity.repository.UserRepository;
import com.example.house_moving_service.modules.voucher.repository.VoucherRepository;
import com.example.house_moving_service.modules.voucher.service.impl.VoucherServiceImpl;
import com.example.house_moving_service.modules.booking.service.impl.BookingManagementServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import com.example.house_moving_service.modules.booking.event.BookingCancelledEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookingManagementServiceTest {

    @Mock
    private BookingRepository bookingRepository;
    @Mock
    private BookingRouteRepository bookingRouteRepository;
    @Mock
    private BookingItemRepository bookingItemRepository;
    @Mock
    private BookingServiceRepository bookingServiceRepository;
    @Mock
    private BookingVoucherRepository bookingVoucherRepository;
    @Mock
    private BookingStatusHistoryRepository bookingStatusHistoryRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private VoucherRepository voucherRepository;
    @Mock
    private BookingMapper bookingMapper;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private BookingManagementServiceImpl bookingManagementService;

    private Long adminUserId;
    private User adminUser;
    private Customer customer;
    private Booking booking;

    @BeforeEach
    void setUp() {
        // VoucherServiceImpl thật bọc VoucherRepository mock: các stub/verify trên voucherRepository vẫn có hiệu lực
        ReflectionTestUtils.setField(bookingManagementService, "voucherService", new VoucherServiceImpl(voucherRepository));

        adminUserId = 99L;
        adminUser = User.builder().id(adminUserId).email("admin@housemoving.vn").isActive(true).build();
        customer = Customer.builder().id(1L).fullName("Le Van B").phoneNumber("0988776655").build();

        booking = Booking.builder()
                .id(100)
                .bookingCode("BKG20261019001")
                .customer(customer)
                .status(BookingStatus.PENDING_SURVEY)
                .totalAmount(BigDecimal.valueOf(1500000))
                .discountAmount(BigDecimal.valueOf(50000))
                .build();
    }

    // ==================== getBookingDetailByManagement ====================

    @Test
    @DisplayName("Management get detail: Success should return full detail without IDOR checks")
    void testGetBookingDetailByManagement_Success() {
        BookingRoute route = BookingRoute.builder().id(1).booking(booking).pickupAddress("Point A").dropoffAddress("Point B").build();
        BookingItem item = BookingItem.builder().id(1).booking(booking).itemName("Sofa").build();
        com.example.house_moving_service.modules.booking.entity.BookingService service = com.example.house_moving_service.modules.booking.entity.BookingService.builder().id(1).booking(booking).build();
        BookingVoucher voucher = BookingVoucher.builder().id(1).booking(booking).build();
        BookingStatusHistory history = BookingStatusHistory.builder().id(1).booking(booking).newStatus(BookingStatus.PENDING_SURVEY).build();

        BookingDetailResponse expectedDetail = BookingDetailResponse.builder()
                .id(100)
                .bookingCode("BKG20261019001")
                .build();

        when(bookingRepository.findById(100)).thenReturn(Optional.of(booking));
        when(bookingRouteRepository.findByBookingId(100)).thenReturn(Optional.of(route));
        when(bookingItemRepository.findAllByBookingId(100)).thenReturn(List.of(item));
        when(bookingServiceRepository.findAllByBookingId(100)).thenReturn(List.of(service));
        when(bookingVoucherRepository.findAllByBookingId(100)).thenReturn(List.of(voucher));
        when(bookingStatusHistoryRepository.findAllByBookingIdOrderByChangedAtAsc(100)).thenReturn(List.of(history));
        when(bookingMapper.toDetailResponse(booking, route, List.of(item), List.of(service), List.of(voucher), List.of(history)))
                .thenReturn(expectedDetail);

        BookingDetailResponse response = bookingManagementService.getBookingDetailByManagement(100);

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(100);
        assertThat(response.getBookingCode()).isEqualTo("BKG20261019001");
    }

    @Test
    @DisplayName("Management get detail: Should throw ERR_BOOKING_NOT_FOUND when booking does not exist")
    void testGetBookingDetailByManagement_NotFound() {
        when(bookingRepository.findById(999)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> bookingManagementService.getBookingDetailByManagement(999))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ERR_BOOKING_NOT_FOUND);
    }

    // ==================== searchAllBookings ====================

    @Test
    @DisplayName("Management search bookings: Should return paged list with specification filter")
    void testSearchAllBookings_Success() {
        Pageable pageable = PageRequest.of(0, 10);
        BookingManagementFilterRequest filter = BookingManagementFilterRequest.builder()
                .status(BookingStatus.CONFIRMED)
                .bookingCode("BKG")
                .build();

        Booking b1 = Booking.builder().id(101).bookingCode("BKG001").status(BookingStatus.CONFIRMED).build();
        Page<Booking> page = new PageImpl<>(List.of(b1), pageable, 1);
        BookingSummaryResponse summary = BookingSummaryResponse.builder().id(101).bookingCode("BKG001").status(BookingStatus.CONFIRMED).build();

        when(bookingRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);
        when(bookingMapper.toSummaryResponse(b1)).thenReturn(summary);

        PageResponse<BookingSummaryResponse> response = bookingManagementService.searchAllBookings(filter, pageable);

        assertThat(response).isNotNull();
        assertThat(response.getPageNo()).isEqualTo(1);
        assertThat(response.getTotalElements()).isEqualTo(1);
        assertThat(response.getItems()).hasSize(1);
        assertThat(response.getItems().get(0).getBookingCode()).isEqualTo("BKG001");
    }

    // ==================== cancelBookingByManagement ====================

    @Test
    @DisplayName("Management cancel: Should cancel booking at CONFIRMED state and record history")
    void testCancelBookingByManagement_Success_ConfirmedState() {
        booking.setStatus(BookingStatus.CONFIRMED);
        Voucher voucher = Voucher.builder().id(10).code("DISCOUNT50").usedCount(5).build();
        BookingVoucher bv = BookingVoucher.builder().id(1).booking(booking).voucher(voucher).build();

        CancelBookingRequest cancelReq = CancelBookingRequest.builder().reason("Dispatcher hủy theo yêu cầu").build();

        when(userRepository.findById(adminUserId)).thenReturn(Optional.of(adminUser));
        when(bookingRepository.findById(100)).thenReturn(Optional.of(booking));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));
        when(bookingVoucherRepository.findAllByBookingId(100)).thenReturn(List.of(bv));
        when(voucherRepository.findById(10)).thenReturn(Optional.of(voucher));
        when(bookingRouteRepository.findByBookingId(100)).thenReturn(Optional.empty());
        when(bookingItemRepository.findAllByBookingId(100)).thenReturn(Collections.emptyList());
        when(bookingServiceRepository.findAllByBookingId(100)).thenReturn(Collections.emptyList());
        when(bookingStatusHistoryRepository.findAllByBookingIdOrderByChangedAtAsc(100)).thenReturn(Collections.emptyList());

        BookingDetailResponse expectedDetail = BookingDetailResponse.builder()
                .id(100)
                .status(BookingStatus.CANCELLED)
                .build();
        when(bookingMapper.toDetailResponse(any(), any(), any(), any(), any(), any()))
                .thenReturn(expectedDetail);

        BookingDetailResponse response = bookingManagementService.cancelBookingByManagement(100, cancelReq, adminUserId);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CANCELLED);

        // Verify booking, history, voucher revert
        verify(bookingRepository).save(booking);
        verify(bookingStatusHistoryRepository).save(any(BookingStatusHistory.class));
        assertThat(voucher.getUsedCount()).isEqualTo(4);
        verify(voucherRepository).save(voucher);

        // Verify event báo hủy đơn cho module khác
        verify(eventPublisher).publishEvent(new BookingCancelledEvent(100, adminUserId, "Dispatcher hủy theo yêu cầu"));
    }

    @Test
    @DisplayName("Management cancel: Should throw ERR_BOOKING_NOT_FOUND when booking does not exist")
    void testCancelBookingByManagement_NotFound() {
        CancelBookingRequest cancelReq = CancelBookingRequest.builder().reason("Hủy đơn").build();

        when(userRepository.findById(adminUserId)).thenReturn(Optional.of(adminUser));
        when(bookingRepository.findById(999)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> bookingManagementService.cancelBookingByManagement(999, cancelReq, adminUserId))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ERR_BOOKING_NOT_FOUND);
    }

    @Test
    @DisplayName("Management cancel: Should throw ERR_BOOKING_INVALID_STATUS when booking is already CANCELLED")
    void testCancelBookingByManagement_AlreadyCancelled() {
        booking.setStatus(BookingStatus.CANCELLED);
        CancelBookingRequest cancelReq = CancelBookingRequest.builder().reason("Hủy đơn").build();

        when(userRepository.findById(adminUserId)).thenReturn(Optional.of(adminUser));
        when(bookingRepository.findById(100)).thenReturn(Optional.of(booking));

        assertThatThrownBy(() -> bookingManagementService.cancelBookingByManagement(100, cancelReq, adminUserId))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ERR_BOOKING_INVALID_STATUS);
        verify(eventPublisher, never()).publishEvent(any());
    }
}

