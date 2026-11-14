package com.example.house_moving_service.modules.scheduling.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/** Danh sách xe của chuyến (ghi đè): xe không có trong danh sách bị hủy phân công. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AssignVehiclesRequest {

    @NotEmpty(message = "Phải chọn ít nhất 1 xe")
    @Size(max = 20, message = "Một chuyến tối đa 20 xe")
    private List<@NotNull(message = "ID xe không được để trống") Integer> vehicleIds;
}
