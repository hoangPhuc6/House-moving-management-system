package com.example.house_moving_service.modules.payment.service;

import com.example.house_moving_service.common.dto.PageResponse;
import com.example.house_moving_service.common.exception.AppException;
import com.example.house_moving_service.common.exception.ErrorCode;
import com.example.house_moving_service.modules.booking.entity.Booking;
import com.example.house_moving_service.modules.booking.enums.BookingStatus;
import com.example.house_moving_service.modules.booking.repository.BookingRepository;
import com.example.house_moving_service.modules.booking.service.BookingWorkflowService;
import com.example.house_moving_service.modules.identity.entity.Customer;
import com.example.house_moving_service.modules.payment.dto.request.ConfirmPaymentRequest;
import com.example.house_moving_service.modules.payment.dto.request.CreatePaymentRequest;
import com.example.house_moving_service.modules.payment.dto.request.PaymentFilterRequest;
import com.example.house_moving_service.modules.payment.dto.response.PaymentResponse;
import com.example.house_moving_service.modules.payment.dto.response.PaymentSummaryResponse;
import com.example.house_moving_service.modules.payment.entity.Payment;
import com.example.house_moving_service.modules.payment.enums.PaymentMethod;
import com.example.house_moving_service.modules.payment.enums.PaymentStatus;
import com.example.house_moving_service.modules.payment.enums.PaymentType;
import com.example.house_moving_service.modules.payment.mapper.PaymentMapper;
import com.example.house_moving_service.modules.payment.repository.PaymentRepository;
import com.example.house_moving_service.modules.payment.service.impl.PaymentManagementServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentManagementServiceTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private PaymentMapper paymentMapper;

    @Mock
    private BookingWorkflowService bookingWorkflowService;

    @InjectMocks
    private PaymentManagementServiceImpl paymentManagementService;

    private Long staffUserId;
    private Booking booking;
    private Payment pendingPayment;

    @BeforeEach
    void setUp() {
        staffUserId = 99L;

        Customer customer = Customer.builder()
                .id(1L)
                .fullName("Tran Van C")
                .phoneNumber("0912345678")
                .build();

        booking = Booking.builder()
                .id(200)
                .bookingCode("BK-20261103-200")
                .customer(customer)
                .status(BookingStatus.APPROVED)
                .totalAmount(BigDecimal.valueOf(3_500_000))
                .build();

        pendingPayment = Payment.builder()
                .id(10)
                .booking(booking)
                .paymentMethod(PaymentMethod.BANK_TRANSFER)
                .paymentType(PaymentType.DEPOSIT)
                .amount(BigDecimal.valueOf(1_000_000))
                .status(PaymentStatus.PENDING)
                .transactionCode("DEP-BK200-999")
                .createdAt(LocalDateTime.now())
                .build();
    }

    // =========================================================================
    // searchPayments
    // =========================================================================
    @Nested
    @DisplayName("Tests for searchPayments")
    class SearchPaymentsTests {

        @Test
        @DisplayName("searchPayments: Success returns paginated summary response")
        void searchPayments_Success() {
            PaymentFilterRequest filter = PaymentFilterRequest.builder()
                    .status(PaymentStatus.PENDING)
                    .build();
            Pageable pageable = PageRequest.of(0, 10);

            PaymentSummaryResponse summaryDto = PaymentSummaryResponse.builder()
                    .id(10)
                    .bookingId(200)
                    .status(PaymentStatus.PENDING)
                    .build();

            Page<Payment> mockPage = new PageImpl<>(List.of(pendingPayment), pageable, 1);

            when(paymentRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(mockPage);
            when(paymentMapper.toSummaryResponse(pendingPayment)).thenReturn(summaryDto);

            PageResponse<PaymentSummaryResponse> response = paymentManagementService.searchPayments(filter, pageable);

            assertThat(response).isNotNull();
            assertThat(response.getTotalElements()).isEqualTo(1);
            assertThat(response.getItems()).hasSize(1);
            assertThat(response.getItems().get(0).getId()).isEqualTo(10);
        }
    }

    // =========================================================================
    // getPaymentDetail
    // =========================================================================
    @Nested
    @DisplayName("Tests for getPaymentDetail")
    class GetPaymentDetailTests {

        @Test
        @DisplayName("getPaymentDetail: Success returns full payment detail without IDOR checks")
        void getPaymentDetail_Success() {
            PaymentResponse responseDto = PaymentResponse.builder()
                    .id(10)
                    .bookingId(200)
                    .status(PaymentStatus.PENDING)
                    .build();

            when(paymentRepository.findById(10)).thenReturn(Optional.of(pendingPayment));
            when(paymentMapper.toResponse(pendingPayment)).thenReturn(responseDto);

            PaymentResponse actual = paymentManagementService.getPaymentDetail(10);

            assertThat(actual).isNotNull();
            assertThat(actual.getId()).isEqualTo(10);
            assertThat(actual.getBookingId()).isEqualTo(200);
        }

        @Test
        @DisplayName("getPaymentDetail: Throws ERR_PAYMENT_NOT_FOUND when payment does not exist")
        void getPaymentDetail_NotFound() {
            when(paymentRepository.findById(999)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> paymentManagementService.getPaymentDetail(999))
                    .isInstanceOf(AppException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ERR_PAYMENT_NOT_FOUND);
        }
    }

    // =========================================================================
    // getPaymentsByBookingId
    // =========================================================================
    @Nested
    @DisplayName("Tests for getPaymentsByBookingId")
    class GetPaymentsByBookingIdTests {

        @Test
        @DisplayName("getPaymentsByBookingId: Success returns list of payments for booking")
        void getPaymentsByBookingId_Success() {
            PaymentResponse responseDto = PaymentResponse.builder().id(10).build();

            when(bookingRepository.existsById(200)).thenReturn(true);
            when(paymentRepository.findAllByBookingIdOrderByCreatedAtDesc(200)).thenReturn(List.of(pendingPayment));
            when(paymentMapper.toResponse(pendingPayment)).thenReturn(responseDto);

            List<PaymentResponse> result = paymentManagementService.getPaymentsByBookingId(200);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getId()).isEqualTo(10);
        }

        @Test
        @DisplayName("getPaymentsByBookingId: Throws ERR_BOOKING_NOT_FOUND when booking does not exist")
        void getPaymentsByBookingId_BookingNotFound() {
            when(bookingRepository.existsById(999)).thenReturn(false);

            assertThatThrownBy(() -> paymentManagementService.getPaymentsByBookingId(999))
                    .isInstanceOf(AppException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ERR_BOOKING_NOT_FOUND);
        }
    }

    // =========================================================================
    // confirmPayment
    // =========================================================================
    @Nested
    @DisplayName("Tests for confirmPayment")
    class ConfirmPaymentTests {

        @Test
        @DisplayName("confirmPayment: Success on deposit payment updates status to SUCCESS and transitions booking to CONFIRMED")
        void confirmPayment_Deposit_Success_TransitionsBooking() {
            ConfirmPaymentRequest request = ConfirmPaymentRequest.builder()
                    .transactionCode("VNPAY-CONFIRMED-12345")
                    .note("Xac nhan da nhan tien")
                    .build();

            PaymentResponse responseDto = PaymentResponse.builder()
                    .id(10)
                    .status(PaymentStatus.SUCCESS)
                    .build();

            when(paymentRepository.findById(10)).thenReturn(Optional.of(pendingPayment));
            when(paymentRepository.save(any(Payment.class))).thenAnswer(i -> i.getArgument(0));
            when(paymentMapper.toResponse(any(Payment.class))).thenReturn(responseDto);

            PaymentResponse result = paymentManagementService.confirmPayment(10, request, staffUserId);

            assertThat(result.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
            assertThat(pendingPayment.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
            assertThat(pendingPayment.getTransactionCode()).isEqualTo("VNPAY-CONFIRMED-12345");
            assertThat(pendingPayment.getPaymentTime()).isNotNull();

            // Xác minh BookingWorkflowService được gọi để đổi trạng thái đơn sang CONFIRMED
            verify(bookingWorkflowService).changeStatus(
                    eq(200),
                    eq(Set.of(BookingStatus.APPROVED)),
                    eq(BookingStatus.CONFIRMED),
                    eq(staffUserId),
                    any()
            );
        }

        @Test
        @DisplayName("confirmPayment: Success on FINAL payment does not transition booking status")
        void confirmPayment_FinalPayment_Success_NoStatusTransition() {
            pendingPayment.setPaymentType(PaymentType.FINAL);

            when(paymentRepository.findById(10)).thenReturn(Optional.of(pendingPayment));
            when(paymentRepository.save(any(Payment.class))).thenAnswer(i -> i.getArgument(0));
            when(paymentMapper.toResponse(any(Payment.class))).thenReturn(PaymentResponse.builder().id(10).build());

            paymentManagementService.confirmPayment(10, null, staffUserId);

            assertThat(pendingPayment.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
            verify(bookingWorkflowService, never()).changeStatus(any(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("confirmPayment: Throws ERR_PAYMENT_INVALID_STATUS when payment is not PENDING")
        void confirmPayment_NotPending_ThrowsException() {
            pendingPayment.setStatus(PaymentStatus.SUCCESS);

            when(paymentRepository.findById(10)).thenReturn(Optional.of(pendingPayment));

            assertThatThrownBy(() -> paymentManagementService.confirmPayment(10, null, staffUserId))
                    .isInstanceOf(AppException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ERR_PAYMENT_INVALID_STATUS);

            verify(paymentRepository, never()).save(any());
        }
    }

    // =========================================================================
    // rejectPayment
    // =========================================================================
    @Nested
    @DisplayName("Tests for rejectPayment")
    class RejectPaymentTests {

        @Test
        @DisplayName("rejectPayment: Success updates status to FAILED and adds rejection note")
        void rejectPayment_Success() {
            PaymentResponse responseDto = PaymentResponse.builder()
                    .id(10)
                    .status(PaymentStatus.FAILED)
                    .build();

            when(paymentRepository.findById(10)).thenReturn(Optional.of(pendingPayment));
            when(paymentRepository.save(any(Payment.class))).thenAnswer(i -> i.getArgument(0));
            when(paymentMapper.toResponse(any(Payment.class))).thenReturn(responseDto);

            PaymentResponse result = paymentManagementService.rejectPayment(10, "Bill gia mao", staffUserId);

            assertThat(result.getStatus()).isEqualTo(PaymentStatus.FAILED);
            assertThat(pendingPayment.getStatus()).isEqualTo(PaymentStatus.FAILED);
            assertThat(pendingPayment.getNote()).contains("Bill gia mao");
            verify(paymentRepository).save(pendingPayment);
        }

        @Test
        @DisplayName("rejectPayment: Throws ERR_PAYMENT_INVALID_STATUS when payment is already processed")
        void rejectPayment_AlreadyProcessed_ThrowsException() {
            pendingPayment.setStatus(PaymentStatus.FAILED);

            when(paymentRepository.findById(10)).thenReturn(Optional.of(pendingPayment));

            assertThatThrownBy(() -> paymentManagementService.rejectPayment(10, "Ly do", staffUserId))
                    .isInstanceOf(AppException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ERR_PAYMENT_INVALID_STATUS);
        }
    }

    // =========================================================================
    // recordManualPayment
    // =========================================================================
    @Nested
    @DisplayName("Tests for recordManualPayment")
    class RecordManualPaymentTests {

        @Test
        @DisplayName("recordManualPayment: Success creates payment in SUCCESS status and transitions booking on DEPOSIT")
        void recordManualPayment_Deposit_Success() {
            CreatePaymentRequest request = CreatePaymentRequest.builder()
                    .bookingId(200)
                    .paymentMethod(PaymentMethod.CASH)
                    .paymentType(PaymentType.DEPOSIT)
                    .amount(BigDecimal.valueOf(1_000_000))
                    .note("Thu tien mat tai van phong")
                    .build();

            when(bookingRepository.findById(200)).thenReturn(Optional.of(booking));
            when(paymentRepository.save(any(Payment.class))).thenAnswer(i -> {
                Payment p = i.getArgument(0);
                p.setId(15);
                return p;
            });
            when(paymentMapper.toResponse(any(Payment.class))).thenReturn(PaymentResponse.builder().id(15).status(PaymentStatus.SUCCESS).build());

            PaymentResponse response = paymentManagementService.recordManualPayment(request, staffUserId);

            assertThat(response).isNotNull();
            assertThat(response.getId()).isEqualTo(15);
            verify(bookingWorkflowService).changeStatus(
                    eq(200),
                    eq(Set.of(BookingStatus.APPROVED)),
                    eq(BookingStatus.CONFIRMED),
                    eq(staffUserId),
                    any()
            );
        }

        @Test
        @DisplayName("recordManualPayment: Throws ERR_BOOKING_NOT_FOUND when booking does not exist")
        void recordManualPayment_BookingNotFound() {
            CreatePaymentRequest request = CreatePaymentRequest.builder()
                    .bookingId(999)
                    .paymentMethod(PaymentMethod.CASH)
                    .paymentType(PaymentType.DEPOSIT)
                    .amount(BigDecimal.valueOf(1_000_000))
                    .build();

            when(bookingRepository.findById(999)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> paymentManagementService.recordManualPayment(request, staffUserId))
                    .isInstanceOf(AppException.class)
                    .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ERR_BOOKING_NOT_FOUND);
        }
    }

    // =========================================================================
    // createRefund
    // =========================================================================
    @Nested
    @DisplayName("Tests for createRefund")
    class CreateRefundTests {

        @Test
        @DisplayName("createRefund: Success creates REFUND payment in SUCCESS status")
        void createRefund_Success() {
            CreatePaymentRequest request = CreatePaymentRequest.builder()
                    .bookingId(200)
                    .paymentMethod(PaymentMethod.BANK_TRANSFER)
                    .paymentType(PaymentType.REFUND)
                    .amount(BigDecimal.valueOf(500_000))
                    .note("Hoan tra tien dat coc")
                    .build();

            when(bookingRepository.findById(200)).thenReturn(Optional.of(booking));
            when(paymentRepository.save(any(Payment.class))).thenAnswer(i -> {
                Payment p = i.getArgument(0);
                p.setId(20);
                return p;
            });
            when(paymentMapper.toResponse(any(Payment.class))).thenReturn(PaymentResponse.builder().id(20).paymentType(PaymentType.REFUND).status(PaymentStatus.SUCCESS).build());

            PaymentResponse response = paymentManagementService.createRefund(request, staffUserId);

            assertThat(response).isNotNull();
            assertThat(response.getId()).isEqualTo(20);
            assertThat(response.getPaymentType()).isEqualTo(PaymentType.REFUND);
            verify(paymentRepository).save(any(Payment.class));
        }
    }
}

