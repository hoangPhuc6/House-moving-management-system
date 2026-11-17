package com.example.house_moving_service.modules.inventory.dto.request;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Hủy cấp phát: hoàn toàn bộ về kho, không tính tiền. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CancelAllocationRequest {

    // Ghi vào giao dịch RETURN
    @Size(max = 5000, message = "Ghi chú tối đa 5000 ký tự")
    private String note;
}
