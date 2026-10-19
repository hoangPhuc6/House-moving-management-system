package com.example.house_moving_service.dto.response.pricing;

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
public class AppliedVoucherDto {

    private Integer voucherId;
    private String code;
    private String title;
    private String discountType;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private BigDecimal discountValue;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private BigDecimal discountAmount;
}

