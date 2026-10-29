package com.example.house_moving_service.modules.quotation.dto.request;

import lombok.Builder;

import java.math.BigDecimal;

/**
 * Một món đồ khách đã khai (booking_items) dùng làm dữ liệu ban đầu khi mở bản nháp lần đầu.
 * Giữ bookingItemId để dòng báo giá còn liên kết về món đồ gốc.
 */
@Builder
public record DraftItemSeed(
        Integer bookingItemId,
        String itemName,
        Integer quantity,
        BigDecimal estimatedVolumeM3,
        Boolean isFragile,
        Boolean packingRequired,
        Boolean disassemblyRequired,
        String note
) {
}
