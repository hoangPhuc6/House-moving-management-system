package com.example.house_moving_service.modules.inventory.dto.request;

import com.example.house_moving_service.modules.inventory.enums.MaterialCategory;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/** Admin thêm vật tư vào danh mục. Tồn ban đầu luôn = 0; muốn có hàng phải nhập kho. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateMaterialRequest {

    // Được chuẩn hóa (bỏ khoảng trắng hai đầu, viết hoa) trước khi lưu, vd: BOX-L; không sửa được sau khi tạo
    @NotBlank(message = "Mã vật tư không được để trống")
    @Size(max = 50, message = "Mã vật tư tối đa 50 ký tự")
    @Pattern(regexp = "^\\s*[0-9A-Za-z][0-9A-Za-z_\\-.]*\\s*$",
            message = "Mã vật tư chỉ gồm chữ không dấu, số, dấu chấm, gạch ngang, gạch dưới")
    private String code;

    @NotBlank(message = "Tên vật tư không được để trống")
    @Size(max = 255, message = "Tên vật tư tối đa 255 ký tự")
    private String name;

    // CONSUMABLE / TOOL; không sửa được sau khi tạo
    @NotNull(message = "Nhóm vật tư không được để trống")
    private MaterialCategory category;

    @NotBlank(message = "Đơn vị tính không được để trống")
    @Size(max = 30, message = "Đơn vị tính tối đa 30 ký tự")
    private String unit;

    // Không truyền thì 0 (miễn phí). TOOL nên để 0 vì không bán cho khách
    @DecimalMin(value = "0", message = "Đơn giá không được âm")
    @Digits(integer = 10, fraction = 2, message = "Đơn giá không hợp lệ")
    private BigDecimal unitPrice;

    // Không truyền thì 0 (không cảnh báo tồn thấp)
    @Min(value = 0, message = "Tồn tối thiểu không được âm")
    private Integer minStock;

    @Size(max = 5000, message = "Mô tả tối đa 5000 ký tự")
    private String description;
}
