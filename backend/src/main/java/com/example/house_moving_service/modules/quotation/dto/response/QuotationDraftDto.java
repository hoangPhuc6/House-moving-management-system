package com.example.house_moving_service.modules.quotation.dto.response;

import com.example.house_moving_service.modules.quotation.enums.QuotationStatus;
import lombok.Builder;

import java.time.LocalDateTime;

/** Thông tin chung một báo giá (không kèm các dòng), trả cho module khác qua QuotationDraftService. */
@Builder
public record QuotationDraftDto(
        Integer id,
        Integer bookingId,
        Integer version,
        QuotationStatus status,
        String note,
        LocalDateTime createdAt
) {
}
