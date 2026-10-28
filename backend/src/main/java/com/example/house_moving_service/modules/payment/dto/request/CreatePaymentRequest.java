package com.example.house_moving_service.modules.payment.dto.request;

import com.example.house_moving_service.modules.payment.enums.PaymentMethod;
import com.example.house_moving_service.modules.payment.enums.PaymentType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
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
public class CreatePaymentRequest {

    @NotNull(message = "ID đơn hàng (bookingId) không được để trống")
    private Integer bookingId;

    @NotNull(message = "Phương thức thanh toán không được để trống")
    private PaymentMethod paymentMethod;

    @NotNull(message = "Loại thanh toán không được để trống")
    private PaymentType paymentType;

    @NotNull(message = "Số tiền thanh toán không được để trống")
    @DecimalMin(value = "1000.00", message = "Số tiền thanh toán tối thiểu là 1,000 VNĐ")
    private BigDecimal amount;

    @Size(max = 500, message = "Ghi chú không được vượt quá 500 ký tự")
    private String note;
}
