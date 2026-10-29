package com.example.house_moving_service.modules.pricing.scheduler;

import com.example.house_moving_service.modules.pricing.enums.QuotationStatus;
import com.example.house_moving_service.modules.pricing.repository.QuotationRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.annotation.Scheduled;

import java.lang.reflect.Method;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QuotationExpiryJobTest {

    @Mock
    private QuotationRepository quotationRepository;

    @InjectMocks
    private QuotationExpiryJob quotationExpiryJob;

    @Test
    @DisplayName("Chuyển các báo giá SENT quá hạn sang EXPIRED với mốc thời gian hiện tại")
    void expireOverdueQuotations_UpdatesSentToExpiredUsingCurrentTime() {
        when(quotationRepository.bulkUpdateStatusForOverdue(any(), any(), any())).thenReturn(3);

        LocalDateTime before = LocalDateTime.now();
        quotationExpiryJob.expireOverdueQuotations();
        LocalDateTime after = LocalDateTime.now();

        ArgumentCaptor<LocalDateTime> nowCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(quotationRepository, times(1)).bulkUpdateStatusForOverdue(
                eq(QuotationStatus.SENT), eq(QuotationStatus.EXPIRED), nowCaptor.capture());
        assertThat(nowCaptor.getValue()).isBetween(before, after);
    }

    @Test
    @DisplayName("Không có báo giá quá hạn thì job vẫn chạy bình thường")
    void expireOverdueQuotations_NoOverdueQuotations_DoesNothingElse() {
        when(quotationRepository.bulkUpdateStatusForOverdue(any(), any(), any())).thenReturn(0);

        quotationExpiryJob.expireOverdueQuotations();

        verify(quotationRepository, times(1)).bulkUpdateStatusForOverdue(
                eq(QuotationStatus.SENT), eq(QuotationStatus.EXPIRED), any(LocalDateTime.class));
    }

    @Test
    @DisplayName("Lỗi DB được ném ra để transaction rollback")
    void expireOverdueQuotations_RepositoryFails_PropagatesException() {
        when(quotationRepository.bulkUpdateStatusForOverdue(any(), any(), any()))
                .thenThrow(new RuntimeException("DB down"));

        assertThatThrownBy(() -> quotationExpiryJob.expireOverdueQuotations())
                .isInstanceOf(RuntimeException.class)
                .hasMessage("DB down");
    }

    @Test
    @DisplayName("Job được lên lịch bằng cron cấu hình, mặc định mỗi 10 phút")
    void expireOverdueQuotations_IsScheduledWithConfigurableCron() throws NoSuchMethodException {
        Method method = QuotationExpiryJob.class.getMethod("expireOverdueQuotations");
        Scheduled scheduled = method.getAnnotation(Scheduled.class);

        assertThat(scheduled).isNotNull();
        assertThat(scheduled.cron()).isEqualTo("${app.pricing.quote-expiry-cron:0 */10 * * * *}");
    }
}
