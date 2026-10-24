package com.example.house_moving_service.modules.pricing.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FinancialSummaryDto {

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private BigDecimal subtotal;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private BigDecimal discountAmount;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private BigDecimal finalTotal;
}

