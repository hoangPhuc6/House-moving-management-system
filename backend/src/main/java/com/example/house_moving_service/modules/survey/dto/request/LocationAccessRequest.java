package com.example.house_moving_service.modules.survey.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Điều kiện ra vào thực tế tại một đầu (điểm lấy hoặc điểm giao hàng). */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LocationAccessRequest {

    @NotNull(message = "Số tầng không được để trống")
    @Min(value = 0, message = "Số tầng không được âm")
    @Max(value = 100, message = "Số tầng tối đa 100")
    private Integer floorNumber;

    @NotNull(message = "Vui lòng cho biết có thang máy hay không")
    private Boolean hasElevator;

    @NotNull(message = "Cự ly đi bộ trong hẻm không được để trống")
    @Min(value = 0, message = "Cự ly không được âm")
    @Max(value = 5000, message = "Cự ly tối đa 5000m")
    private Integer walkingDistanceMeters;

    @Size(max = 500, message = "Ghi chú tối đa 500 ký tự")
    private String extraNote;
}
