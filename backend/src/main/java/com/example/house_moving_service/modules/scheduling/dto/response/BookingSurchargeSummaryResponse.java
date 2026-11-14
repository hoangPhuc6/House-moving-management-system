package com.example.house_moving_service.modules.scheduling.dto.response;

import com.example.house_moving_service.modules.scheduling.enums.SurchargeStatus;
import lombok.Builder;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Toàn bộ phụ phí của đơn cho điều phối: tổng tiền theo từng trạng thái, tổng đã xác nhận, tiền chưa thu,
 * và id các khoản đang chặn bắt đầu chuyến (PROPOSED / WAITING_CUSTOMER / REJECTED_BY_CUSTOMER).
 */
@Builder
public record BookingSurchargeSummaryResponse(
        Integer bookingId,
        String bookingCode,
        Map<SurchargeStatus, BigDecimal> totalsByStatus,
        BigDecimal acceptedTotal,
        BigDecimal uncollectedTotal,
        List<Integer> blockingSurchargeIds,
        List<SurchargeResponse> surcharges
) {
}
