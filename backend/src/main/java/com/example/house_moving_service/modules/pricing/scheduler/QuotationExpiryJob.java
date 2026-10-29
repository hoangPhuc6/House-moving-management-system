package com.example.house_moving_service.modules.pricing.scheduler;

import com.example.house_moving_service.modules.pricing.enums.QuotationStatus;
import com.example.house_moving_service.modules.pricing.repository.QuotationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Định kỳ chuyển các báo giá đang chờ khách phản hồi (SENT) đã quá hạn valid_until sang EXPIRED.
 * Đơn hàng giữ nguyên QUOTED để nhân viên có thể lập báo giá phiên bản mới.
 * Câu UPDATE idempotent nên chạy song song trên nhiều instance vẫn an toàn.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class QuotationExpiryJob {

    private final QuotationRepository quotationRepository;

    @Scheduled(cron = "${app.pricing.quote-expiry-cron:0 */10 * * * *}")
    @Transactional(rollbackFor = Exception.class)
    public void expireOverdueQuotations() {
        int expiredCount = quotationRepository.bulkUpdateStatusForOverdue(
                QuotationStatus.SENT, QuotationStatus.EXPIRED, LocalDateTime.now());
        if (expiredCount > 0) {
            log.info("Expired {} overdue quotation(s)", expiredCount);
        }
    }
}
