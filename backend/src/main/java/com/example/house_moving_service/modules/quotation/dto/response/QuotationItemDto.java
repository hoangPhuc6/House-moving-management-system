package com.example.house_moving_service.modules.quotation.dto.response;

import lombok.Builder;

import java.math.BigDecimal;

/** Một dòng báo giá, trả cho module khác qua QuotationDraftService. */
@Builder
public record QuotationItemDto(
        Integer id,
        Integer quotationId,
        Integer bookingItemId,
        String itemName,
        Integer quantity,
        String unit,
        BigDecimal unitPrice,
        BigDecimal lineAmount,
        BigDecimal estimatedVolumeM3,
        Boolean isFragile,
        Boolean packingRequired,
        Boolean disassemblyRequired,
        String note
) {
}
