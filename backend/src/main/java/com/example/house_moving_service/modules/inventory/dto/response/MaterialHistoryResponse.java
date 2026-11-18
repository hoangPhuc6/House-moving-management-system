package com.example.house_moving_service.modules.inventory.dto.response;

import com.example.house_moving_service.common.dto.PageResponse;
import com.example.house_moving_service.modules.inventory.enums.InventoryTransactionType;
import lombok.Builder;

import java.util.Map;

/** Lịch sử nhập xuất của một vật tư: thông tin + tồn hiện tại, tổng theo loại trong khoảng lọc, trang giao dịch. */
@Builder
public record MaterialHistoryResponse(
        MaterialResponse material,
        // Tổng quantity theo từng loại giao dịch trong khoảng lọc; đủ mọi loại, loại không có giao dịch = 0
        Map<InventoryTransactionType, Long> totals,
        PageResponse<InventoryTransactionResponse> transactions
) {
}
