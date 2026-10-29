package com.example.house_moving_service.modules.quotation.service.impl;

import com.example.house_moving_service.common.exception.AppException;
import com.example.house_moving_service.common.exception.ErrorCode;
import com.example.house_moving_service.modules.booking.service.BookingWorkflowService;
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
import com.example.house_moving_service.modules.quotation.service.QuotationDraftService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
@Slf4j
@RequiredArgsConstructor
public class QuotationDraftServiceImpl implements QuotationDraftService {

    private static final Set<QuotationStatus> OPEN_STATUSES = EnumSet.of(QuotationStatus.DRAFT, QuotationStatus.SENT);
    private static final String DEFAULT_UNIT = "cái";

    private final QuotationRepository quotationRepository;
    private final QuotationItemRepository quotationItemRepository;
    private final BookingWorkflowService bookingWorkflowService;
    private final UserLookupService userLookupService;
    private final QuotationMapper quotationMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public QuotationDraftDto openDraftForSurvey(Integer bookingId, Integer surveyId, Long actorUserId,
                                                List<DraftItemSeed> seedFromBookingItems) {
        if (hasAcceptedQuotation(bookingId)) {
            throw new AppException(ErrorCode.ERR_QUOTATION_ALREADY_ACCEPTED);
        }

        // Kiểm tra booking tồn tại (ném ERR_BOOKING_NOT_FOUND) và lấy reference để gắn quan hệ
        var booking = bookingWorkflowService.getReference(bookingId);

        // Đồ đạc của báo giá gần nhất (khảo sát lại thì copy từ đây, nhân viên không phải nhập lại)
        List<QuotationItem> previousItems = quotationRepository.findTopByBookingIdOrderByVersionDesc(bookingId)
                .map(previous -> quotationItemRepository.findAllByQuotationIdOrderByIdAsc(previous.getId()))
                .orElse(Collections.emptyList());


        expire(bookingId, OPEN_STATUSES, "Mở bản nháp mới cho khảo sát #" + surveyId);

        Quotation draft = quotationRepository.save(Quotation.builder()
                .booking(booking)
                .version(quotationRepository.findMaxVersion(bookingId) + 1)
                .status(QuotationStatus.DRAFT)
                .note("[SURVEY#" + surveyId + "]")
                .createdBy(actorUserId != null ? userLookupService.getUserReference(actorUserId) : null)
                .build());

        List<QuotationItem> items = !previousItems.isEmpty()
                ? previousItems.stream().map(previous -> copyItem(previous, draft)).toList()
                : seedItems(seedFromBookingItems, draft);
        quotationItemRepository.saveAll(items);

        log.info("Opened draft quotation id: {} (version {}) for booking id: {}, survey id: {}, items: {}",
                draft.getId(), draft.getVersion(), bookingId, surveyId, items.size());
        return quotationMapper.toDraftDto(draft);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<QuotationDraftDto> findCurrentDraft(Integer bookingId) {
        return quotationRepository.findByBookingIdAndStatus(bookingId, QuotationStatus.DRAFT)
                .map(quotationMapper::toDraftDto);
    }

    @Override
    @Transactional(readOnly = true)
    public List<QuotationDraftDto> findHistory(Integer bookingId) {
        return quotationRepository.findAllByBookingIdOrderByVersionDesc(bookingId).stream()
                .map(quotationMapper::toDraftDto)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<QuotationItemDto> listItems(Integer quotationId) {
        if (!quotationRepository.existsById(quotationId)) {
            throw new AppException(ErrorCode.ERR_QUOTATION_NOT_FOUND, quotationId);
        }
        return quotationItemRepository.findAllByQuotationIdOrderByIdAsc(quotationId).stream()
                .map(quotationMapper::toItemDto)
                .toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public QuotationItemDto addItem(Integer quotationId, DraftItemCommand command) {
        Quotation draft = findEditableDraft(quotationId);
        validate(command);

        QuotationItem item = QuotationItem.builder()
                .quotation(draft)
                .unitPrice(BigDecimal.ZERO)
                .lineAmount(BigDecimal.ZERO)
                .build();
        apply(command, item);

        return quotationMapper.toItemDto(quotationItemRepository.save(item));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public QuotationItemDto updateItem(Integer quotationId, Integer itemId, DraftItemCommand command) {
        findEditableDraft(quotationId);
        validate(command);

        QuotationItem item = findItem(quotationId, itemId);
        apply(command, item);

        return quotationMapper.toItemDto(quotationItemRepository.save(item));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteItem(Integer quotationId, Integer itemId) {
        findEditableDraft(quotationId);
        quotationItemRepository.delete(findItem(quotationId, itemId));
    }

    @Override
    @Transactional(readOnly = true)
    public long countItems(Integer quotationId) {
        return quotationItemRepository.countByQuotationId(quotationId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void expireDraft(Integer bookingId, String reason) {
        expire(bookingId, EnumSet.of(QuotationStatus.DRAFT), reason);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void expireOpenQuotations(Integer bookingId, String reason) {
        expire(bookingId, OPEN_STATUSES, reason);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasAcceptedQuotation(Integer bookingId) {
        return quotationRepository.existsByBookingIdAndStatus(bookingId, QuotationStatus.ACCEPTED);
    }

    private void expire(Integer bookingId, Set<QuotationStatus> statuses, String reason) {
        List<Quotation> quotations = quotationRepository.findAllByBookingIdAndStatusIn(bookingId, statuses);
        if (quotations.isEmpty()) {
            return;
        }
        for (Quotation quotation : quotations) {
            quotation.setStatus(QuotationStatus.EXPIRED);
            quotation.setNote(appendNote(quotation.getNote(), "[EXPIRED] " + reason));
        }
        quotationRepository.saveAllAndFlush(quotations);
        log.info("Expired {} quotation(s) of booking id: {}", quotations.size(), bookingId);
    }

    private Quotation findEditableDraft(Integer quotationId) {
        Quotation quotation = quotationRepository.findById(quotationId)
                .orElseThrow(() -> new AppException(ErrorCode.ERR_QUOTATION_NOT_FOUND, quotationId));
        if (quotation.getStatus() != QuotationStatus.DRAFT) {
            throw new AppException(ErrorCode.ERR_QUOTATION_NOT_EDITABLE);
        }
        return quotation;
    }

    private QuotationItem findItem(Integer quotationId, Integer itemId) {
        return quotationItemRepository.findByIdAndQuotationId(itemId, quotationId)
                .orElseThrow(() -> new AppException(ErrorCode.ERR_QUOTATION_ITEM_NOT_FOUND, itemId));
    }

    private void validate(DraftItemCommand command) {
        if (command == null || command.itemName() == null || command.itemName().isBlank()) {
            throw new AppException(ErrorCode.ERR_INVALID_INPUT, "Tên đồ đạc không được để trống");
        }
        if (command.quantity() == null || command.quantity() < 1) {
            throw new AppException(ErrorCode.ERR_INVALID_INPUT, "Số lượng phải lớn hơn 0");
        }
    }

    // Không đụng tới unitPrice / lineAmount: khảo sát không được ghi giá
    private void apply(DraftItemCommand command, QuotationItem item) {
        item.setItemName(command.itemName().trim());
        item.setQuantity(command.quantity());
        item.setUnit(command.unit() == null || command.unit().isBlank() ? DEFAULT_UNIT : command.unit().trim());
        item.setEstimatedVolumeM3(command.estimatedVolumeM3());
        item.setIsFragile(Boolean.TRUE.equals(command.isFragile()));
        item.setPackingRequired(Boolean.TRUE.equals(command.packingRequired()));
        item.setDisassemblyRequired(Boolean.TRUE.equals(command.disassemblyRequired()));
        item.setNote(command.note());
    }

    private QuotationItem copyItem(QuotationItem source, Quotation draft) {
        return QuotationItem.builder()
                .quotation(draft)
                .bookingItem(source.getBookingItem())
                .itemName(source.getItemName())
                .quantity(source.getQuantity())
                .unit(source.getUnit())
                .unitPrice(BigDecimal.ZERO)
                .lineAmount(BigDecimal.ZERO)
                .estimatedVolumeM3(source.getEstimatedVolumeM3())
                .isFragile(source.getIsFragile())
                .packingRequired(source.getPackingRequired())
                .disassemblyRequired(source.getDisassemblyRequired())
                .note(source.getNote())
                .build();
    }

    private List<QuotationItem> seedItems(List<DraftItemSeed> seeds, Quotation draft) {
        if (seeds == null) {
            return Collections.emptyList();
        }
        return seeds.stream()
                .map(seed -> QuotationItem.builder()
                        .quotation(draft)
                        .bookingItem(seed.bookingItemId() != null
                                ? bookingWorkflowService.getItemReference(seed.bookingItemId())
                                : null)
                        .itemName(seed.itemName())
                        .quantity(seed.quantity() != null && seed.quantity() > 0 ? seed.quantity() : 1)
                        .unit(DEFAULT_UNIT)
                        .unitPrice(BigDecimal.ZERO)
                        .lineAmount(BigDecimal.ZERO)
                        .estimatedVolumeM3(seed.estimatedVolumeM3())
                        .isFragile(Boolean.TRUE.equals(seed.isFragile()))
                        .packingRequired(Boolean.TRUE.equals(seed.packingRequired()))
                        .disassemblyRequired(Boolean.TRUE.equals(seed.disassemblyRequired()))
                        .note(seed.note())
                        .build())
                .toList();
    }

    private String appendNote(String current, String line) {
        return current == null || current.isBlank() ? line : current + "\n" + line;
    }
}
