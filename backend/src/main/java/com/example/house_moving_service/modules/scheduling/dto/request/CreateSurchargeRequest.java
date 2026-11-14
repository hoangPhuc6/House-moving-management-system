package com.example.house_moving_service.modules.scheduling.dto.request;

import com.example.house_moving_service.modules.scheduling.enums.SurchargeType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Tạo khoản phụ phí. scheduleId chỉ dùng ở API điều phối (không bắt buộc); API hiện trường lấy chuyến theo đường dẫn
 * và bỏ qua field này. unitPrice chỉ nhập với WAITING_TIME / OTHER; serviceId chỉ gửi với EXTRA_ITEMS / DISASSEMBLY /
 * PACKING_MATERIAL; các loại còn lại hệ thống tự lấy giá theo mã dịch vụ (xem SurchargePricingResolver).
 * quantity là phần chênh lệch so với lúc khách khai (ví dụ khai tầng 3 có thang máy, thực tế không có → 2).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateSurchargeRequest {

    private Integer scheduleId;

    @NotNull(message = "Loại phụ phí không được để trống")
    private SurchargeType surchargeType;

    @NotBlank(message = "Mô tả phụ phí không được để trống")
    @Size(max = 5000, message = "Mô tả tối đa 5000 ký tự")
    private String description;

    @NotNull(message = "Số lượng không được để trống")
    @Min(value = 1, message = "Số lượng phải lớn hơn 0")
    private Integer quantity;

    private BigDecimal unitPrice;

    private Integer serviceId;

    private Integer incidentId;
}
