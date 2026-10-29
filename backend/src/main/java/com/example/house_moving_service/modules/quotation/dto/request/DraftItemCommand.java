package com.example.house_moving_service.modules.quotation.dto.request;

import lombok.Builder;

import java.math.BigDecimal;

/**
 * Dữ liệu thêm / sửa một dòng đồ đạc trong bản nháp báo giá (module khác gọi qua QuotationDraftService).
 * Cố ý không có field giá: bản nháp do khảo sát tạo luôn có giá = 0, giá do module Quotation tính sau.
 */
@Builder
public record DraftItemCommand(
        String itemName,
        Integer quantity,
        String unit,
        BigDecimal estimatedVolumeM3,
        Boolean isFragile,
        Boolean packingRequired,
        Boolean disassemblyRequired,
        String note
) {
}
