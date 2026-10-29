package com.example.house_moving_service.modules.quotation.repository;

import com.example.house_moving_service.modules.booking.entity.Booking;
import com.example.house_moving_service.modules.booking.repository.BookingRepository;
import com.example.house_moving_service.modules.quotation.entity.Quotation;
import com.example.house_moving_service.modules.quotation.enums.QuotationStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class QuotationRepositoryTest {

    @Autowired
    private QuotationRepository quotationRepository;

    @Autowired
    private BookingRepository bookingRepository;

    private Booking booking;

    @BeforeEach
    void setUp() {
        // Dùng một booking seed V9; mỗi test tự rollback
        booking = bookingRepository.findAll(PageRequest.of(0, 1)).getContent().get(0);
        quotationRepository.deleteAll(quotationRepository.findAllByBookingIdOrderByVersionDesc(booking.getId()));
        quotationRepository.flush();
    }

    private Quotation quotation(int version, QuotationStatus status) {
        return Quotation.builder().booking(booking).version(version).status(status).build();
    }

    @Test
    @DisplayName("findMaxVersion: Booking chưa có báo giá trả 0, có rồi trả version lớn nhất")
    void testFindMaxVersion() {
        assertThat(quotationRepository.findMaxVersion(booking.getId())).isZero();

        quotationRepository.saveAndFlush(quotation(1, QuotationStatus.EXPIRED));
        quotationRepository.saveAndFlush(quotation(2, QuotationStatus.DRAFT));

        assertThat(quotationRepository.findMaxVersion(booking.getId())).isEqualTo(2);
    }

    @Test
    @DisplayName("Tiền mặc định 0 khi tạo bản nháp không có giá")
    void testDefaultAmountsAreZero() {
        Quotation saved = quotationRepository.saveAndFlush(quotation(1, QuotationStatus.DRAFT));

        assertThat(saved.getAmount()).isEqualByComparingTo("0");
        assertThat(saved.getDiscountAmount()).isEqualByComparingTo("0");
        assertThat(saved.getFinalAmount()).isEqualByComparingTo("0");
        assertThat(saved.getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("Nhiều báo giá EXPIRED + 1 DRAFT cùng booking là hợp lệ")
    void testOneDraftWithExpiredVersions_Allowed() {
        quotationRepository.saveAndFlush(quotation(1, QuotationStatus.EXPIRED));
        quotationRepository.saveAndFlush(quotation(2, QuotationStatus.EXPIRED));
        quotationRepository.saveAndFlush(quotation(3, QuotationStatus.DRAFT));

        assertThat(quotationRepository.findByBookingIdAndStatus(booking.getId(), QuotationStatus.DRAFT))
                .get().extracting(Quotation::getVersion).isEqualTo(3);
    }

    @Test
    @DisplayName("2 bản DRAFT cùng booking vi phạm uq_quotations_one_draft_per_booking")
    void testTwoDrafts_Violation() {
        quotationRepository.saveAndFlush(quotation(1, QuotationStatus.DRAFT));

        assertThatThrownBy(() -> quotationRepository.saveAndFlush(quotation(2, QuotationStatus.DRAFT)))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uq_quotations_one_draft_per_booking");
    }

    @Test
    @DisplayName("Trùng (booking_id, version) vi phạm uq_quotations_booking_version")
    void testDuplicateVersion_Violation() {
        quotationRepository.saveAndFlush(quotation(1, QuotationStatus.EXPIRED));

        assertThatThrownBy(() -> quotationRepository.saveAndFlush(quotation(1, QuotationStatus.SENT)))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uq_quotations_booking_version");
    }
}
