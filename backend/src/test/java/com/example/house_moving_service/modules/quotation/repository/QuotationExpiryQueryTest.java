package com.example.house_moving_service.modules.quotation.repository;

import com.example.house_moving_service.modules.booking.entity.Booking;
import com.example.house_moving_service.modules.booking.enums.BookingStatus;
import com.example.house_moving_service.modules.booking.repository.BookingRepository;
import com.example.house_moving_service.modules.identity.entity.Customer;
import com.example.house_moving_service.modules.identity.entity.User;
import com.example.house_moving_service.modules.identity.repository.CustomerRepository;
import com.example.house_moving_service.modules.identity.repository.UserRepository;
import com.example.house_moving_service.modules.quotation.entity.Quotation;
import com.example.house_moving_service.modules.quotation.enums.QuotationStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class QuotationExpiryQueryTest {

    @Autowired
    private QuotationRepository quotationRepository;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CustomerRepository customerRepository;

    private Booking booking;
    private int nextVersion;

    @BeforeEach
    void setUp() {
        User user = userRepository.save(User.builder()
                .email("test_quote_expiry_" + System.currentTimeMillis() + "@gmail.com")
                .password("$2a$10$dummyHashPassword123")
                .isActive(true)
                .build());

        Customer customer = customerRepository.save(Customer.builder()
                .user(user)
                .fullName("Khách Hàng Báo Giá")
                .phoneNumber("0903333333")
                .build());

        booking = bookingRepository.save(Booking.builder()
                .bookingCode("BKG-QEXP-" + System.currentTimeMillis())
                .customer(customer)
                .totalAmount(new BigDecimal("1000000.00"))
                .discountAmount(BigDecimal.ZERO)
                .status(BookingStatus.QUOTED)
                .contactName("Anh Test")
                .contactPhone("0903333333")
                .build());

        nextVersion = 1;
    }

    @Test
    @DisplayName("Chỉ báo giá SENT đã quá hạn bị chuyển sang EXPIRED")
    void bulkUpdateStatusForOverdue_OnlyExpiresOverdueSentQuotations() {
        LocalDateTime now = LocalDateTime.now();

        Quotation overdueSent = saveQuotation(QuotationStatus.SENT, now.minusDays(1));
        Quotation validSent = saveQuotation(QuotationStatus.SENT, now.plusDays(1));
        Quotation noDeadlineSent = saveQuotation(QuotationStatus.SENT, null);
        Quotation overdueRejected = saveQuotation(QuotationStatus.REJECTED, now.minusDays(1));
        Quotation overdueAccepted = saveQuotation(QuotationStatus.ACCEPTED, now.minusDays(1));

        int updated = quotationRepository.bulkUpdateStatusForOverdue(
                QuotationStatus.SENT, QuotationStatus.EXPIRED, now);

        assertThat(updated).isEqualTo(1);
        assertThat(statusOf(overdueSent)).isEqualTo(QuotationStatus.EXPIRED);
        assertThat(statusOf(validSent)).isEqualTo(QuotationStatus.SENT);
        assertThat(statusOf(noDeadlineSent)).isEqualTo(QuotationStatus.SENT);
        assertThat(statusOf(overdueRejected)).isEqualTo(QuotationStatus.REJECTED);
        assertThat(statusOf(overdueAccepted)).isEqualTo(QuotationStatus.ACCEPTED);
    }

    @Test
    @DisplayName("Báo giá hết hạn đúng mốc thời gian quét thì chưa bị chuyển (so sánh strict <)")
    void bulkUpdateStatusForOverdue_ValidUntilEqualsNow_NotExpired() {
        LocalDateTime now = LocalDateTime.now().withNano(0);
        Quotation boundary = saveQuotation(QuotationStatus.SENT, now);

        int updated = quotationRepository.bulkUpdateStatusForOverdue(
                QuotationStatus.SENT, QuotationStatus.EXPIRED, now);

        assertThat(updated).isZero();
        assertThat(statusOf(boundary)).isEqualTo(QuotationStatus.SENT);
    }

    @Test
    @DisplayName("Chạy lại lần hai không cập nhật thêm báo giá nào (idempotent)")
    void bulkUpdateStatusForOverdue_RunTwice_IsIdempotent() {
        LocalDateTime now = LocalDateTime.now();
        saveQuotation(QuotationStatus.SENT, now.minusHours(1));

        int firstRun = quotationRepository.bulkUpdateStatusForOverdue(
                QuotationStatus.SENT, QuotationStatus.EXPIRED, now);
        int secondRun = quotationRepository.bulkUpdateStatusForOverdue(
                QuotationStatus.SENT, QuotationStatus.EXPIRED, now);

        assertThat(firstRun).isEqualTo(1);
        assertThat(secondRun).isZero();
    }

    private Quotation saveQuotation(QuotationStatus status, LocalDateTime validUntil) {
        return quotationRepository.save(Quotation.builder()
                .booking(booking)
                .version(nextVersion++)
                .amount(new BigDecimal("1000000.00"))
                .discountAmount(BigDecimal.ZERO)
                .finalAmount(new BigDecimal("1000000.00"))
                .status(status)
                .validUntil(validUntil)
                .build());
    }

    private QuotationStatus statusOf(Quotation quotation) {
        // Query @Modifying đã clear persistence context nên findById đọc lại trạng thái mới từ DB
        return quotationRepository.findById(quotation.getId()).orElseThrow().getStatus();
    }
}
