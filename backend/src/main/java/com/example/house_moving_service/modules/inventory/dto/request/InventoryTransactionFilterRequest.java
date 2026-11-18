package com.example.house_moving_service.modules.inventory.dto.request;

import com.example.house_moving_service.modules.inventory.enums.InventoryTransactionType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

/** Bộ lọc tra cứu giao dịch kho toàn kho (query param), mọi field đều không bắt buộc. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InventoryTransactionFilterRequest {

    private Integer materialId;

    private InventoryTransactionType type;

    private Integer bookingId;

    private Integer scheduleId;

    // Khoảng ngày tạo giao dịch (yyyy-MM-dd), tính cả ngày cuối
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate from;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate to;
}
