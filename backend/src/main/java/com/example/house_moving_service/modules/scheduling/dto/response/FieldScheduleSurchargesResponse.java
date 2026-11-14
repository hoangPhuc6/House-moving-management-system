package com.example.house_moving_service.modules.scheduling.dto.response;

import lombok.Builder;

import java.math.BigDecimal;
import java.util.List;

/**
 * Phụ phí liên quan tới một chuyến (khoản gắn chuyến và khoản cấp đơn không gắn chuyến), cho đội hiện trường biết
 * khách đã xác nhận chưa và đã được bắt đầu chuyến chưa (startBlocked / blockingSurchargeIds tính trên toàn đơn).
 */
@Builder
public record FieldScheduleSurchargesResponse(
        Integer scheduleId,
        Integer bookingId,
        boolean startBlocked,
        List<Integer> blockingSurchargeIds,
        BigDecimal acceptedTotal,
        BigDecimal uncollectedTotal,
        List<SurchargeResponse> surcharges
) {
}
