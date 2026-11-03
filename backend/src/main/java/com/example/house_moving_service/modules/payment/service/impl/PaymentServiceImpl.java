package com.example.house_moving_service.modules.payment.service.impl;

import com.example.house_moving_service.common.exception.AppException;
import com.example.house_moving_service.common.exception.ErrorCode;
import com.example.house_moving_service.modules.booking.entity.Booking;
import com.example.house_moving_service.modules.booking.enums.BookingStatus;
import com.example.house_moving_service.modules.booking.repository.BookingRepository;
import com.example.house_moving_service.modules.payment.dto.request.CreatePaymentRequest;
import com.example.house_moving_service.modules.payment.dto.response.PaymentResponse;
import com.example.house_moving_service.modules.payment.entity.Payment;
import com.example.house_moving_service.modules.payment.enums.PaymentMethod;
import com.example.house_moving_service.modules.payment.enums.PaymentStatus;
import com.example.house_moving_service.modules.payment.enums.PaymentType;
import com.example.house_moving_service.modules.payment.mapper.PaymentMapper;
import com.example.house_moving_service.modules.payment.repository.PaymentRepository;
import com.example.house_moving_service.modules.payment.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {

    private final PaymentRepository paymentRepository;
    private final BookingRepository bookingRepository;
    private final PaymentMapper paymentMapper;

    @Override
    @Transactional
    public PaymentResponse createPayment(CreatePaymentRequest request, Long userId) {
        log.info("Customer user {} creating payment for booking id: {}", userId, request.getBookingId());

        Booking booking = findOwnedBooking(request.getBookingId(), userId);

        // 1. Kiểm tra trạng thái đơn hàng
        if (booking.getStatus() == BookingStatus.CANCELLED) {
            log.warn("Cannot make payment for cancelled booking id: {}", booking.getId());
            throw new AppException(ErrorCode.ERR_BOOKING_INVALID_STATUS);
        }

        // 2. Ràng buộc theo loại thanh toán (DEPOSIT, FINAL, PARTIAL)
        List<Payment> existingPayments = paymentRepository.findAllByBookingId(booking.getId());

        if (request.getPaymentType() == PaymentType.DEPOSIT) {
            // Không được cọc nếu đơn chưa được duyệt báo giá (phải từ APPROVED trở đi)
            if (booking.getStatus() != BookingStatus.APPROVED && booking.getStatus() != BookingStatus.CONFIRMED) {
                log.warn("Booking id: {} is not in APPROVED/CONFIRMED status for deposit, current status: {}",
                        booking.getId(), booking.getStatus());
                throw new AppException(ErrorCode.ERR_BOOKING_INVALID_STATUS);
            }

            // Kiểm tra xem đã có giao dịch cọc thành công trước đó chưa
            boolean hasSuccessfulDeposit = existingPayments.stream()
                    .anyMatch(p -> p.getPaymentType() == PaymentType.DEPOSIT && p.getStatus() == PaymentStatus.SUCCESS);
            if (hasSuccessfulDeposit) {
                log.warn("Booking id: {} already has a successful deposit payment", booking.getId());
                throw new AppException(ErrorCode.ERR_PAYMENT_ALREADY_PROCESSED);
            }
        }

        // 3. Kiểm tra số tiền thanh toán không được vượt quá tổng giá trị đơn hàng
        if (booking.getTotalAmount() != null && request.getAmount().compareTo(booking.getTotalAmount()) > 0) {
            log.warn("Payment amount {} exceeds total booking amount {}", request.getAmount(), booking.getTotalAmount());
            throw new AppException(ErrorCode.ERR_INVALID_INPUT, "Số tiền thanh toán vượt quá tổng giá trị đơn hàng");
        }

        // 4. Sinh mã giao dịch khởi tạo cho khách (dùng để đối soát/nội dung chuyển khoản)
        String generatedTxnCode = generateTransactionCode(booking, request.getPaymentType());

        // 5. Khởi tạo đối tượng Payment ở trạng thái PENDING
        Payment payment = Payment.builder()
                .booking(booking)
                .paymentMethod(request.getPaymentMethod())
                .paymentType(request.getPaymentType())
                .amount(request.getAmount())
                .status(PaymentStatus.PENDING)
                .transactionCode(generatedTxnCode)
                .note(request.getNote())
                .build();

        Payment savedPayment = paymentRepository.save(payment);
        log.info("Payment created successfully with id: {}, txnCode: {}", savedPayment.getId(), generatedTxnCode);

        // 6. Xử lý URL thanh toán (paymentUrl) nếu có cổng trực tuyến
        String paymentUrl = resolvePaymentUrl(savedPayment);

        return paymentMapper.toResponse(savedPayment, paymentUrl);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PaymentResponse> getPaymentsByBookingId(Integer bookingId, Long userId) {
        log.info("Customer user {} fetching payments for booking id: {}", userId, bookingId);
        // Xác thực quyền sở hữu đơn hàng (chống IDOR)
        findOwnedBooking(bookingId, userId);

        List<Payment> payments = paymentRepository.findAllByBookingIdOrderByCreatedAtDesc(bookingId);
        return payments.stream()
                .map(paymentMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public PaymentResponse getPaymentDetail(Integer id, Long userId) {
        log.info("Customer user {} fetching payment detail for id: {}", userId, id);

        Payment payment = paymentRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.ERR_PAYMENT_NOT_FOUND, id));

        // Kiểm tra quyền sở hữu đơn hàng tương ứng
        validatePaymentOwnership(payment, userId);

        String paymentUrl = resolvePaymentUrl(payment);
        return paymentMapper.toResponse(payment, paymentUrl);
    }

    @Override
    @Transactional
    public PaymentResponse cancelPayment(Integer id, Long userId) {
        log.info("Customer user {} requesting to cancel payment id: {}", userId, id);

        Payment payment = paymentRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.ERR_PAYMENT_NOT_FOUND, id));

        // Kiểm tra quyền sở hữu
        validatePaymentOwnership(payment, userId);

        // Chỉ cho phép hủy khi giao dịch còn đang chờ (PENDING)
        if (payment.getStatus() != PaymentStatus.PENDING) {
            log.warn("Cannot cancel payment id: {} with status: {}", id, payment.getStatus());
            throw new AppException(ErrorCode.ERR_PAYMENT_INVALID_STATUS, payment.getStatus());
        }

        payment.setStatus(PaymentStatus.CANCELLED);
        String cancelNote = (payment.getNote() != null && !payment.getNote().isEmpty())
                ? payment.getNote() + " | Khách hàng đã hủy giao dịch"
                : "Khách hàng đã hủy giao dịch";
        payment.setNote(cancelNote);

        Payment updatedPayment = paymentRepository.save(payment);
        log.info("Payment id: {} cancelled successfully by customer user: {}", id, userId);

        return paymentMapper.toResponse(updatedPayment);
    }

    /**
     * Tra cứu đơn hàng và xác thực quyền sở hữu của khách hàng hiện tại (Chống IDOR).
     */
    private Booking findOwnedBooking(Integer bookingId, Long currentUserId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new AppException(ErrorCode.ERR_BOOKING_NOT_FOUND, bookingId));

        if (booking.getCustomer() == null || !booking.getCustomer().getId().equals(currentUserId)) {
            log.warn("IDOR check failed: User {} does not own booking {}", currentUserId, bookingId);
            throw new AppException(ErrorCode.ERR_BOOKING_FORBIDDEN);
        }

        return booking;
    }

    /**
     * Xác thực quyền sở hữu của giao dịch thanh toán.
     */
    private void validatePaymentOwnership(Payment payment, Long currentUserId) {
        if (payment.getBooking() == null || payment.getBooking().getCustomer() == null
                || !payment.getBooking().getCustomer().getId().equals(currentUserId)) {
            log.warn("IDOR check failed: User {} does not own payment {}", currentUserId, payment.getId());
            throw new AppException(ErrorCode.ERR_BOOKING_FORBIDDEN);
        }
    }

    /**
     * Tự động sinh mã giao dịch tham chiếu dựa trên mã đơn và thời gian.
     */
    private String generateTransactionCode(Booking booking, PaymentType paymentType) {
        String prefix = paymentType == PaymentType.DEPOSIT ? "DEP" : "PAY";
        String bookingCode = booking.getBookingCode() != null ? booking.getBookingCode().replaceAll("[^a-zA-Z0-9]", "") : "BK";
        long timestampSuffix = System.currentTimeMillis() % 1000000;
        return String.format("%s-%s-%06d", prefix, bookingCode, timestampSuffix);
    }

    /**
     * Trả về URL thanh toán trực tuyến (nếu là cổng thanh toán hoặc ví điện tử).
     */
    private String resolvePaymentUrl(Payment payment) {
        if (payment.getStatus() != PaymentStatus.PENDING) {
            return null;
        }
        // Trường hợp tích hợp cổng trực tuyến (VNPay / MoMo) sẽ khởi tạo link tại đây
        // Mặc định hiện tại để null hoặc link tra cứu nếu chưa cấu hình cổng bên thứ 3
        return null;
    }
}

