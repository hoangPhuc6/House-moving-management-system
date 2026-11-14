package com.example.house_moving_service.modules.scheduling.dto.response;

import lombok.Builder;

import java.math.BigDecimal;
import java.util.List;

/**
 * Phụ phí của đơn phía khách: khoản đang chờ xác nhận (WAITING_CUSTOMER), đã xác nhận (kèm đã thu tiền chưa),
 * đã từ chối. acceptedTotal là tổng đã được cộng vào tiền đơn; uncollectedTotal là phần đã xác nhận nhưng chưa trả.
 */
@Builder
public record CustomerSurchargeSummaryResponse(
        int waitingCount,
        BigDecimal waitingTotal,
        BigDecimal acceptedTotal,
        BigDecimal uncollectedTotal,
        List<CustomerSurchargeResponse> surcharges
) {
}
