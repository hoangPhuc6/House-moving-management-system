package com.example.house_moving_service.modules.payment.service.impl;

import com.example.house_moving_service.common.dto.PageResponse;
import com.example.house_moving_service.common.exception.AppException;
import com.example.house_moving_service.common.exception.ErrorCode;
import com.example.house_moving_service.modules.booking.entity.Booking;
import com.example.house_moving_service.modules.booking.enums.BookingStatus;
import com.example.house_moving_service.modules.booking.repository.BookingRepository;
import com.example.house_moving_service.modules.booking.service.BookingWorkflowService;
import com.example.house_moving_service.modules.payment.dto.request.ConfirmPaymentRequest;
import com.example.house_moving_service.modules.payment.dto.request.CreatePaymentRequest;
import com.example.house_moving_service.modules.payment.dto.request.PaymentFilterRequest;
import com.example.house_moving_service.modules.payment.dto.response.PaymentResponse;
import com.example.house_moving_service.modules.payment.dto.response.PaymentSummaryResponse;
import com.example.house_moving_service.modules.payment.entity.Payment;
import com.example.house_moving_service.modules.payment.enums.PaymentStatus;
import com.example.house_moving_service.modules.payment.enums.PaymentType;
import com.example.house_moving_service.modules.payment.mapper.PaymentMapper;
import com.example.house_moving_service.modules.payment.repository.PaymentRepository;
import com.example.house_moving_service.modules.payment.repository.specification.PaymentSpecification;
import com.example.house_moving_service.modules.payment.service.PaymentManagementService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Service
@Slf4j
@RequiredArgsConstructor
public class PaymentManagementServiceImpl implements PaymentManagementService {

    private final PaymentRepository paymentRepository;
    private final BookingRepository bookingRepository;
    private final PaymentMapper paymentMapper;
    private final BookingWorkflowService bookingWorkflowService;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<PaymentSummaryResponse> searchPayments(PaymentFilterRequest filter, Pageable pageable) {
        log.info("Management searching payments with filter: {}, pageable: {}", filter, pageable);

        Specification<Payment> spec = PaymentSpecification.filterPayments(filter);
        Page<Payment> paymentPage = paymentRepository.findAll(spec, pageable);

        List<PaymentSummaryResponse> summaryList = paymentPage.getContent().stream()
                .map(paymentMapper::toSummaryResponse)
                .toList();

        return PageResponse.<PaymentSummaryResponse>builder()
                .pageNo(paymentPage.getNumber() + 1)
                .pageSize(paymentPage.getSize())
                .totalPages(paymentPage.getTotalPages())
                .totalElements(paymentPage.getTotalElements())
                .last(paymentPage.isLast())
                .items(summaryList)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public PaymentResponse getPaymentDetail(Integer id) {
        log.info("Management fetching payment detail for id: {}", id);
        Payment payment = paymentRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.ERR_PAYMENT_NOT_FOUND, id));
        return paymentMapper.toResponse(payment);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PaymentResponse> getPaymentsByBookingId(Integer bookingId) {
        log.info("Management fetching payment history for booking id: {}", bookingId);

        if (!bookingRepository.existsById(bookingId)) {
            throw new AppException(ErrorCode.ERR_BOOKING_NOT_FOUND, bookingId);
        }

        List<Payment> payments = paymentRepository.findAllByBookingIdOrderByCreatedAtDesc(bookingId);
        return payments.stream()
                .map(paymentMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public PaymentResponse confirmPayment(Integer id, ConfirmPaymentRequest request, Long staffId) {
        log.info("Staff user {} confirming payment id: {}", staffId, id);

        Payment payment = paymentRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.ERR_PAYMENT_NOT_FOUND, id));

        if (payment.getStatus() != PaymentStatus.PENDING) {
            throw new AppException(ErrorCode.ERR_PAYMENT_INVALID_STATUS, payment.getStatus());
        }

        payment.setStatus(PaymentStatus.SUCCESS);
        payment.setPaymentTime(LocalDateTime.now());

        if (request != null) {
            if (request.getTransactionCode() != null && !request.getTransactionCode().trim().isEmpty()) {
                payment.setTransactionCode(request.getTransactionCode().trim());
            }
            if (request.getNote() != null && !request.getNote().trim().isEmpty()) {
                String updatedNote = (payment.getNote() != null && !payment.getNote().isEmpty())
                        ? payment.getNote() + " | " + request.getNote().trim()
                        : request.getNote().trim();
                payment.setNote(updatedNote);
            }
        }

        Payment savedPayment = paymentRepository.save(payment);

        // Khi khách đã thanh toán đặt cọc thành công, tự động chuyển đơn sang CONFIRMED (nếu đơn đang APPROVED)
        if (payment.getPaymentType() == PaymentType.DEPOSIT && payment.getBooking() != null) {
            Booking booking = payment.getBooking();
            if (booking.getStatus() == BookingStatus.APPROVED) {
                try {
                    bookingWorkflowService.changeStatus(
                            booking.getId(),
                            Set.of(BookingStatus.APPROVED),
                            BookingStatus.CONFIRMED,
                            staffId,
                            "Xác nhận đã nhận tiền đặt cọc " + payment.getAmount() + " VNĐ"
                    );
                } catch (Exception ex) {
                    log.warn("Could not transition booking status upon deposit payment confirmation: {}", ex.getMessage());
                }
            }
        }

        return paymentMapper.toResponse(savedPayment);
    }

