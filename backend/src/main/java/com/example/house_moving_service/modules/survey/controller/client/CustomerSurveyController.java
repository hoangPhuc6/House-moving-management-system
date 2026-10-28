package com.example.house_moving_service.modules.survey.controller.client;

import com.example.house_moving_service.common.dto.ApiResponse;
import com.example.house_moving_service.modules.survey.dto.response.CustomerSurveyResponse;
import com.example.house_moving_service.modules.survey.service.CustomerSurveyService;
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
@Tag(name = "Booking - Surveys", description = "Khách hàng xem lịch khảo sát của đơn mình")
@SecurityRequirement(name = "bearerAuth")
public class CustomerSurveyController {

    private final CustomerSurveyService customerSurveyService;

    @GetMapping("/{bookingId}/surveys")
    @Operation(
            summary = "Lịch khảo sát của đơn hàng",
            description = "Mọi lần khảo sát của đơn, mới nhất trước: ngày, khung giờ, nhân viên khảo sát, trạng thái. "
                    + "Muốn đổi lịch vui lòng liên hệ tổng đài / điều phối viên. Đơn không phải của mình → 403."
    )
    public ResponseEntity<ApiResponse<List<CustomerSurveyResponse>>> getSurveys(
            @Parameter(description = "ID đơn hàng", required = true) @PathVariable Integer bookingId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(ApiResponse.ok(customerSurveyService.getByBooking(bookingId, userDetails.getId())));
    }
}
