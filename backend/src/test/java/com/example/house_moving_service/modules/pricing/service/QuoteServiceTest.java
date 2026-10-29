package com.example.house_moving_service.modules.pricing.service;

import com.example.house_moving_service.modules.booking.dto.request.BookingItemRequest;
import com.example.house_moving_service.modules.pricing.config.PricingProperties;
import com.example.house_moving_service.modules.pricing.dto.request.CreateQuoteRequest;
import com.example.house_moving_service.modules.pricing.dto.request.PricingCalculationRequest;
import com.example.house_moving_service.modules.pricing.dto.request.RejectQuotationRequest;
import com.example.house_moving_service.modules.pricing.dto.request.ServiceItemDto;
import com.example.house_moving_service.modules.pricing.dto.response.AppliedVoucherDto;
import com.example.house_moving_service.modules.pricing.dto.response.BreakdownItemDto;
import com.example.house_moving_service.modules.pricing.dto.response.FinancialSummaryDto;
import com.example.house_moving_service.modules.pricing.dto.response.PricingCalculationResponse;
import com.example.house_moving_service.modules.pricing.dto.response.QuotationResponse;
import com.example.house_moving_service.modules.pricing.entity.Quotation;
import com.example.house_moving_service.modules.pricing.enums.QuotationStatus;
import com.example.house_moving_service.modules.pricing.mapper.QuotationMapper;
import com.example.house_moving_service.modules.pricing.repository.QuotationRepository;
import com.example.house_moving_service.modules.catalog.service.ServiceLookupService;
import com.example.house_moving_service.modules.pricing.service.impl.QuoteServiceImpl;
import com.example.house_moving_service.modules.booking.entity.Booking;
import com.example.house_moving_service.modules.booking.entity.BookingItem;
import com.example.house_moving_service.modules.booking.entity.BookingRoute;
import com.example.house_moving_service.modules.booking.entity.BookingStatusHistory;
import com.example.house_moving_service.modules.booking.entity.BookingVoucher;
import com.example.house_moving_service.modules.booking.enums.BookingStatus;
import com.example.house_moving_service.modules.booking.repository.BookingItemRepository;
import com.example.house_moving_service.modules.booking.repository.BookingRepository;
import com.example.house_moving_service.modules.booking.repository.BookingRouteRepository;
import com.example.house_moving_service.modules.booking.repository.BookingServiceRepository;
import com.example.house_moving_service.modules.booking.repository.BookingStatusHistoryRepository;
import com.example.house_moving_service.modules.booking.repository.BookingVoucherRepository;
import com.example.house_moving_service.modules.identity.entity.Customer;
import com.example.house_moving_service.modules.identity.entity.User;
import com.example.house_moving_service.modules.identity.repository.UserRepository;
import com.example.house_moving_service.modules.voucher.entity.Voucher;
import com.example.house_moving_service.modules.voucher.repository.VoucherRepository;
import com.example.house_moving_service.modules.voucher.service.impl.VoucherServiceImpl;
import com.example.house_moving_service.common.exception.AppException;
import com.example.house_moving_service.common.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QuoteServiceTest {

    @Mock
    private BookingRepository bookingRepository;
    @Mock
    private BookingRouteRepository bookingRouteRepository;
    @Mock
    private BookingServiceRepository bookingServiceRepository;
    @Mock
    private BookingItemRepository bookingItemRepository;
    @Mock
    private BookingVoucherRepository bookingVoucherRepository;
    @Mock
    private BookingStatusHistoryRepository bookingStatusHistoryRepository;
    @Mock
    private QuotationRepository quotationRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private VoucherRepository voucherRepository;
    @Mock
    private PricingService pricingService;
    @Mock
    private ServiceLookupService serviceLookupService;
    @InjectMocks
    private QuoteServiceImpl quoteService;

    private Long adminUserId;
    private User adminUser;
    private Long customerUserId;
    private User customerUser;
    private Booking booking;

    @BeforeEach
    void setUp() {
        // VoucherServiceImpl thật bọc VoucherRepository mock: các stub/verify trên voucherRepository vẫn có hiệu lực
        ReflectionTestUtils.setField(quoteService, "voucherService", new VoucherServiceImpl(voucherRepository));
        ReflectionTestUtils.setField(quoteService, "pricingProperties", new PricingProperties());
        ReflectionTestUtils.setField(quoteService, "quotationMapper", new QuotationMapper());

        adminUserId = 99L;
        adminUser = User.builder().id(adminUserId).email("admin@housemoving.vn").isActive(true).build();
        customerUserId = 1L;
        customerUser = User.builder().id(customerUserId).email("customer@housemoving.vn").isActive(true).build();
        Customer customer = Customer.builder().id(customerUserId).fullName("Le Van B").phoneNumber("0988776655").build();

        booking = Booking.builder()
                .id(100)
                .bookingCode("BKG20261019001")
                .customer(customer)
                .status(BookingStatus.PENDING)
                .totalAmount(BigDecimal.valueOf(1500000))
                .discountAmount(BigDecimal.valueOf(50000))
                .build();
    }

    private BookingRoute quoteRoute() {
        return BookingRoute.builder().id(1).booking(booking)
                .pickupAddress("Point A").pickupLat(BigDecimal.valueOf(10.77)).pickupLng(BigDecimal.valueOf(106.70))
                .dropoffAddress("Point B").dropoffLat(BigDecimal.valueOf(10.80)).dropoffLng(BigDecimal.valueOf(106.65))
                .estimatedDistanceKm(BigDecimal.valueOf(10))
                .build();
    }

    private CreateQuoteRequest quoteRequest() {
        return CreateQuoteRequest.builder()
                .serviceItems(List.of(ServiceItemDto.builder().serviceId(1).quantity(2).build()))
                .actualDistanceKm(BigDecimal.valueOf(12.5))
                .note("Khảo sát thực tế phát sinh thêm tủ lạnh")
                .build();
    }

    private PricingCalculationResponse pricingResponse(BigDecimal subtotal, BigDecimal discount, AppliedVoucherDto appliedVoucher) {
        return PricingCalculationResponse.builder()
                .calculatedDistanceKm(BigDecimal.valueOf(12.5))
                .financialSummary(FinancialSummaryDto.builder()
                        .subtotal(subtotal)
                        .discountAmount(discount)
                        .finalTotal(subtotal.subtract(discount))
                        .build())
                .breakdownItems(List.of(BreakdownItemDto.builder()
                        .serviceId(1)
                        .serviceName("Xe tải 1.5 tấn")
                        .quantity(2)
                        .unitPrice(subtotal.divide(BigDecimal.valueOf(2)))
                        .totalPrice(subtotal)
                        .build()))
                .appliedVoucher(appliedVoucher)
                .build();
    }

    private Voucher.VoucherBuilder activeVoucher() {
        return Voucher.builder()
                .isActive(true)
                .startDate(LocalDateTime.now().minusDays(1))
                .endDate(LocalDateTime.now().plusDays(30));
    }

    private void stubQuoteContext(BookingRoute route, List<BookingVoucher> vouchers) {
        when(userRepository.findById(adminUserId)).thenReturn(Optional.of(adminUser));
        when(bookingRepository.findById(100)).thenReturn(Optional.of(booking));
        when(bookingRouteRepository.findByBookingId(100)).thenReturn(Optional.of(route));
        when(bookingVoucherRepository.findAllByBookingId(100)).thenReturn(vouchers);
    }

    private void stubQuotePersistence() {
        stubQuotePersistence(Collections.emptyList(), Optional.empty());
    }

    private void stubQuotePersistence(List<Quotation> sentQuotations, Optional<Quotation> latestQuotation) {
        when(bookingServiceRepository.findAllByBookingId(100)).thenReturn(Collections.emptyList());
        when(bookingServiceRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));
        when(quotationRepository.findAllByBookingIdAndStatus(100, QuotationStatus.SENT)).thenReturn(sentQuotations);
        when(quotationRepository.findTopByBookingIdOrderByVersionDesc(100)).thenReturn(latestQuotation);
        when(quotationRepository.save(any(Quotation.class))).thenAnswer(inv -> inv.getArgument(0));
        when(bookingRepository.save(booking)).thenReturn(booking);
    }

    private Quotation captureSavedQuotation() {
        ArgumentCaptor<Quotation> captor = ArgumentCaptor.forClass(Quotation.class);
        verify(quotationRepository).save(captor.capture());
        return captor.getValue();
    }

    private Quotation sentQuotation(int id, int version) {
        return Quotation.builder()
                .id(id)
                .booking(booking)
                .version(version)
                .amount(BigDecimal.valueOf(2000000))
                .discountAmount(BigDecimal.valueOf(100000))
                .finalAmount(BigDecimal.valueOf(1900000))
                .status(QuotationStatus.SENT)
                .validUntil(LocalDateTime.now().plusDays(3))
                .build();
    }

    // ===================== createQuote =====================

    @Test
    @DisplayName("Management quote: Success should create SENT quotation v1, keep voucher, move booking to QUOTED without touching total")
    void testCreateQuote_SuccessKeepsVoucher() {
        BookingRoute route = quoteRoute();
        Voucher voucher = Voucher.builder().id(5).code("SALE10").build();
        BookingVoucher bookingVoucher = BookingVoucher.builder().id(1).booking(booking).voucher(voucher)
                .discountAmount(BigDecimal.valueOf(50000)).build();

        stubQuoteContext(route, List.of(bookingVoucher));
        when(pricingService.calculatePrice(any(PricingCalculationRequest.class), eq(adminUserId)))
                .thenReturn(pricingResponse(BigDecimal.valueOf(2000000), BigDecimal.valueOf(80000),
                        AppliedVoucherDto.builder().voucherId(5).code("SALE10").discountAmount(BigDecimal.valueOf(80000)).build()));
        stubQuotePersistence();

        QuotationResponse response = quoteService.createQuote(100, quoteRequest(), adminUserId);

        ArgumentCaptor<PricingCalculationRequest> captor = ArgumentCaptor.forClass(PricingCalculationRequest.class);
        verify(pricingService).calculatePrice(captor.capture(), eq(adminUserId));
        assertThat(captor.getValue().getVoucherCode()).isEqualTo("SALE10");
        assertThat(captor.getValue().getRoute().getManualDistanceKm()).isEqualByComparingTo("12.5");

        Quotation quotation = captureSavedQuotation();
        assertThat(quotation.getVersion()).isEqualTo(1);
        assertThat(quotation.getStatus()).isEqualTo(QuotationStatus.SENT);
        assertThat(quotation.getAmount()).isEqualByComparingTo("2000000");
        assertThat(quotation.getDiscountAmount()).isEqualByComparingTo("80000");
        assertThat(quotation.getFinalAmount()).isEqualByComparingTo("1920000");
        assertThat(quotation.getValidUntil()).isAfter(LocalDateTime.now().plusDays(6));
        assertThat(quotation.getCreatedBy()).isSameAs(adminUser);
        assertThat(quotation.getItems()).hasSize(1);
        assertThat(quotation.getItems().get(0).getItemName()).isEqualTo("Xe tải 1.5 tấn");
        assertThat(quotation.getItems().get(0).getQuantity()).isEqualTo(2);
        assertThat(quotation.getItems().get(0).getLineAmount()).isEqualByComparingTo("2000000");
        assertThat(response.getFinalAmount()).isEqualByComparingTo("1920000");

        assertThat(booking.getStatus()).isEqualTo(BookingStatus.QUOTED);
        // Tổng tiền đơn chưa đổi cho tới khi khách chấp nhận báo giá
        assertThat(booking.getTotalAmount()).isEqualByComparingTo("1500000");
        assertThat(booking.getDiscountAmount()).isEqualByComparingTo("50000");
        assertThat(bookingVoucher.getDiscountAmount()).isEqualByComparingTo("80000");
        assertThat(route.getEstimatedDistanceKm()).isEqualByComparingTo("12.5");

        verify(bookingServiceRepository).saveAll(anyList());
        verify(bookingVoucherRepository).save(bookingVoucher);
        verify(bookingItemRepository, never()).findAllByBookingId(any());
        verify(bookingStatusHistoryRepository).save(any(BookingStatusHistory.class));
    }

    @Test
    @DisplayName("Management quote: Re-quote should expire previous SENT quotation and bump version")
    void testCreateQuote_RequoteExpiresPrevious() {
        booking.setStatus(BookingStatus.QUOTED);
        Quotation previous = sentQuotation(7, 2);

        stubQuoteContext(quoteRoute(), Collections.emptyList());
        when(pricingService.calculatePrice(any(PricingCalculationRequest.class), eq(adminUserId)))
                .thenReturn(pricingResponse(BigDecimal.valueOf(2000000), BigDecimal.ZERO, null));
        stubQuotePersistence(List.of(previous), Optional.of(previous));

        quoteService.createQuote(100, quoteRequest(), adminUserId);

        assertThat(previous.getStatus()).isEqualTo(QuotationStatus.EXPIRED);
        verify(quotationRepository).saveAll(List.of(previous));
        Quotation quotation = captureSavedQuotation();
        assertThat(quotation.getVersion()).isEqualTo(3);
        assertThat(quotation.getStatus()).isEqualTo(QuotationStatus.SENT);
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.QUOTED);
    }

    @Test
    @DisplayName("Management quote: Items should be synced by id - update existing, add new, delete missing")
    @SuppressWarnings("unchecked")
    void testCreateQuote_SyncsItemsById() {
        BookingItem sofa = BookingItem.builder().id(11).booking(booking).itemName("Sofa").quantity(1).build();
        BookingItem tv = BookingItem.builder().id(12).booking(booking).itemName("TV").quantity(1).build();
        CreateQuoteRequest request = quoteRequest();
        request.setItems(List.of(
                BookingItemRequest.builder().id(11).itemName("Sofa góc").quantity(2).isFragile(true).build(),
                BookingItemRequest.builder().itemName("Tủ lạnh").quantity(1).build()));

        stubQuoteContext(quoteRoute(), Collections.emptyList());
        when(pricingService.calculatePrice(any(PricingCalculationRequest.class), eq(adminUserId)))
                .thenReturn(pricingResponse(BigDecimal.valueOf(2000000), BigDecimal.ZERO, null));
        stubQuotePersistence();
        when(bookingItemRepository.findAllByBookingId(100)).thenReturn(new ArrayList<>(List.of(sofa, tv)));
        when(bookingItemRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        quoteService.createQuote(100, request, adminUserId);

        ArgumentCaptor<Collection<BookingItem>> deletedCaptor = ArgumentCaptor.forClass(Collection.class);
        verify(bookingItemRepository).deleteAll(deletedCaptor.capture());
        assertThat(deletedCaptor.getValue()).containsExactly(tv);

        ArgumentCaptor<List<BookingItem>> savedCaptor = ArgumentCaptor.forClass(List.class);
        verify(bookingItemRepository).saveAll(savedCaptor.capture());
        List<BookingItem> saved = savedCaptor.getValue();
        assertThat(saved).hasSize(2);
        assertThat(saved.get(0)).isSameAs(sofa);
        assertThat(sofa.getItemName()).isEqualTo("Sofa góc");
        assertThat(sofa.getQuantity()).isEqualTo(2);
        assertThat(sofa.getIsFragile()).isTrue();
        assertThat(saved.get(1).getId()).isNull();
        assertThat(saved.get(1).getItemName()).isEqualTo("Tủ lạnh");
        assertThat(saved.get(1).getBooking()).isSameAs(booking);
    }

    @Test
    @DisplayName("Management quote: Item id not belonging to booking should throw ERR_BOOKING_ITEM_NOT_FOUND")
    void testCreateQuote_UnknownItemId() {
        CreateQuoteRequest request = quoteRequest();
        request.setItems(List.of(BookingItemRequest.builder().id(999).itemName("Lạ").quantity(1).build()));

        stubQuoteContext(quoteRoute(), Collections.emptyList());
        when(pricingService.calculatePrice(any(PricingCalculationRequest.class), eq(adminUserId)))
                .thenReturn(pricingResponse(BigDecimal.valueOf(2000000), BigDecimal.ZERO, null));
        when(bookingServiceRepository.findAllByBookingId(100)).thenReturn(Collections.emptyList());
        when(bookingItemRepository.findAllByBookingId(100)).thenReturn(Collections.emptyList());

        assertThatThrownBy(() -> quoteService.createQuote(100, request, adminUserId))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ERR_BOOKING_ITEM_NOT_FOUND);
        verify(quotationRepository, never()).save(any(Quotation.class));
    }

    @Test
    @DisplayName("Management quote: Voucher no longer valid should be dropped and usage released")
    void testCreateQuote_DropsInvalidVoucher() {
        Voucher voucher = Voucher.builder().id(5).code("SALE10").usedCount(3).build();
        BookingVoucher bookingVoucher = BookingVoucher.builder().id(1).booking(booking).voucher(voucher)
                .discountAmount(BigDecimal.valueOf(50000)).build();

        stubQuoteContext(quoteRoute(), List.of(bookingVoucher));
        when(pricingService.calculatePrice(any(PricingCalculationRequest.class), eq(adminUserId)))
                .thenThrow(new AppException(ErrorCode.ERR_VOUCHER_EXPIRED, "SALE10"))
                .thenReturn(pricingResponse(BigDecimal.valueOf(2000000), BigDecimal.ZERO, null));
        when(voucherRepository.findById(5)).thenReturn(Optional.of(voucher));
        stubQuotePersistence();

        quoteService.createQuote(100, quoteRequest(), adminUserId);

        verify(pricingService, times(2)).calculatePrice(any(PricingCalculationRequest.class), eq(adminUserId));
        verify(bookingVoucherRepository).delete(bookingVoucher);
        verify(bookingVoucherRepository, never()).save(any(BookingVoucher.class));
        assertThat(voucher.getUsedCount()).isEqualTo(2);
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.QUOTED);

        Quotation quotation = captureSavedQuotation();
        assertThat(quotation.getDiscountAmount()).isEqualByComparingTo("0");
        assertThat(quotation.getFinalAmount()).isEqualByComparingTo("2000000");
    }

    @Test
    @DisplayName("Management quote: New voucherCode should replace current voucher and move usage")
    void testCreateQuote_SwapsVoucher() {
        Voucher oldVoucher = Voucher.builder().id(5).code("SALE10").usedCount(3).build();
        Voucher newVoucher = activeVoucher().id(6).code("SALE20").usedCount(1)
                .totalUsageLimit(10).usagePerUserLimit(1).build();
        BookingVoucher oldBookingVoucher = BookingVoucher.builder().id(1).booking(booking).voucher(oldVoucher)
                .discountAmount(BigDecimal.valueOf(50000)).build();
        CreateQuoteRequest request = quoteRequest();
        request.setVoucherCode(" sale20 ");

        stubQuoteContext(quoteRoute(), List.of(oldBookingVoucher));
        when(voucherRepository.findByCodeWithLock("SALE20")).thenReturn(Optional.of(newVoucher));
        when(bookingVoucherRepository.countByVoucherId(6)).thenReturn(1L);
        when(bookingVoucherRepository.countByVoucherIdAndBooking_CustomerId(6, 1L)).thenReturn(0L);
        when(voucherRepository.findById(5)).thenReturn(Optional.of(oldVoucher));
        when(bookingVoucherRepository.save(any(BookingVoucher.class))).thenAnswer(inv -> inv.getArgument(0));
        when(pricingService.calculatePrice(any(PricingCalculationRequest.class), eq(adminUserId)))
                .thenReturn(pricingResponse(BigDecimal.valueOf(2000000), BigDecimal.valueOf(200000),
                        AppliedVoucherDto.builder().voucherId(6).code("SALE20").discountAmount(BigDecimal.valueOf(200000)).build()));
        stubQuotePersistence();

        quoteService.createQuote(100, request, adminUserId);

        ArgumentCaptor<PricingCalculationRequest> pricingCaptor = ArgumentCaptor.forClass(PricingCalculationRequest.class);
        verify(pricingService).calculatePrice(pricingCaptor.capture(), eq(adminUserId));
        assertThat(pricingCaptor.getValue().getVoucherCode()).isEqualTo("SALE20");

        verify(bookingVoucherRepository).delete(oldBookingVoucher);
        assertThat(oldVoucher.getUsedCount()).isEqualTo(2);
        assertThat(newVoucher.getUsedCount()).isEqualTo(2);

        ArgumentCaptor<BookingVoucher> voucherCaptor = ArgumentCaptor.forClass(BookingVoucher.class);
        verify(bookingVoucherRepository).save(voucherCaptor.capture());
        assertThat(voucherCaptor.getValue().getVoucher()).isSameAs(newVoucher);
        assertThat(voucherCaptor.getValue().getDiscountAmount()).isEqualByComparingTo("200000");

        Quotation quotation = captureSavedQuotation();
        assertThat(quotation.getDiscountAmount()).isEqualByComparingTo("200000");
        assertThat(quotation.getFinalAmount()).isEqualByComparingTo("1800000");
    }

    @Test
    @DisplayName("Management quote: Empty voucherCode should remove current voucher")
    void testCreateQuote_RemovesVoucher() {
        Voucher voucher = Voucher.builder().id(5).code("SALE10").usedCount(3).build();
        BookingVoucher bookingVoucher = BookingVoucher.builder().id(1).booking(booking).voucher(voucher)
                .discountAmount(BigDecimal.valueOf(50000)).build();
        CreateQuoteRequest request = quoteRequest();
        request.setVoucherCode("");

        stubQuoteContext(quoteRoute(), List.of(bookingVoucher));
        when(voucherRepository.findById(5)).thenReturn(Optional.of(voucher));
        when(pricingService.calculatePrice(any(PricingCalculationRequest.class), eq(adminUserId)))
                .thenReturn(pricingResponse(BigDecimal.valueOf(2000000), BigDecimal.ZERO, null));
        stubQuotePersistence();

        quoteService.createQuote(100, request, adminUserId);

        ArgumentCaptor<PricingCalculationRequest> captor = ArgumentCaptor.forClass(PricingCalculationRequest.class);
        verify(pricingService).calculatePrice(captor.capture(), eq(adminUserId));
        assertThat(captor.getValue().getVoucherCode()).isNull();
        verify(bookingVoucherRepository).delete(bookingVoucher);
        verify(bookingVoucherRepository, never()).save(any(BookingVoucher.class));
        assertThat(voucher.getUsedCount()).isEqualTo(2);
        assertThat(captureSavedQuotation().getDiscountAmount()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("Management quote: New voucher that fails pricing rules should be rejected, keeping current voucher")
    void testCreateQuote_RejectsInvalidNewVoucher() {
        Voucher oldVoucher = Voucher.builder().id(5).code("SALE10").usedCount(3).build();
        Voucher newVoucher = activeVoucher().id(6).code("BIG500").usedCount(0).build();
        BookingVoucher oldBookingVoucher = BookingVoucher.builder().id(1).booking(booking).voucher(oldVoucher)
                .discountAmount(BigDecimal.valueOf(50000)).build();
        CreateQuoteRequest request = quoteRequest();
        request.setVoucherCode("BIG500");

        stubQuoteContext(quoteRoute(), List.of(oldBookingVoucher));
        when(voucherRepository.findByCodeWithLock("BIG500")).thenReturn(Optional.of(newVoucher));
        when(pricingService.calculatePrice(any(PricingCalculationRequest.class), eq(adminUserId)))
                .thenThrow(new AppException(ErrorCode.ERR_VOUCHER_MIN_VALUE, 5000000.0));

        assertThatThrownBy(() -> quoteService.createQuote(100, request, adminUserId))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ERR_VOUCHER_MIN_VALUE);
        verify(pricingService, times(1)).calculatePrice(any(PricingCalculationRequest.class), eq(adminUserId));
        verify(bookingVoucherRepository, never()).delete(any(BookingVoucher.class));
        verify(quotationRepository, never()).save(any(Quotation.class));
        assertThat(oldVoucher.getUsedCount()).isEqualTo(3);
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.PENDING);
    }

    @Test
    @DisplayName("Management quote: Should throw ERR_BOOKING_INVALID_STATUS when booking is past the quoting stage")
    void testCreateQuote_InvalidStatus() {
        booking.setStatus(BookingStatus.APPROVED);

        when(userRepository.findById(adminUserId)).thenReturn(Optional.of(adminUser));
        when(bookingRepository.findById(100)).thenReturn(Optional.of(booking));

        assertThatThrownBy(() -> quoteService.createQuote(100, quoteRequest(), adminUserId))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ERR_BOOKING_INVALID_STATUS);
        verify(pricingService, never()).calculatePrice(any(PricingCalculationRequest.class), any());
    }

    // ===================== accept / reject =====================

    @Test
    @DisplayName("Accept quotation: Should mark ACCEPTED, lock booking total and move booking to APPROVED")
    void testAcceptQuotation_Success() {
        booking.setStatus(BookingStatus.QUOTED);
        Quotation quotation = sentQuotation(7, 2);

        when(userRepository.findById(customerUserId)).thenReturn(Optional.of(customerUser));
        when(bookingRepository.findById(100)).thenReturn(Optional.of(booking));
        when(quotationRepository.findByIdAndBookingId(7, 100)).thenReturn(Optional.of(quotation));
        when(quotationRepository.save(quotation)).thenReturn(quotation);
        when(bookingRepository.save(booking)).thenReturn(booking);

        QuotationResponse response = quoteService.acceptQuotation(100, 7, customerUserId);

        assertThat(response.getStatus()).isEqualTo(QuotationStatus.ACCEPTED);
        assertThat(quotation.getAcceptedAt()).isNotNull();
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.APPROVED);
        assertThat(booking.getTotalAmount()).isEqualByComparingTo("1900000");
        assertThat(booking.getDiscountAmount()).isEqualByComparingTo("100000");

        ArgumentCaptor<BookingStatusHistory> historyCaptor = ArgumentCaptor.forClass(BookingStatusHistory.class);
        verify(bookingStatusHistoryRepository).save(historyCaptor.capture());
        assertThat(historyCaptor.getValue().getOldStatus()).isEqualTo(BookingStatus.QUOTED);
        assertThat(historyCaptor.getValue().getNewStatus()).isEqualTo(BookingStatus.APPROVED);
    }

    @Test
    @DisplayName("Accept quotation: Expired quotation should throw ERR_QUOTATION_EXPIRED")
    void testAcceptQuotation_Expired() {
        booking.setStatus(BookingStatus.QUOTED);
        Quotation quotation = sentQuotation(7, 2);
        quotation.setValidUntil(LocalDateTime.now().minusMinutes(1));

        when(userRepository.findById(customerUserId)).thenReturn(Optional.of(customerUser));
        when(bookingRepository.findById(100)).thenReturn(Optional.of(booking));
        when(quotationRepository.findByIdAndBookingId(7, 100)).thenReturn(Optional.of(quotation));

        assertThatThrownBy(() -> quoteService.acceptQuotation(100, 7, customerUserId))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ERR_QUOTATION_EXPIRED);
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.QUOTED);
        assertThat(booking.getTotalAmount()).isEqualByComparingTo("1500000");
    }

    @Test
    @DisplayName("Accept quotation: Quotation no longer SENT should throw ERR_QUOTATION_INVALID_STATUS")
    void testAcceptQuotation_NotSent() {
        booking.setStatus(BookingStatus.QUOTED);
        Quotation quotation = sentQuotation(7, 1);
        quotation.setStatus(QuotationStatus.EXPIRED);

        when(userRepository.findById(customerUserId)).thenReturn(Optional.of(customerUser));
        when(bookingRepository.findById(100)).thenReturn(Optional.of(booking));
        when(quotationRepository.findByIdAndBookingId(7, 100)).thenReturn(Optional.of(quotation));

        assertThatThrownBy(() -> quoteService.acceptQuotation(100, 7, customerUserId))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ERR_QUOTATION_INVALID_STATUS);
    }

    @Test
    @DisplayName("Accept quotation: Other customer's booking should throw ERR_BOOKING_FORBIDDEN")
    void testAcceptQuotation_Forbidden() {
        booking.setStatus(BookingStatus.QUOTED);
        Long otherUserId = 2L;

        when(userRepository.findById(otherUserId)).thenReturn(Optional.of(User.builder().id(otherUserId).build()));
        when(bookingRepository.findById(100)).thenReturn(Optional.of(booking));

        assertThatThrownBy(() -> quoteService.acceptQuotation(100, 7, otherUserId))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ERR_BOOKING_FORBIDDEN);
        verify(quotationRepository, never()).findByIdAndBookingId(any(), any());
    }

    @Test
    @DisplayName("Reject quotation: Should mark REJECTED and keep booking QUOTED with history note")
    void testRejectQuotation_Success() {
        booking.setStatus(BookingStatus.QUOTED);
        Quotation quotation = sentQuotation(7, 2);

        when(userRepository.findById(customerUserId)).thenReturn(Optional.of(customerUser));
        when(bookingRepository.findById(100)).thenReturn(Optional.of(booking));
        when(quotationRepository.findByIdAndBookingId(7, 100)).thenReturn(Optional.of(quotation));
        when(quotationRepository.save(quotation)).thenReturn(quotation);

        QuotationResponse response = quoteService.rejectQuotation(100, 7,
                RejectQuotationRequest.builder().reason(" Giá cao quá ").build(), customerUserId);

        assertThat(response.getStatus()).isEqualTo(QuotationStatus.REJECTED);
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.QUOTED);
        assertThat(booking.getTotalAmount()).isEqualByComparingTo("1500000");
        verify(bookingRepository, never()).save(any(Booking.class));

        ArgumentCaptor<BookingStatusHistory> historyCaptor = ArgumentCaptor.forClass(BookingStatusHistory.class);
        verify(bookingStatusHistoryRepository).save(historyCaptor.capture());
        assertThat(historyCaptor.getValue().getNote()).isEqualTo("Khách hàng từ chối báo giá phiên bản 2: Giá cao quá");
    }
}
