package com.example.house_moving_service.modules.scheduling.dto.request;

import com.example.house_moving_service.modules.scheduling.enums.StaffAssignmentRole;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * Danh sách nhân viên của chuyến (ghi đè): người không có trong danh sách bị hủy phân công.
 * Bắt buộc đúng 1 SUPERVISOR (kiểm tra ở service).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AssignStaffRequest {

    @NotEmpty(message = "Phải chọn ít nhất 1 nhân viên")
    @Size(max = 50, message = "Một chuyến tối đa 50 nhân viên")
    private List<@Valid @NotNull(message = "Dòng phân công không được để trống") Item> assignments;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Item {

        @NotNull(message = "ID nhân viên không được để trống")
        private Long staffId;

        @NotNull(message = "Vai trò không được để trống (SUPERVISOR / MOVER / DRIVER)")
        private StaffAssignmentRole role;
    }
}
