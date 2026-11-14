package com.example.house_moving_service.modules.scheduling.dto.response;

import com.example.house_moving_service.modules.scheduling.enums.SurchargeStatus;
import com.example.house_moving_service.modules.scheduling.enums.SurchargeType;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Khoản phụ phí trả cho khách: không có người đề xuất / duyệt / thu tiền, ghi chú duyệt hay id phiếu thu. */
@Builder
public record CustomerSurchargeResponse(
        Integer id,
        Integer scheduleId,
        SurchargeType surchargeType,
        String serviceName,
        String description,
        Integer quantity,
        BigDecimal unitPrice,
        BigDecimal amount,
        SurchargeStatus status,
        LocalDateTime sentToCustomerAt,
        LocalDateTime customerRespondedAt,
        String customerNote,
        boolean collected,
        LocalDateTime collectedAt
) {
}
