package com.example.house_moving_service.modules.scheduling.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Điều phối viên chốt đề xuất rồi gửi khách. Mọi field không bắt buộc: không gửi thì giữ nguyên.
 * unitPrice chỉ sửa được với WAITING_TIME / OTHER.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SendSurchargeRequest {

    @Min(value = 1, message = "Số lượng phải lớn hơn 0")
    private Integer quantity;

    private BigDecimal unitPrice;

    @Size(max = 2000, message = "Ghi chú duyệt tối đa 2000 ký tự")
    private String reviewNote;
}
