package com.example.house_moving_service.modules.inventory.controller.client;

import com.example.house_moving_service.common.dto.ApiResponse;
import com.example.house_moving_service.modules.inventory.dto.response.CustomerMaterialChargeResponse;
import com.example.house_moving_service.modules.inventory.service.MaterialAllocationService;
import com.example.house_moving_service.security.access.CustomerOnly;
import com.example.house_moving_service.security.userdetails.CustomUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/bookings")
@CustomerOnly
@RequiredArgsConstructor
@Tag(name = "Booking - Materials", description = "Khách hàng xem vật tư đã tính tiền trong đơn của mình")
@SecurityRequirement(name = "bearerAuth")
public class CustomerMaterialController {

    private final MaterialAllocationService materialAllocationService;

    @GetMapping("/{bookingId}/materials")
    @Operation(
            summary = "Vật tư đã tính tiền trong đơn",
            description = "Chỉ chủ đơn, sai → 403 mã 4004. Chỉ các dòng đã chốt (SETTLED) có thành tiền > 0: tên vật tư, "
                    + "đơn vị, số đã dùng, đơn giá, thành tiền, thời điểm chốt."
    )
    public ResponseEntity<ApiResponse<List<CustomerMaterialChargeResponse>>> getMaterialCharges(
            @Parameter(description = "ID đơn hàng", required = true) @PathVariable Integer bookingId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(ApiResponse.ok(
                materialAllocationService.getCustomerCharges(bookingId, userDetails.getId())));
    }
}
