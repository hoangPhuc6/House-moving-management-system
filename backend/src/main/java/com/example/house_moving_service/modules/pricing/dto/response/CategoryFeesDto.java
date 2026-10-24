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
public class CategoryFeesDto {

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private BigDecimal baseTransportFee;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private BigDecimal laborFee;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private BigDecimal technicalFee;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private BigDecimal materialFee;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private BigDecimal terrainSurchargeFee;
}

