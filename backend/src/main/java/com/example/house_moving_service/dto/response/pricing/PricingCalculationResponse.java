package com.example.house_moving_service.dto.response.pricing;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PricingCalculationResponse {

    @Builder.Default
    private Boolean isEstimated = true;

    private BigDecimal calculatedDistanceKm;

    @Builder.Default
    private String currency = "VND";

    @Builder.Default
    private String disclaimer = "Bảng giá mang tính chất tham khảo dự kiến. Báo giá chính thức sẽ được xác nhận sau khi hoàn tất khảo sát.";

    private FinancialSummaryDto financialSummary;
    private CategoryFeesDto categoryFees;
    private List<BreakdownItemDto> breakdownItems;
    private AppliedVoucherDto appliedVoucher;
}

