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
public class BreakdownItemDto {

    private Integer serviceId;
    private Integer serviceTypeId;
    private String serviceTypeName;
    private String serviceName;
    private BigDecimal quantity;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private BigDecimal unitPrice;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private BigDecimal totalPrice;

    private String note;
}

