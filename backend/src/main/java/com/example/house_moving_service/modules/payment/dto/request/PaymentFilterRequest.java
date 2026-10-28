package com.example.house_moving_service.modules.payment.dto.request;

import com.example.house_moving_service.modules.payment.enums.PaymentMethod;
import com.example.house_moving_service.modules.payment.enums.PaymentStatus;
import com.example.house_moving_service.modules.payment.enums.PaymentType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentFilterRequest {

    private Integer bookingId;

    private String bookingCode;

    private PaymentStatus status;

    private PaymentMethod paymentMethod;

    private PaymentType paymentType;

    private String transactionCode;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime fromDate;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime toDate;
}