    @Override
    @Transactional
    public PaymentResponse rejectPayment(Integer id, String reason, Long staffId) {
        log.info("Staff user {} rejecting payment id: {} with reason: {}", staffId, id, reason);

        Payment payment = paymentRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.ERR_PAYMENT_NOT_FOUND, id));

        if (payment.getStatus() != PaymentStatus.PENDING) {
            throw new AppException(ErrorCode.ERR_PAYMENT_INVALID_STATUS, payment.getStatus());
        }

        payment.setStatus(PaymentStatus.FAILED);
        if (reason != null && !reason.trim().isEmpty()) {
            String updatedNote = (payment.getNote() != null && !payment.getNote().isEmpty())
                    ? payment.getNote() + " | Lý do từ chối: " + reason.trim()
                    : "Lý do từ chối: " + reason.trim();
            payment.setNote(updatedNote);
        }

        Payment savedPayment = paymentRepository.save(payment);
        return paymentMapper.toResponse(savedPayment);
    }

    @Override
    @Transactional
    public PaymentResponse recordManualPayment(CreatePaymentRequest request, Long staffId) {
        log.info("Staff user {} recording manual payment for booking: {}", staffId, request.getBookingId());

        Booking booking = bookingRepository.findById(request.getBookingId())
                .orElseThrow(() -> new AppException(ErrorCode.ERR_BOOKING_NOT_FOUND, request.getBookingId()));

        Payment payment = Payment.builder()
                .booking(booking)
                .paymentMethod(request.getPaymentMethod())
                .paymentType(request.getPaymentType())
                .amount(request.getAmount())
                .status(PaymentStatus.SUCCESS)
                .paymentTime(LocalDateTime.now())
                .note(request.getNote())
                .build();

        Payment savedPayment = paymentRepository.save(payment);

        // Cập nhật trạng thái đơn hàng nếu cọc thành công
        if (payment.getPaymentType() == PaymentType.DEPOSIT && booking.getStatus() == BookingStatus.APPROVED) {
            try {
                bookingWorkflowService.changeStatus(
                        booking.getId(),
                        Set.of(BookingStatus.APPROVED),
                        BookingStatus.CONFIRMED,
                        staffId,
                        "Thu tiền đặt cọc trực tiếp tại quầy: " + payment.getAmount() + " VNĐ"
                );
            } catch (Exception ex) {
                log.warn("Could not transition booking status upon manual deposit payment: {}", ex.getMessage());
            }
        }

        return paymentMapper.toResponse(savedPayment);
    }

    @Override
    @Transactional
    public PaymentResponse createRefund(CreatePaymentRequest request, Long staffId) {
        log.info("Staff user {} creating refund for booking: {}", staffId, request.getBookingId());

        Booking booking = bookingRepository.findById(request.getBookingId())
                .orElseThrow(() -> new AppException(ErrorCode.ERR_BOOKING_NOT_FOUND, request.getBookingId()));

        Payment refundPayment = Payment.builder()
                .booking(booking)
                .paymentMethod(request.getPaymentMethod())
                .paymentType(PaymentType.REFUND)
                .amount(request.getAmount())
                .status(PaymentStatus.SUCCESS)
                .paymentTime(LocalDateTime.now())
                .note(request.getNote() != null ? request.getNote() : "Hoàn tiền cho khách hàng")
                .build();

        Payment savedPayment = paymentRepository.save(refundPayment);
        return paymentMapper.toResponse(savedPayment);
    }
}

