package com.example.house_moving_service.modules.payment.dto.response;

import com.example.house_moving_service.modules.payment.enums.PaymentMethod;
import com.example.house_moving_service.modules.payment.enums.PaymentStatus;
import com.example.house_moving_service.modules.payment.enums.PaymentType;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PaymentSummaryResponse {

    private Integer id;

    private Integer bookingId;

    private String bookingCode;

    private PaymentMethod paymentMethod;

    private PaymentType paymentType;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private BigDecimal amount;

    private PaymentStatus status;

    private String transactionCode;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime paymentTime;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime createdAt;
}
