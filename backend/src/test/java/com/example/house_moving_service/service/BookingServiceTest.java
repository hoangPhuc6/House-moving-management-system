package com.example.house_moving_service.service;

import com.example.house_moving_service.dto.request.booking.BookingItemRequest;
import com.example.house_moving_service.dto.request.booking.CreateBookingRequest;
import com.example.house_moving_service.dto.request.pricing.LocationDto;
import com.example.house_moving_service.dto.request.pricing.RouteDto;
import com.example.house_moving_service.dto.request.pricing.ScheduleDto;
import com.example.house_moving_service.dto.request.pricing.ServiceItemDto;
import com.example.house_moving_service.dto.response.booking.BookingDetailResponse;
import com.example.house_moving_service.dto.response.pricing.AppliedVoucherDto;
import com.example.house_moving_service.dto.response.pricing.BreakdownItemDto;
import com.example.house_moving_service.dto.response.pricing.FinancialSummaryDto;
import com.example.house_moving_service.dto.response.pricing.PricingCalculationResponse;
import com.example.house_moving_service.entity.Booking;
import com.example.house_moving_service.entity.BookingItem;
import com.example.house_moving_service.entity.BookingRoute;
import com.example.house_moving_service.entity.BookingStatusHistory;
import com.example.house_moving_service.entity.BookingVoucher;
import com.example.house_moving_service.entity.Customer;
import com.example.house_moving_service.entity.Service;
import com.example.house_moving_service.entity.User;
import com.example.house_moving_service.entity.Voucher;
import com.example.house_moving_service.enums.BookingStatus;
import com.example.house_moving_service.enums.PropertyType;
import com.example.house_moving_service.exception.AppException;
import com.example.house_moving_service.exception.ErrorCode;
import com.example.house_moving_service.mapper.BookingMapper;
import com.example.house_moving_service.repository.BookingItemRepository;
import com.example.house_moving_service.repository.BookingRepository;
import com.example.house_moving_service.repository.BookingRouteRepository;
import com.example.house_moving_service.repository.BookingServiceRepository;
import com.example.house_moving_service.repository.BookingStatusHistoryRepository;
import com.example.house_moving_service.repository.BookingVoucherRepository;
import com.example.house_moving_service.repository.CustomerRepository;
import com.example.house_moving_service.repository.ServiceRepository;
import com.example.house_moving_service.repository.UserRepository;
import com.example.house_moving_service.repository.VoucherRepository;
import com.example.house_moving_service.service.impl.BookingServiceImpl;
import com.example.house_moving_service.util.BookingCodeGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
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

    @InjectMocks
    private BookingServiceImpl bookingService;

    private Long userId;
    private User user;
    private Customer customer;
    private CreateBookingRequest request;

    @BeforeEach
    void setUp() {
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
                .serviceItems(List.of(ServiceItemDto.builder().serviceId(101).quantity(BigDecimal.ONE).build()))
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
                        .quantity(BigDecimal.ONE)
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
                .status(BookingStatus.PENDING_SURVEY)
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
}
