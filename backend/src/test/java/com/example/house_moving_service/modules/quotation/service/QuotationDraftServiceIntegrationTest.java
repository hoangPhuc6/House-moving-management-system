package com.example.house_moving_service.modules.quotation.service;

import com.example.house_moving_service.modules.booking.entity.Booking;
import com.example.house_moving_service.modules.booking.repository.BookingRepository;
import com.example.house_moving_service.modules.identity.repository.UserRepository;
import com.example.house_moving_service.modules.quotation.dto.request.DraftItemSeed;
import com.example.house_moving_service.modules.quotation.dto.response.QuotationDraftDto;
import com.example.house_moving_service.modules.quotation.dto.response.QuotationItemDto;
import com.example.house_moving_service.modules.quotation.enums.QuotationStatus;
import com.example.house_moving_service.modules.quotation.repository.QuotationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Chạy trên PostgreSQL thật: kiểm tra mở bản nháp 2 lần liên tiếp (khảo sát lại) không vi phạm
 * uq_quotations_one_draft_per_booking — lỗi chỉ xuất hiện khi Hibernate thực sự flush xuống DB.
 */
@SpringBootTest
@Transactional
class QuotationDraftServiceIntegrationTest {

    @Autowired
    private QuotationDraftService quotationDraftService;

    @Autowired
    private QuotationRepository quotationRepository;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private UserRepository userRepository;

    private Booking booking;
    private Long moverId;

    @BeforeEach
    void setUp() {
        booking = bookingRepository.findAll(PageRequest.of(0, 1)).getContent().get(0);
        quotationRepository.deleteAll(quotationRepository.findAllByBookingIdOrderByVersionDesc(booking.getId()));
        quotationRepository.flush();
        moverId = userRepository.findByEmail("mover1@housemoving.vn").orElseThrow().getId();
    }

    @Test
    @DisplayName("Mở bản nháp lần 2 (khảo sát lại): bản 1 EXPIRED, bản 2 DRAFT, đồ đạc được copy")
    void testOpenDraftTwice_ExpiresPreviousAndCopiesItems() {
        QuotationDraftDto first = quotationDraftService.openDraftForSurvey(booking.getId(), 1, moverId, List.of(
                DraftItemSeed.builder().itemName("Tủ lạnh").quantity(1).isFragile(true).build(),
                DraftItemSeed.builder().itemName("Giường").quantity(2).build()));

        QuotationDraftDto second = quotationDraftService.openDraftForSurvey(booking.getId(), 2, moverId, List.of());

        assertThat(first.version()).isEqualTo(1);
        assertThat(second.version()).isEqualTo(2);
        assertThat(quotationRepository.findById(first.id()).orElseThrow().getStatus()).isEqualTo(QuotationStatus.EXPIRED);
        assertThat(quotationDraftService.findCurrentDraft(booking.getId()))
                .get().extracting(QuotationDraftDto::id).isEqualTo(second.id());

        List<QuotationItemDto> copied = quotationDraftService.listItems(second.id());
        assertThat(copied).extracting(QuotationItemDto::itemName).containsExactly("Tủ lạnh", "Giường");
        assertThat(copied).allSatisfy(item -> assertThat(item.unitPrice()).isEqualByComparingTo("0"));
    }
}
