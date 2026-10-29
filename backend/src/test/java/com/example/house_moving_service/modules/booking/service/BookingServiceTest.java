package com.example.house_moving_service.modules.booking.service;

import com.example.house_moving_service.modules.booking.dto.request.BookingItemRequest;
import com.example.house_moving_service.modules.booking.dto.request.CancelBookingRequest;
import com.example.house_moving_service.modules.booking.dto.request.CreateBookingRequest;
import com.example.house_moving_service.modules.pricing.dto.request.LocationDto;
import com.example.house_moving_service.modules.pricing.dto.request.RouteDto;
import com.example.house_moving_service.modules.pricing.dto.request.ScheduleDto;
import com.example.house_moving_service.modules.pricing.dto.request.ServiceItemDto;
import com.example.house_moving_service.common.dto.PageResponse;
import com.example.house_moving_service.modules.booking.dto.response.BookingDetailResponse;
import com.example.house_moving_service.modules.booking.dto.response.BookingSummaryResponse;
import com.example.house_moving_service.modules.pricing.dto.response.AppliedVoucherDto;
import com.example.house_moving_service.modules.pricing.dto.response.BreakdownItemDto;
import com.example.house_moving_service.modules.pricing.dto.response.FinancialSummaryDto;
import com.example.house_moving_service.modules.pricing.dto.response.PricingCalculationResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import java.util.Collections;
import com.example.house_moving_service.modules.booking.entity.Booking;
import com.example.house_moving_service.modules.booking.entity.BookingItem;
import com.example.house_moving_service.modules.booking.entity.BookingRoute;
import com.example.house_moving_service.modules.booking.entity.BookingStatusHistory;
import com.example.house_moving_service.modules.booking.entity.BookingVoucher;
import com.example.house_moving_service.modules.identity.entity.Customer;
import com.example.house_moving_service.modules.catalog.entity.Service;
import com.example.house_moving_service.modules.identity.entity.User;
import com.example.house_moving_service.modules.pricing.service.PricingService;
import com.example.house_moving_service.modules.voucher.entity.Voucher;
import com.example.house_moving_service.modules.booking.enums.BookingStatus;
import com.example.house_moving_service.common.enums.PropertyType;
import com.example.house_moving_service.common.exception.AppException;
import com.example.house_moving_service.common.exception.ErrorCode;
import com.example.house_moving_service.modules.booking.mapper.BookingMapper;
import com.example.house_moving_service.modules.booking.repository.BookingItemRepository;
import com.example.house_moving_service.modules.booking.repository.BookingRepository;
import com.example.house_moving_service.modules.booking.repository.BookingRouteRepository;
import com.example.house_moving_service.modules.booking.repository.BookingServiceRepository;
import com.example.house_moving_service.modules.booking.repository.BookingStatusHistoryRepository;
import com.example.house_moving_service.modules.booking.repository.BookingVoucherRepository;
import com.example.house_moving_service.modules.identity.repository.CustomerRepository;
import com.example.house_moving_service.modules.catalog.repository.ServiceRepository;
import com.example.house_moving_service.modules.catalog.service.impl.ServiceLookupServiceImpl;
import com.example.house_moving_service.modules.identity.repository.UserRepository;
import com.example.house_moving_service.modules.voucher.repository.VoucherRepository;
import com.example.house_moving_service.modules.voucher.service.impl.VoucherServiceImpl;
import com.example.house_moving_service.modules.booking.service.impl.BookingServiceImpl;
import com.example.house_moving_service.modules.booking.util.BookingCodeGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import com.example.house_moving_service.modules.booking.event.BookingCancelledEvent;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookingServiceTest {

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
    private CustomerRepository customerRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private VoucherRepository voucherRepository;
    @Mock
    private ServiceRepository serviceRepository;
    @Mock
    private PricingService pricingService;
    @Mock
    private BookingCodeGenerator bookingCodeGenerator;
    @Mock
    private BookingMapper bookingMapper;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private BookingServiceImpl bookingService;

    private Long userId;
    private User user;
    private Customer customer;
    private CreateBookingRequest request;

    @BeforeEach
    void setUp() {
        // VoucherServiceImpl thật bọc VoucherRepository mock: các stub/verify trên voucherRepository vẫn có hiệu lực
        ReflectionTestUtils.setField(bookingService, "voucherService", new VoucherServiceImpl(voucherRepository));
        ReflectionTestUtils.setField(bookingService, "serviceLookupService",
                new ServiceLookupServiceImpl(serviceRepository));

        userId = 1L;
        user = User.builder().id(userId).email("customer@example.com").isActive(true).build();
        customer = Customer.builder().id(userId).user(user).fullName("Nguyen Van A").phoneNumber("0912345678").build();

        ScheduleDto schedule = ScheduleDto.builder()
                .desiredStartAt(LocalDateTime.now().plusDays(2))
                .desiredEndAt(LocalDateTime.now().plusDays(2).plusHours(4))
                .build();

        RouteDto route = RouteDto.builder()
                .pickup(LocationDto.builder()
                        .address("123 Nguyen Trai, Q1, TP.HCM")
                        .lat(BigDecimal.valueOf(10.768))
                        .lng(BigDecimal.valueOf(106.690))
                        .propertyType(PropertyType.HOUSE)
                        .floorNumber(1)
                        .hasElevator(false)
                        .walkingDistanceMeters(10)
                        .build())
                .dropoff(LocationDto.builder()
                        .address("456 Le Loi, Q1, TP.HCM")
                        .lat(BigDecimal.valueOf(10.772))
                        .lng(BigDecimal.valueOf(106.698))
                        .propertyType(PropertyType.APARTMENT)
                        .floorNumber(5)
                        .hasElevator(true)
                        .walkingDistanceMeters(0)
                        .build())
                .build();

        request = CreateBookingRequest.builder()
                .contactName("Nguyen Van A")
                .contactPhone("0912345678")
                .note("Don nha can than")
                .schedule(schedule)
                .route(route)
                .serviceItems(List.of(ServiceItemDto.builder().serviceId(101).quantity(1).build()))
                .items(List.of(BookingItemRequest.builder().itemName("Tu lanh").quantity(1).build()))
                .build();
    }

    @Test
    @DisplayName("Should create booking successfully with all 6 tables saved when voucher is valid")
    void testCreateBooking_Success_WithVoucher() {
        request.setVoucherCode("SALE50");

        Voucher voucher = Voucher.builder()
                .id(10)
                .code("SALE50")
                .isActive(true)
                .startDate(LocalDateTime.now().minusDays(1))
                .endDate(LocalDateTime.now().plusDays(5))
                .totalUsageLimit(100)
                .usagePerUserLimit(1)
                .usedCount(5)
                .build();

        PricingCalculationResponse pricingResponse = PricingCalculationResponse.builder()
                .calculatedDistanceKm(BigDecimal.valueOf(12.5))
                .financialSummary(FinancialSummaryDto.builder()
                        .subtotal(BigDecimal.valueOf(1000000))
                        .discountAmount(BigDecimal.valueOf(50000))
                        .finalTotal(BigDecimal.valueOf(950000))
                        .build())
                .breakdownItems(List.of(BreakdownItemDto.builder()
                        .serviceId(101)
                        .serviceName("Xe tai 1.5 tan")
                        .quantity(1)
                        .unitPrice(BigDecimal.valueOf(1000000))
                        .totalPrice(BigDecimal.valueOf(1000000))
                        .note("Xe van chuyen")
                        .build()))
                .appliedVoucher(AppliedVoucherDto.builder()
                        .voucherId(10)
                        .code("SALE50")
                        .discountAmount(BigDecimal.valueOf(50000))
                        .build())
                .build();

        Booking savedBooking = Booking.builder()
                .id(1)
                .bookingCode("BKG261018ABCD")
                .customer(customer)
                .createdBy(user)
                .status(BookingStatus.PENDING)
                .totalAmount(BigDecimal.valueOf(950000))
                .discountAmount(BigDecimal.valueOf(50000))
                .build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(customerRepository.findById(userId)).thenReturn(Optional.of(customer));
        when(voucherRepository.findByCodeWithLock("SALE50")).thenReturn(Optional.of(voucher));
        when(bookingVoucherRepository.countByVoucherId(10)).thenReturn(5L);
        when(bookingVoucherRepository.countByVoucherIdAndBooking_CustomerId(10, userId)).thenReturn(0L);

        when(pricingService.calculatePrice(any(), eq(userId))).thenReturn(pricingResponse);
        when(bookingCodeGenerator.generateUniqueBookingCode()).thenReturn("BKG261018ABCD");
        when(bookingRepository.save(any(Booking.class))).thenReturn(savedBooking);

        BookingRoute savedRoute = BookingRoute.builder().id(1).booking(savedBooking).build();
        when(bookingRouteRepository.save(any(BookingRoute.class))).thenReturn(savedRoute);

        Service serviceProxy = Service.builder().id(101).name("Xe tai 1.5 tan").build();
        when(serviceRepository.getReferenceById(101)).thenReturn(serviceProxy);
        when(voucherRepository.getReferenceById(10)).thenReturn(voucher);

        when(bookingStatusHistoryRepository.save(any(BookingStatusHistory.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        when(bookingMapper.toDetailResponse(any(), any(), any(), any(), any(), any()))
                .thenReturn(BookingDetailResponse.builder()
                        .id(1)
                        .bookingCode("BKG261018ABCD")
                        .totalAmount(BigDecimal.valueOf(950000))
                        .discountAmount(BigDecimal.valueOf(50000))
                        .build());

        BookingDetailResponse response = bookingService.createBooking(request, userId);

        assertThat(response).isNotNull();
        assertThat(response.getBookingCode()).isEqualTo("BKG261018ABCD");
        assertThat(response.getTotalAmount()).isEqualByComparingTo("950000");

        // Verify that all 6 tables received their saves
        verify(bookingRepository).save(any(Booking.class));
        verify(bookingRouteRepository).save(any(BookingRoute.class));
        verify(bookingItemRepository).saveAll(anyList());
        verify(bookingServiceRepository).saveAll(anyList());
        verify(bookingVoucherRepository).save(any(BookingVoucher.class));
        verify(bookingStatusHistoryRepository).save(any(BookingStatusHistory.class));

        // Chuỗi điều kiện ra vào giữ đúng định dạng cũ sau khi chuyển sang RouteAccessNoteCodec
        ArgumentCaptor<BookingRoute> routeCaptor = ArgumentCaptor.forClass(BookingRoute.class);
        verify(bookingRouteRepository).save(routeCaptor.capture());
        assertThat(routeCaptor.getValue().getPickupNote()).isEqualTo("Tầng: 1, Thang máy: Không, Cự ly hẻm: 10m");
        assertThat(routeCaptor.getValue().getDropoffNote()).isEqualTo("Tầng: 5, Thang máy: Có, Cự ly hẻm: 0m");
    }

    @Test
    @DisplayName("Should throw ERR_CUSTOMER_NOT_FOUND when user has no customer profile")
    void testCreateBooking_CustomerNotFound() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(customerRepository.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> bookingService.createBooking(request, userId))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ERR_CUSTOMER_NOT_FOUND);
    }

    @Test
    @DisplayName("Should throw exception when saving history fails (verifying rollback triggering)")
    void testCreateBooking_WhenSavingHistoryFails_ThenRollbackAll() {
        request.setVoucherCode(null);

        PricingCalculationResponse pricingResponse = PricingCalculationResponse.builder()
                .calculatedDistanceKm(BigDecimal.valueOf(10.0))
                .financialSummary(FinancialSummaryDto.builder()
                        .subtotal(BigDecimal.valueOf(500000))
                        .discountAmount(BigDecimal.ZERO)
                        .finalTotal(BigDecimal.valueOf(500000))
                        .build())
                .breakdownItems(Collections.emptyList())
                .build();

        Booking savedBooking = Booking.builder()
                .id(1)
                .bookingCode("BKG261018ABCD")
                .customer(customer)
                .createdBy(user)
                .status(BookingStatus.PENDING)
                .totalAmount(BigDecimal.valueOf(500000))
                .discountAmount(BigDecimal.ZERO)
                .build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(customerRepository.findById(userId)).thenReturn(Optional.of(customer));
        when(pricingService.calculatePrice(any(), eq(userId))).thenReturn(pricingResponse);
        when(bookingCodeGenerator.generateUniqueBookingCode()).thenReturn("BKG261018ABCD");
        when(bookingRepository.save(any(Booking.class))).thenReturn(savedBooking);
        when(bookingRouteRepository.save(any(BookingRoute.class))).thenReturn(BookingRoute.builder().id(1).build());

        // Mock historyRepository.save() ném Exception
        when(bookingStatusHistoryRepository.save(any(BookingStatusHistory.class)))
                .thenThrow(new RuntimeException("Database error on history insert"));

        assertThatThrownBy(() -> bookingService.createBooking(request, userId))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Database error on history insert");
    }

    @Test
    @DisplayName("Should throw ERR_VOUCHER_LIMIT_REACHED when total usage limit is exceeded")
    void testCreateBooking_VoucherTotalLimitReached() {
        request.setVoucherCode("LIMITED");

        Voucher voucher = Voucher.builder()
                .id(20)
                .code("LIMITED")
                .isActive(true)
                .startDate(LocalDateTime.now().minusDays(1))
                .endDate(LocalDateTime.now().plusDays(5))
                .totalUsageLimit(10)
                .build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(customerRepository.findById(userId)).thenReturn(Optional.of(customer));
        when(voucherRepository.findByCodeWithLock("LIMITED")).thenReturn(Optional.of(voucher));
        when(bookingVoucherRepository.countByVoucherId(20)).thenReturn(10L); // Limit reached

        assertThatThrownBy(() -> bookingService.createBooking(request, userId))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ERR_VOUCHER_LIMIT_REACHED);
    }

    @Test
    @DisplayName("Should throw ERR_VOUCHER_LIMIT_REACHED when user usage limit is exceeded")
    void testCreateBooking_VoucherUserLimitReached() {
        request.setVoucherCode("LIMITED_USER");

        Voucher voucher = Voucher.builder()
                .id(21)
                .code("LIMITED_USER")
                .isActive(true)
                .startDate(LocalDateTime.now().minusDays(1))
                .endDate(LocalDateTime.now().plusDays(5))
                .usagePerUserLimit(1)
                .build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(customerRepository.findById(userId)).thenReturn(Optional.of(customer));
        when(voucherRepository.findByCodeWithLock("LIMITED_USER")).thenReturn(Optional.of(voucher));
        when(bookingVoucherRepository.countByVoucherIdAndBooking_CustomerId(21, userId)).thenReturn(1L); // Limit reached

        assertThatThrownBy(() -> bookingService.createBooking(request, userId))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ERR_VOUCHER_LIMIT_REACHED);
    }

    // ==================== Tests for getBookingById ====================

    @Test
    @DisplayName("getBookingById: Should return detail response when valid user and booking exists")
    void testGetBookingById_Success() {
        Booking booking = Booking.builder()
                .id(100)
                .bookingCode("BKG202610190001")
                .customer(customer)
                .status(BookingStatus.PENDING)
                .totalAmount(BigDecimal.valueOf(1000000))
                .build();

        BookingRoute route = BookingRoute.builder().id(1).booking(booking).pickupAddress("123 A").dropoffAddress("456 B").build();
        BookingItem item = BookingItem.builder().id(1).booking(booking).itemName("Ban lam viec").quantity(1).build();
        com.example.house_moving_service.modules.booking.entity.BookingService service = com.example.house_moving_service.modules.booking.entity.BookingService.builder().id(1).booking(booking).build();
        BookingVoucher voucher = BookingVoucher.builder().id(1).booking(booking).build();
        BookingStatusHistory history = BookingStatusHistory.builder().id(1).booking(booking).newStatus(BookingStatus.PENDING).build();

        BookingDetailResponse expectedResponse = BookingDetailResponse.builder()
                .id(100)
                .bookingCode("BKG202610190001")
                .build();

        when(bookingRepository.findById(100)).thenReturn(Optional.of(booking));
        when(bookingRouteRepository.findByBookingId(100)).thenReturn(Optional.of(route));
        when(bookingItemRepository.findAllByBookingId(100)).thenReturn(List.of(item));
        when(bookingServiceRepository.findAllByBookingId(100)).thenReturn(List.of(service));
        when(bookingVoucherRepository.findAllByBookingId(100)).thenReturn(List.of(voucher));
        when(bookingStatusHistoryRepository.findAllByBookingIdOrderByChangedAtAsc(100)).thenReturn(List.of(history));
        when(bookingMapper.toDetailResponse(booking, route, List.of(item), List.of(service), List.of(voucher), List.of(history)))
                .thenReturn(expectedResponse);

        BookingDetailResponse response = bookingService.getBookingById(100, userId);

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(100);
        assertThat(response.getBookingCode()).isEqualTo("BKG202610190001");
    }

    @Test
    @DisplayName("getBookingById: Should throw ERR_BOOKING_NOT_FOUND when booking does not exist")
    void testGetBookingById_NotFound() {
        when(bookingRepository.findById(999)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> bookingService.getBookingById(999, userId))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ERR_BOOKING_NOT_FOUND);
    }

    @Test
    @DisplayName("getBookingById: Should throw ERR_BOOKING_FORBIDDEN on IDOR attack")
    void testGetBookingById_IdorAttack_ThrowsForbidden() {
        Customer otherCustomer = Customer.builder().id(99L).build();
        Booking otherBooking = Booking.builder()
                .id(100)
                .customer(otherCustomer)
                .build();

        when(bookingRepository.findById(100)).thenReturn(Optional.of(otherBooking));

        assertThatThrownBy(() -> bookingService.getBookingById(100, userId))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ERR_BOOKING_FORBIDDEN);
    }

    // ==================== Tests for getCustomerBookings ====================

    @Test
    @DisplayName("getCustomerBookings: Should return paged list when no status filter provided")
    void testGetCustomerBookings_WithoutStatus_Success() {
        Pageable pageable = PageRequest.of(0, 10);
        Booking booking = Booking.builder().id(100).bookingCode("BKG001").customer(customer).build();
        Page<Booking> page = new PageImpl<>(List.of(booking), pageable, 1);

        BookingSummaryResponse summary = BookingSummaryResponse.builder().id(100).bookingCode("BKG001").build();

        when(bookingRepository.findAllByCustomerId(userId, pageable)).thenReturn(page);
        when(bookingMapper.toSummaryResponse(booking)).thenReturn(summary);

        PageResponse<BookingSummaryResponse> response = bookingService.getCustomerBookings(userId, pageable);

        assertThat(response).isNotNull();
        assertThat(response.getPageNo()).isEqualTo(1);
        assertThat(response.getTotalElements()).isEqualTo(1);
        assertThat(response.getItems()).hasSize(1);
        assertThat(response.getItems().get(0).getBookingCode()).isEqualTo("BKG001");
    }

    @Test
    @DisplayName("getCustomerBookings: Should filter by status when status is provided")
    void testGetCustomerBookings_WithStatus_Success() {
        Pageable pageable = PageRequest.of(0, 10);
        Booking booking = Booking.builder().id(100).status(BookingStatus.CONFIRMED).customer(customer).build();
        Page<Booking> page = new PageImpl<>(List.of(booking), pageable, 1);

        BookingSummaryResponse summary = BookingSummaryResponse.builder().id(100).status(BookingStatus.CONFIRMED).build();

        when(bookingRepository.findAllByCustomerIdAndStatus(userId, BookingStatus.CONFIRMED, pageable)).thenReturn(page);
        when(bookingMapper.toSummaryResponse(booking)).thenReturn(summary);

        PageResponse<BookingSummaryResponse> response = bookingService.getCustomerBookings(userId, BookingStatus.CONFIRMED, pageable);

        assertThat(response).isNotNull();
        assertThat(response.getItems()).hasSize(1);
        assertThat(response.getItems().get(0).getStatus()).isEqualTo(BookingStatus.CONFIRMED);
    }

    // ==================== Tests for cancelBooking ====================

    @Test
    @DisplayName("cancelBooking: Should cancel booking and record status history when in PENDING")
    void testCancelBooking_Success() {
        Booking booking = Booking.builder()
                .id(100)
                .bookingCode("BKG001")
                .status(BookingStatus.PENDING)
                .customer(customer)
                .build();

        Voucher voucher = Voucher.builder().id(10).code("VOUCHER10").usedCount(3).build();
        BookingVoucher bv = BookingVoucher.builder().id(1).booking(booking).voucher(voucher).build();

        CancelBookingRequest cancelReq = CancelBookingRequest.builder().reason("Thay doi ke hoach").build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
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

        BookingDetailResponse response = bookingService.cancelBooking(100, cancelReq, userId);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(BookingStatus.CANCELLED);

        // Verify booking status changed and saved
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CANCELLED);
        verify(bookingRepository).save(booking);

        // Verify history saved
        verify(bookingStatusHistoryRepository).save(any(BookingStatusHistory.class));

        // Verify voucher usedCount decremented
        assertThat(voucher.getUsedCount()).isEqualTo(2);
        verify(voucherRepository).save(voucher);

        // Verify event báo hủy đơn cho module khác
        verify(eventPublisher).publishEvent(new BookingCancelledEvent(100, userId, "Thay doi ke hoach"));
    }

    @Test
    @DisplayName("cancelBooking: Should throw ERR_BOOKING_NOT_FOUND when booking does not exist")
    void testCancelBooking_NotFound() {
        CancelBookingRequest cancelReq = CancelBookingRequest.builder().reason("Huy don").build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(bookingRepository.findById(999)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> bookingService.cancelBooking(999, cancelReq, userId))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ERR_BOOKING_NOT_FOUND);
    }

    @Test
    @DisplayName("cancelBooking: Should throw ERR_BOOKING_FORBIDDEN on IDOR attack")
    void testCancelBooking_IdorAttack_ThrowsForbidden() {
        Customer otherCustomer = Customer.builder().id(99L).build();
        Booking otherBooking = Booking.builder().id(100).customer(otherCustomer).status(BookingStatus.PENDING).build();
        CancelBookingRequest cancelReq = CancelBookingRequest.builder().reason("Huy don").build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(bookingRepository.findById(100)).thenReturn(Optional.of(otherBooking));

        assertThatThrownBy(() -> bookingService.cancelBooking(100, cancelReq, userId))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ERR_BOOKING_FORBIDDEN);
    }

    @Test
    @DisplayName("cancelBooking: Should throw ERR_BOOKING_INVALID_STATUS when booking is CONFIRMED")
    void testCancelBooking_WhenStatusIsConfirmed_ThrowsInvalidStatus() {
        Booking booking = Booking.builder().id(100).customer(customer).status(BookingStatus.CONFIRMED).build();
        CancelBookingRequest cancelReq = CancelBookingRequest.builder().reason("Huy don").build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(bookingRepository.findById(100)).thenReturn(Optional.of(booking));

        assertThatThrownBy(() -> bookingService.cancelBooking(100, cancelReq, userId))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ERR_BOOKING_INVALID_STATUS);
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("cancelBooking: Should throw ERR_BOOKING_INVALID_STATUS when booking is already CANCELLED")
    void testCancelBooking_WhenAlreadyCancelled_ThrowsInvalidStatus() {
        Booking booking = Booking.builder().id(100).customer(customer).status(BookingStatus.CANCELLED).build();
        CancelBookingRequest cancelReq = CancelBookingRequest.builder().reason("Huy don").build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(bookingRepository.findById(100)).thenReturn(Optional.of(booking));

        assertThatThrownBy(() -> bookingService.cancelBooking(100, cancelReq, userId))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ERR_BOOKING_INVALID_STATUS);
    }
}
