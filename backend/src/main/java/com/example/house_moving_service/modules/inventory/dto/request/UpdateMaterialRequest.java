package com.example.house_moving_service.modules.inventory.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
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
 * Admin sửa thông tin vật tư (ghi đè toàn bộ các field dưới đây). Không có code, category, quantityOnHand:
 * truyền lên cũng bị bỏ qua. Đổi unitPrice chỉ áp dụng cho lần cấp phát sau, dòng đã cấp giữ giá cũ.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateMaterialRequest {

    @NotBlank(message = "Tên vật tư không được để trống")
    @Size(max = 255, message = "Tên vật tư tối đa 255 ký tự")
    private String name;

    @NotBlank(message = "Đơn vị tính không được để trống")
    @Size(max = 30, message = "Đơn vị tính tối đa 30 ký tự")
    private String unit;

    @NotNull(message = "Đơn giá không được để trống")
    @DecimalMin(value = "0", message = "Đơn giá không được âm")
    @Digits(integer = 10, fraction = 2, message = "Đơn giá không hợp lệ")
    private BigDecimal unitPrice;

    @NotNull(message = "Tồn tối thiểu không được để trống")
    @Min(value = 0, message = "Tồn tối thiểu không được âm")
    private Integer minStock;

    // null hoặc rỗng thì xóa mô tả
    @Size(max = 5000, message = "Mô tả tối đa 5000 ký tự")
    private String description;
}
