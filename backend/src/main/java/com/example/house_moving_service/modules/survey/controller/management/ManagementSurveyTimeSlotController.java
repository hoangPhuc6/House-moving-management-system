package com.example.house_moving_service.modules.survey.controller.management;

import com.example.house_moving_service.common.dto.ApiResponse;
import com.example.house_moving_service.modules.survey.dto.response.SurveyTimeSlotResponse;
import com.example.house_moving_service.modules.survey.service.SurveyTimeSlotService;
import com.example.house_moving_service.security.access.StaffOnly;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/management/survey-time-slots")
@StaffOnly
@RequiredArgsConstructor
@Tag(name = "Management - Survey Time Slots", description = "Khung giờ khảo sát đang dùng, để Quản trị viên & Điều phối viên chọn khi xếp lịch")
@SecurityRequirement(name = "bearerAuth")
public class ManagementSurveyTimeSlotController {

    private final SurveyTimeSlotService surveyTimeSlotService;

    @GetMapping
    @Operation(summary = "Danh sách khung giờ khảo sát đang dùng", description = "Chỉ trả khung giờ đang bật, sắp theo giờ bắt đầu.")
    public ResponseEntity<ApiResponse<List<SurveyTimeSlotResponse>>> getActiveSlots() {
        return ResponseEntity.ok(ApiResponse.success(surveyTimeSlotService.getAll(true)));
    }
}
