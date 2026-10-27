package com.example.house_moving_service.modules.pricing.dto.request;

import com.example.house_moving_service.modules.booking.dto.request.BookingItemRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
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
public class CreateQuoteRequest {
    @NotEmpty(message = "Danh sách dịch vụ không được để trống")
    @Valid
    private List<ServiceItemDto> serviceItems;


    @Valid
    private List<BookingItemRequest> items;

    @DecimalMin(value = "0.0", message = "Cự ly thực tế không được âm")
    private BigDecimal actualDistanceKm;

    /**
     * Voucher áp dụng cho báo giá: null = giữ voucher hiện tại của đơn,
     * chuỗi rỗng = gỡ voucher, mã khác voucher hiện tại = đổi sang voucher mới.
     */
    private String voucherCode;

    @Size(max = 1000, message = "Ghi chú báo giá không được vượt quá 1000 ký tự")
    private String note;
}
