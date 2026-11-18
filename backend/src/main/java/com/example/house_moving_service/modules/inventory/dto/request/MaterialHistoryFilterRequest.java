package com.example.house_moving_service.modules.inventory.dto.request;

import com.example.house_moving_service.modules.inventory.enums.InventoryTransactionType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

/** Bộ lọc lịch sử giao dịch của một vật tư (query param), mọi field đều không bắt buộc. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MaterialHistoryFilterRequest {

    private InventoryTransactionType type;

    // Khoảng ngày tạo giao dịch (yyyy-MM-dd), tính cả ngày cuối
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate from;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate to;
}
