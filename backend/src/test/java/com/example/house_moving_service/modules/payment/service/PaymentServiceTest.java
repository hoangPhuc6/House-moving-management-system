package com.example.house_moving_service.modules.payment.service;

import com.example.house_moving_service.common.exception.AppException;
import com.example.house_moving_service.common.exception.ErrorCode;
import com.example.house_moving_service.modules.booking.entity.Booking;
import com.example.house_moving_service.modules.booking.enums.BookingStatus;
import com.example.house_moving_service.modules.booking.repository.BookingRepository;
import com.example.house_moving_service.modules.identity.entity.Customer;
import com.example.house_moving_service.modules.payment.dto.request.CreatePaymentRequest;
import com.example.house_moving_service.modules.payment.dto.response.PaymentResponse;
import com.example.house_moving_service.modules.payment.entity.Payment;
import com.example.house_moving_service.modules.payment.enums.PaymentMethod;
import com.example.house_moving_service.modules.payment.enums.PaymentStatus;
import com.example.house_moving_service.modules.payment.enums.PaymentType;
import com.example.house_moving_service.modules.payment.mapper.PaymentMapper;
import com.example.house_moving_service.modules.payment.repository.PaymentRepository;
import com.example.house_moving_service.modules.payment.service.impl.PaymentServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private PaymentMapper paymentMapper;

    @InjectMocks
    private PaymentServiceImpl paymentService;

    private Long customerUserId;
    private Customer customer;
    private Booking booking;
    private Payment pendingPayment;

    @BeforeEach
    void setUp() {
        customerUserId = 10L;
        customer = Customer.builder()
                .id(customerUserId)
                .fullName("Nguyen Van A")
                .phoneNumber("0901234567")
                .build();

        booking = Booking.builder()
                .id(100)
                .bookingCode("BK-20261103-001")
                .customer(customer)
                .status(BookingStatus.APPROVED)
                .totalAmount(BigDecimal.valueOf(2_000_000))
                .build();

        pendingPayment = Payment.builder()
                .id(1)
                .booking(booking)
                .paymentMethod(PaymentMethod.BANK_TRANSFER)
                .paymentType(PaymentType.DEPOSIT)
                .amount(BigDecimal.valueOf(500_000))
                .status(PaymentStatus.PENDING)
                .transactionCode("DEP-BK20261103001-123456")
                .createdAt(LocalDateTime.now())
                .build();
    }

    // =========================================================================
    // createPayment
    // =========================================================================
    @Nested
    @DisplayName("Tests for createPayment")
    class CreatePaymentTests {

        @Test
        @DisplayName("createPayment: Success when deposit for APPROVED booking")
        void createPayment_Deposit_Success() {
            CreatePaymentRequest request = CreatePaymentRequest.builder()
                    .bookingId(100)
                    .paymentMethod(PaymentMethod.BANK_TRANSFER)
                    .paymentType(PaymentType.DEPOSIT)
                    .amount(BigDecimal.valueOf(500_000))
                    .note("Dat coc chuyen nha")
                    .build();

            PaymentResponse expectedResponse = PaymentResponse.builder()
                    .id(1)
                    .bookingId(100)
                    .bookingCode("BK-20261103-001")
                    .paymentMethod(PaymentMethod.BANK_TRANSFER)
                    .paymentType(PaymentType.DEPOSIT)
                    .amount(BigDecimal.valueOf(500_000))
                    .status(PaymentStatus.PENDING)
                    .build();

            when(bookingRepository.findById(100)).thenReturn(Optional.of(booking));
            when(paymentRepository.findAllByBookingId(100)).thenReturn(Collections.emptyList());
            when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> {
                Payment p = invocation.getArgument(0);
                p.setId(1);
                return p;
            });
            when(paymentMapper.toResponse(any(Payment.class), any())).thenReturn(expectedResponse);

            PaymentResponse actual = paymentService.createPayment(request, customerUserId);

            assertThat(actual).isNotNull();
            assertThat(actual.getId()).isEqualTo(1);
            assertThat(actual.getStatus()).isEqualTo(PaymentStatus.PENDING);
            verify(paymentRepository).save(any(Payment.class));
        }

        @Test
        @DisplayName("createPayment: Throws ERR_BOOKING_NOT_FOUND when booking does not exist")
        void createPayment_BookingNotFound() {
            CreatePaymentRequest request = CreatePaymentRequest.builder()
                    .bookingId(999)
                    .paymentMethod(PaymentMethod.CASH)
                    .paymentType(PaymentType.FINAL)
                    .amount(BigDecimal.valueOf(100_000))
                    .build();

            when(bookingRepository.findById(999)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> paymentService.createPayment(request, customerUserId))
                    .isInstanceOf(AppException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ERR_BOOKING_NOT_FOUND);

            verify(paymentRepository, never()).save(any());
        }

        @Test
        @DisplayName("createPayment: Throws ERR_BOOKING_FORBIDDEN when user does not own booking (IDOR)")
        void createPayment_IDOR_Forbidden() {
            Long strangerUserId = 999L;
            CreatePaymentRequest request = CreatePaymentRequest.builder()
                    .bookingId(100)
                    .paymentMethod(PaymentMethod.CASH)
                    .paymentType(PaymentType.FINAL)
                    .amount(BigDecimal.valueOf(100_000))
                    .build();

            when(bookingRepository.findById(100)).thenReturn(Optional.of(booking));

            assertThatThrownBy(() -> paymentService.createPayment(request, strangerUserId))
                    .isInstanceOf(AppException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ERR_BOOKING_FORBIDDEN);

            verify(paymentRepository, never()).save(any());
        }

        @Test
        @DisplayName("createPayment: Throws ERR_BOOKING_INVALID_STATUS when booking is CANCELLED")
        void createPayment_BookingCancelled_ThrowsException() {
            booking.setStatus(BookingStatus.CANCELLED);
            CreatePaymentRequest request = CreatePaymentRequest.builder()
                    .bookingId(100)
                    .paymentMethod(PaymentMethod.BANK_TRANSFER)
                    .paymentType(PaymentType.DEPOSIT)
                    .amount(BigDecimal.valueOf(500_000))
                    .build();

            when(bookingRepository.findById(100)).thenReturn(Optional.of(booking));

            assertThatThrownBy(() -> paymentService.createPayment(request, customerUserId))
                    .isInstanceOf(AppException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ERR_BOOKING_INVALID_STATUS);
        }

        @Test
        @DisplayName("createPayment: Throws ERR_BOOKING_INVALID_STATUS when deposit attempted on PENDING_SURVEY")
        void createPayment_DepositBeforeApproved_ThrowsException() {
            booking.setStatus(BookingStatus.PENDING_SURVEY);
            CreatePaymentRequest request = CreatePaymentRequest.builder()
                    .bookingId(100)
                    .paymentMethod(PaymentMethod.BANK_TRANSFER)
                    .paymentType(PaymentType.DEPOSIT)
                    .amount(BigDecimal.valueOf(500_000))
                    .build();

            when(bookingRepository.findById(100)).thenReturn(Optional.of(booking));
            when(paymentRepository.findAllByBookingId(100)).thenReturn(Collections.emptyList());

            assertThatThrownBy(() -> paymentService.createPayment(request, customerUserId))
                    .isInstanceOf(AppException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ERR_BOOKING_INVALID_STATUS);
        }

        @Test
        @DisplayName("createPayment: Throws ERR_PAYMENT_ALREADY_PROCESSED when deposit already paid successfully")
        void createPayment_DuplicateDeposit_ThrowsException() {
            Payment paidDeposit = Payment.builder()
                    .id(5)
                    .paymentType(PaymentType.DEPOSIT)
                    .status(PaymentStatus.SUCCESS)
                    .build();

            CreatePaymentRequest request = CreatePaymentRequest.builder()
                    .bookingId(100)
                    .paymentMethod(PaymentMethod.BANK_TRANSFER)
                    .paymentType(PaymentType.DEPOSIT)
                    .amount(BigDecimal.valueOf(500_000))
                    .build();

            when(bookingRepository.findById(100)).thenReturn(Optional.of(booking));
            when(paymentRepository.findAllByBookingId(100)).thenReturn(List.of(paidDeposit));

            assertThatThrownBy(() -> paymentService.createPayment(request, customerUserId))
                    .isInstanceOf(AppException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ERR_PAYMENT_ALREADY_PROCESSED);
        }

        @Test
        @DisplayName("createPayment: Throws ERR_INVALID_INPUT when amount exceeds booking total")
        void createPayment_AmountExceedsTotal_ThrowsException() {
            CreatePaymentRequest request = CreatePaymentRequest.builder()
                    .bookingId(100)
                    .paymentMethod(PaymentMethod.BANK_TRANSFER)
                    .paymentType(PaymentType.FINAL)
                    .amount(BigDecimal.valueOf(3_000_000)) // > totalAmount 2_000_000
                    .build();

            when(bookingRepository.findById(100)).thenReturn(Optional.of(booking));
            when(paymentRepository.findAllByBookingId(100)).thenReturn(Collections.emptyList());

            assertThatThrownBy(() -> paymentService.createPayment(request, customerUserId))
                    .isInstanceOf(AppException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ERR_INVALID_INPUT);
        }
    }

    // =========================================================================
    // getPaymentsByBookingId
    // =========================================================================
    @Nested
    @DisplayName("Tests for getPaymentsByBookingId")
    class GetPaymentsByBookingIdTests {

        @Test
        @DisplayName("getPaymentsByBookingId: Success returns list of payments for owned booking")
        void getPaymentsByBookingId_Success() {
            PaymentResponse responseDto = PaymentResponse.builder().id(1).build();

            when(bookingRepository.findById(100)).thenReturn(Optional.of(booking));
            when(paymentRepository.findAllByBookingIdOrderByCreatedAtDesc(100)).thenReturn(List.of(pendingPayment));
            when(paymentMapper.toResponse(pendingPayment)).thenReturn(responseDto);

            List<PaymentResponse> result = paymentService.getPaymentsByBookingId(100, customerUserId);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getId()).isEqualTo(1);
        }

        @Test
        @DisplayName("getPaymentsByBookingId: Throws ERR_BOOKING_FORBIDDEN on IDOR")
        void getPaymentsByBookingId_IDOR_ThrowsException() {
            when(bookingRepository.findById(100)).thenReturn(Optional.of(booking));

            assertThatThrownBy(() -> paymentService.getPaymentsByBookingId(100, 999L))
                    .isInstanceOf(AppException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ERR_BOOKING_FORBIDDEN);
        }
    }

    // =========================================================================
    // getPaymentDetail
    // =========================================================================
    @Nested
    @DisplayName("Tests for getPaymentDetail")
    class GetPaymentDetailTests {

        @Test
        @DisplayName("getPaymentDetail: Success returns detail of owned payment")
        void getPaymentDetail_Success() {
            PaymentResponse responseDto = PaymentResponse.builder().id(1).build();

            when(paymentRepository.findById(1)).thenReturn(Optional.of(pendingPayment));
            when(paymentMapper.toResponse(eq(pendingPayment), any())).thenReturn(responseDto);

            PaymentResponse actual = paymentService.getPaymentDetail(1, customerUserId);

            assertThat(actual).isNotNull();
            assertThat(actual.getId()).isEqualTo(1);
        }

        @Test
        @DisplayName("getPaymentDetail: Throws ERR_PAYMENT_NOT_FOUND when payment does not exist")
        void getPaymentDetail_NotFound() {
            when(paymentRepository.findById(999)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> paymentService.getPaymentDetail(999, customerUserId))
                    .isInstanceOf(AppException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ERR_PAYMENT_NOT_FOUND);
        }

        @Test
        @DisplayName("getPaymentDetail: Throws ERR_BOOKING_FORBIDDEN on IDOR check")
        void getPaymentDetail_IDOR_Forbidden() {
            when(paymentRepository.findById(1)).thenReturn(Optional.of(pendingPayment));

            assertThatThrownBy(() -> paymentService.getPaymentDetail(1, 999L))
                    .isInstanceOf(AppException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ERR_BOOKING_FORBIDDEN);
        }
    }

    // =========================================================================
    // cancelPayment
    // =========================================================================
    @Nested
    @DisplayName("Tests for cancelPayment")
    class CancelPaymentTests {

        @Test
        @DisplayName("cancelPayment: Success when payment is PENDING")
        void cancelPayment_Success() {
            PaymentResponse responseDto = PaymentResponse.builder()
                    .id(1)
                    .status(PaymentStatus.CANCELLED)
                    .build();

            when(paymentRepository.findById(1)).thenReturn(Optional.of(pendingPayment));
            when(paymentRepository.save(any(Payment.class))).thenAnswer(i -> i.getArgument(0));
            when(paymentMapper.toResponse(any(Payment.class))).thenReturn(responseDto);

            PaymentResponse result = paymentService.cancelPayment(1, customerUserId);

            assertThat(result.getStatus()).isEqualTo(PaymentStatus.CANCELLED);
            assertThat(pendingPayment.getStatus()).isEqualTo(PaymentStatus.CANCELLED);
            verify(paymentRepository).save(pendingPayment);
        }

        @Test
        @DisplayName("cancelPayment: Throws ERR_PAYMENT_INVALID_STATUS when payment is already SUCCESS")
        void cancelPayment_AlreadySuccess_ThrowsException() {
            pendingPayment.setStatus(PaymentStatus.SUCCESS);

            when(paymentRepository.findById(1)).thenReturn(Optional.of(pendingPayment));

            assertThatThrownBy(() -> paymentService.cancelPayment(1, customerUserId))
                    .isInstanceOf(AppException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ERR_PAYMENT_INVALID_STATUS);

            verify(paymentRepository, never()).save(any());
        }

        @Test
        @DisplayName("cancelPayment: Throws ERR_BOOKING_FORBIDDEN on IDOR")
        void cancelPayment_IDOR_ThrowsException() {
            when(paymentRepository.findById(1)).thenReturn(Optional.of(pendingPayment));

            assertThatThrownBy(() -> paymentService.cancelPayment(1, 999L))
                    .isInstanceOf(AppException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ERR_BOOKING_FORBIDDEN);
        }
    }
}

