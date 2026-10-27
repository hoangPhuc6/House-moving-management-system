package com.example.house_moving_service.modules.quotation.service;

import com.example.house_moving_service.modules.quotation.dto.request.DraftItemCommand;
import com.example.house_moving_service.modules.quotation.dto.request.DraftItemSeed;
import com.example.house_moving_service.modules.quotation.dto.response.QuotationDraftDto;
import com.example.house_moving_service.modules.quotation.dto.response.QuotationItemDto;

import java.util.List;
import java.util.Optional;

/**
 * Thao tác trên bản nháp báo giá dành cho module survey: mở bản nháp khi bắt đầu khảo sát,
 * sửa đồ đạc trong lúc khảo sát, bỏ bản nháp khi khảo sát thất bại / khảo sát lại.
 * Tính giá, gửi khách, chấp nhận... thuộc service khác của module quotation (làm sau).
 * Không kiểm tra "ai được sửa": module gọi tự kiểm tra trước khi gọi.
 */
public interface QuotationDraftService {

    /**
     * Mở bản nháp version mới cho booking: các báo giá DRAFT / SENT cũ chuyển EXPIRED;
     * đồ đạc copy từ báo giá gần nhất nếu có, nếu không thì từ seedFromBookingItems.
     * Ném ERR_QUOTATION_ALREADY_ACCEPTED nếu booking đã có báo giá được chấp nhận.
     */
    QuotationDraftDto openDraftForSurvey(Integer bookingId, Integer surveyId, Long actorUserId,
                                         List<DraftItemSeed> seedFromBookingItems);

    Optional<QuotationDraftDto> findCurrentDraft(Integer bookingId);

    /** Mọi version của booking, mới nhất trước. */
    List<QuotationDraftDto> findHistory(Integer bookingId);

    List<QuotationItemDto> listItems(Integer quotationId);

    /** Ném ERR_QUOTATION_NOT_EDITABLE nếu báo giá không còn DRAFT. */
    QuotationItemDto addItem(Integer quotationId, DraftItemCommand command);

    QuotationItemDto updateItem(Integer quotationId, Integer itemId, DraftItemCommand command);

    void deleteItem(Integer quotationId, Integer itemId);

    long countItems(Integer quotationId);

    /** Bản nháp hiện tại (nếu có) chuyển EXPIRED. Không có bản nháp thì không làm gì. */
    void expireDraft(Integer bookingId, String reason);

    /** Mọi báo giá DRAFT / SENT của booking chuyển EXPIRED (dùng khi khảo sát lại). */
    void expireOpenQuotations(Integer bookingId, String reason);

    boolean hasAcceptedQuotation(Integer bookingId);
}
