package com.example.house_moving_service.modules.quotation.service;

import com.example.house_moving_service.common.exception.AppException;
import com.example.house_moving_service.common.exception.ErrorCode;
import com.example.house_moving_service.modules.booking.entity.Booking;
import com.example.house_moving_service.modules.booking.entity.BookingItem;
import com.example.house_moving_service.modules.booking.service.BookingWorkflowService;
import com.example.house_moving_service.modules.identity.entity.User;
import com.example.house_moving_service.modules.identity.service.UserLookupService;
import com.example.house_moving_service.modules.quotation.dto.request.DraftItemCommand;
import com.example.house_moving_service.modules.quotation.dto.request.DraftItemSeed;
import com.example.house_moving_service.modules.quotation.dto.response.QuotationDraftDto;
import com.example.house_moving_service.modules.quotation.dto.response.QuotationItemDto;
import com.example.house_moving_service.modules.quotation.entity.Quotation;
import com.example.house_moving_service.modules.quotation.entity.QuotationItem;
import com.example.house_moving_service.modules.quotation.enums.QuotationStatus;
import com.example.house_moving_service.modules.quotation.mapper.QuotationMapper;
import com.example.house_moving_service.modules.quotation.repository.QuotationItemRepository;
import com.example.house_moving_service.modules.quotation.repository.QuotationRepository;
import com.example.house_moving_service.modules.quotation.service.impl.QuotationDraftServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QuotationDraftServiceTest {

    @Mock
    private QuotationRepository quotationRepository;
    @Mock
    private QuotationItemRepository quotationItemRepository;
    @Mock
    private BookingWorkflowService bookingWorkflowService;
    @Mock
    private UserLookupService userLookupService;

    private QuotationDraftServiceImpl quotationDraftService;

    private Booking booking;

    @BeforeEach
    void setUp() {
        quotationDraftService = new QuotationDraftServiceImpl(
                quotationRepository, quotationItemRepository, bookingWorkflowService, userLookupService, new QuotationMapper());
        booking = Booking.builder().id(10).build();
    }

    private void stubSaveDraftWithId(int id) {
        when(quotationRepository.save(any(Quotation.class))).thenAnswer(inv -> {
            Quotation q = inv.getArgument(0);
            q.setId(id);
            return q;
        });
    }

    @SuppressWarnings("unchecked")
    private List<QuotationItem> captureSavedItems() {
        ArgumentCaptor<List<QuotationItem>> captor = ArgumentCaptor.forClass(List.class);
        verify(quotationItemRepository).saveAll(captor.capture());
        return captor.getValue();
    }

    @Test
    @DisplayName("openDraftForSurvey: Lần đầu copy từ booking_items, version 1, giá 0, note có [SURVEY#id]")
    void testOpenDraft_FirstTime_SeedsFromBookingItems() {
        BookingItem fridgeRef = BookingItem.builder().id(7).build();
        User mover = User.builder().id(3L).build();
        when(quotationRepository.existsByBookingIdAndStatus(10, QuotationStatus.ACCEPTED)).thenReturn(false);
        when(bookingWorkflowService.getReference(10)).thenReturn(booking);
        when(quotationRepository.findTopByBookingIdOrderByVersionDesc(10)).thenReturn(Optional.empty());
        when(quotationRepository.findAllByBookingIdAndStatusIn(eq(10), any())).thenReturn(Collections.emptyList());
        when(quotationRepository.findMaxVersion(10)).thenReturn(0);
        when(userLookupService.getUserReference(3L)).thenReturn(mover);
        when(bookingWorkflowService.getItemReference(7)).thenReturn(fridgeRef);
        stubSaveDraftWithId(1);

        List<DraftItemSeed> seeds = List.of(
                DraftItemSeed.builder().bookingItemId(7).itemName("Tủ lạnh").quantity(1)
                        .estimatedVolumeM3(BigDecimal.valueOf(1.2)).isFragile(true).build(),
                DraftItemSeed.builder().bookingItemId(null).itemName("Thùng sách").quantity(null).build());

        QuotationDraftDto dto = quotationDraftService.openDraftForSurvey(10, 5, 3L, seeds);

        assertThat(dto.version()).isEqualTo(1);
        assertThat(dto.status()).isEqualTo(QuotationStatus.DRAFT);
        assertThat(dto.note()).isEqualTo("[SURVEY#5]");
        verify(quotationRepository, never()).saveAllAndFlush(anyList());

        List<QuotationItem> items = captureSavedItems();
        assertThat(items).hasSize(2);
        assertThat(items.get(0).getBookingItem()).isSameAs(fridgeRef);
        assertThat(items.get(0).getIsFragile()).isTrue();
        assertThat(items.get(1).getBookingItem()).isNull();
        assertThat(items.get(1).getQuantity()).isEqualTo(1);
        assertThat(items).allSatisfy(item -> {
            assertThat(item.getUnitPrice()).isEqualByComparingTo("0");
            assertThat(item.getLineAmount()).isEqualByComparingTo("0");
            assertThat(item.getUnit()).isEqualTo("cái");
        });
    }

    @Test
    @DisplayName("openDraftForSurvey: Khảo sát lại copy từ version trước, version tăng, bản cũ EXPIRED")
    void testOpenDraft_Resurvey_CopiesPreviousVersion() {
        Quotation previous = Quotation.builder().id(1).booking(booking).version(1)
                .status(QuotationStatus.DRAFT).note("[SURVEY#5]").build();
        QuotationItem previousItem = QuotationItem.builder().id(100).quotation(previous)
                .bookingItem(BookingItem.builder().id(7).build())
                .itemName("Tủ lạnh 2 cánh").quantity(1).unit("cái")
                .isFragile(true).packingRequired(false).disassemblyRequired(false).build();

        when(quotationRepository.existsByBookingIdAndStatus(10, QuotationStatus.ACCEPTED)).thenReturn(false);
        when(bookingWorkflowService.getReference(10)).thenReturn(booking);
        when(quotationRepository.findTopByBookingIdOrderByVersionDesc(10)).thenReturn(Optional.of(previous));
        when(quotationItemRepository.findAllByQuotationIdOrderByIdAsc(1)).thenReturn(List.of(previousItem));
        when(quotationRepository.findAllByBookingIdAndStatusIn(eq(10), any())).thenReturn(List.of(previous));
        when(quotationRepository.findMaxVersion(10)).thenReturn(1);
        stubSaveDraftWithId(2);

        QuotationDraftDto dto = quotationDraftService.openDraftForSurvey(10, 6, null, List.of(
                DraftItemSeed.builder().bookingItemId(7).itemName("Không được dùng").quantity(1).build()));

        assertThat(dto.version()).isEqualTo(2);
        assertThat(previous.getStatus()).isEqualTo(QuotationStatus.EXPIRED);
        assertThat(previous.getNote()).contains("[EXPIRED] Mở bản nháp mới cho khảo sát #6");
        verify(quotationRepository).saveAllAndFlush(List.of(previous));

        List<QuotationItem> items = captureSavedItems();
        assertThat(items).singleElement().satisfies(item -> {
            assertThat(item.getItemName()).isEqualTo("Tủ lạnh 2 cánh");
            assertThat(item.getBookingItem().getId()).isEqualTo(7);
            assertThat(item.getQuotation().getId()).isEqualTo(2);
        });
        verify(bookingWorkflowService, never()).getItemReference(anyInt());
    }

    @Test
    @DisplayName("openDraftForSurvey: Đã có báo giá ACCEPTED thì ném ERR_QUOTATION_ALREADY_ACCEPTED")
    void testOpenDraft_AlreadyAccepted() {
        when(quotationRepository.existsByBookingIdAndStatus(10, QuotationStatus.ACCEPTED)).thenReturn(true);

        assertThatThrownBy(() -> quotationDraftService.openDraftForSurvey(10, 5, 3L, List.of()))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ERR_QUOTATION_ALREADY_ACCEPTED);
        verify(quotationRepository, never()).save(any());
    }

    @Test
    @DisplayName("addItem: Thêm dòng vào bản nháp, giá luôn 0, đơn vị mặc định 'cái'")
    void testAddItem_Success() {
        Quotation draft = Quotation.builder().id(1).booking(booking).status(QuotationStatus.DRAFT).build();
        when(quotationRepository.findById(1)).thenReturn(Optional.of(draft));
        when(quotationItemRepository.save(any(QuotationItem.class))).thenAnswer(inv -> inv.getArgument(0));

        QuotationItemDto dto = quotationDraftService.addItem(1, DraftItemCommand.builder()
                .itemName("  Máy giặt ").quantity(2).disassemblyRequired(true).build());

        assertThat(dto.itemName()).isEqualTo("Máy giặt");
        assertThat(dto.quantity()).isEqualTo(2);
        assertThat(dto.unit()).isEqualTo("cái");
        assertThat(dto.unitPrice()).isEqualByComparingTo("0");
        assertThat(dto.lineAmount()).isEqualByComparingTo("0");
        assertThat(dto.disassemblyRequired()).isTrue();
        assertThat(dto.isFragile()).isFalse();
        assertThat(dto.bookingItemId()).isNull();
    }

    @Test
    @DisplayName("addItem: Báo giá đã EXPIRED thì ném ERR_QUOTATION_NOT_EDITABLE")
    void testAddItem_NotDraft() {
        Quotation expired = Quotation.builder().id(1).status(QuotationStatus.EXPIRED).build();
        when(quotationRepository.findById(1)).thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> quotationDraftService.addItem(1,
                DraftItemCommand.builder().itemName("Bàn").quantity(1).build()))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ERR_QUOTATION_NOT_EDITABLE);
        verify(quotationItemRepository, never()).save(any());
    }

    @Test
    @DisplayName("addItem: Số lượng < 1 hoặc thiếu tên thì ném ERR_INVALID_INPUT")
    void testAddItem_InvalidCommand() {
        Quotation draft = Quotation.builder().id(1).status(QuotationStatus.DRAFT).build();
        when(quotationRepository.findById(1)).thenReturn(Optional.of(draft));

        assertThatThrownBy(() -> quotationDraftService.addItem(1,
                DraftItemCommand.builder().itemName("Bàn").quantity(0).build()))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ERR_INVALID_INPUT);
        assertThatThrownBy(() -> quotationDraftService.addItem(1,
                DraftItemCommand.builder().itemName(" ").quantity(1).build()))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ERR_INVALID_INPUT);
    }

    @Test
    @DisplayName("updateItem: Dòng không thuộc báo giá thì ném ERR_QUOTATION_ITEM_NOT_FOUND")
    void testUpdateItem_ItemOfOtherQuotation() {
        Quotation draft = Quotation.builder().id(1).status(QuotationStatus.DRAFT).build();
        when(quotationRepository.findById(1)).thenReturn(Optional.of(draft));
        when(quotationItemRepository.findByIdAndQuotationId(999, 1)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> quotationDraftService.updateItem(1, 999,
                DraftItemCommand.builder().itemName("Bàn").quantity(1).build()))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ERR_QUOTATION_ITEM_NOT_FOUND);
    }

    @Test
    @DisplayName("updateItem: Sửa dòng không làm thay đổi giá")
    void testUpdateItem_DoesNotTouchPrice() {
        Quotation draft = Quotation.builder().id(1).status(QuotationStatus.DRAFT).build();
        QuotationItem item = QuotationItem.builder().id(100).quotation(draft).itemName("Tủ").quantity(1)
                .unitPrice(BigDecimal.ZERO).lineAmount(BigDecimal.ZERO).build();
        when(quotationRepository.findById(1)).thenReturn(Optional.of(draft));
        when(quotationItemRepository.findByIdAndQuotationId(100, 1)).thenReturn(Optional.of(item));
        when(quotationItemRepository.save(item)).thenReturn(item);

        QuotationItemDto dto = quotationDraftService.updateItem(1, 100,
                DraftItemCommand.builder().itemName("Tủ quần áo 3 cánh").quantity(2).unit("bộ").build());

        assertThat(dto.itemName()).isEqualTo("Tủ quần áo 3 cánh");
        assertThat(dto.unit()).isEqualTo("bộ");
        assertThat(dto.unitPrice()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("deleteItem: Xóa dòng thuộc bản nháp")
    void testDeleteItem_Success() {
        Quotation draft = Quotation.builder().id(1).status(QuotationStatus.DRAFT).build();
        QuotationItem item = QuotationItem.builder().id(100).quotation(draft).build();
        when(quotationRepository.findById(1)).thenReturn(Optional.of(draft));
        when(quotationItemRepository.findByIdAndQuotationId(100, 1)).thenReturn(Optional.of(item));

        quotationDraftService.deleteItem(1, 100);

        verify(quotationItemRepository).delete(item);
    }

    @Test
    @DisplayName("expireDraft: Không có bản nháp thì không làm gì, không ném lỗi")
    void testExpireDraft_NoDraft() {
        when(quotationRepository.findAllByBookingIdAndStatusIn(eq(10), any())).thenReturn(Collections.emptyList());

        quotationDraftService.expireDraft(10, "Khảo sát thất bại");

        verify(quotationRepository, never()).saveAllAndFlush(anyList());
    }

    @Test
    @DisplayName("listItems: Báo giá không tồn tại thì ném ERR_QUOTATION_NOT_FOUND")
    void testListItems_NotFound() {
        when(quotationRepository.existsById(999)).thenReturn(false);

        assertThatThrownBy(() -> quotationDraftService.listItems(999))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ERR_QUOTATION_NOT_FOUND);
    }
}
