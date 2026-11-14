package com.example.house_moving_service.modules.scheduling.dto.response;

import com.example.house_moving_service.modules.scheduling.enums.SurchargeStatus;
import com.example.house_moving_service.modules.scheduling.enums.SurchargeType;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Khoản phụ phí đầy đủ, cho hiện trường và điều phối. requestedBy / reviewedBy / collectedBy là id tài khoản. */
@Builder
public record SurchargeResponse(
        Integer id,
        Integer bookingId,
        String bookingCode,
        Integer scheduleId,
        Integer incidentId,
        Integer serviceId,
        String serviceName,
        SurchargeType surchargeType,
        String description,
        Integer quantity,
        BigDecimal unitPrice,
        BigDecimal amount,
        SurchargeStatus status,
        Long requestedBy,
        LocalDateTime requestedAt,
        Long reviewedBy,
        LocalDateTime reviewedAt,
        String reviewNote,
        LocalDateTime sentToCustomerAt,
        LocalDateTime customerRespondedAt,
        String customerNote,
        boolean collected,
        Long collectedBy,
        LocalDateTime collectedAt,
        Integer paymentId,
        LocalDateTime updatedAt
) {
}
