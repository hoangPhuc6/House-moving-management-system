package com.example.house_moving_service.modules.survey.controller.admin;

import com.example.house_moving_service.common.dto.ApiResponse;
import com.example.house_moving_service.modules.survey.dto.request.SurveyTimeSlotRequest;
import com.example.house_moving_service.modules.survey.dto.response.SurveyTimeSlotResponse;
import com.example.house_moving_service.modules.survey.service.SurveyTimeSlotService;
import com.example.house_moving_service.security.access.AdminOnly;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/survey-time-slots")
@AdminOnly
@RequiredArgsConstructor
@Tag(name = "Admin - Survey Time Slots", description = "APIs cấu hình khung giờ khảo sát dành cho Quản trị viên")
@SecurityRequirement(name = "bearerAuth")
public class AdminSurveyTimeSlotController {

    private final SurveyTimeSlotService surveyTimeSlotService;

    @GetMapping
    @Operation(summary = "Xem khung giờ khảo sát", description = "activeOnly=true chỉ trả khung giờ đang dùng; mặc định trả tất cả, sắp theo giờ bắt đầu.")
    public ResponseEntity<ApiResponse<List<SurveyTimeSlotResponse>>> getAll(
            @RequestParam(required = false) Boolean activeOnly) {
        return ResponseEntity.ok(ApiResponse.success(surveyTimeSlotService.getAll(activeOnly)));
    }

    @PostMapping
    @Operation(summary = "Thêm khung giờ khảo sát", description = "Không được trùng hoặc chồng lấn với khung giờ đang dùng; chạm biên (10:00-12:00 và 12:00-13:00) được phép.")
    public ResponseEntity<ApiResponse<SurveyTimeSlotResponse>> create(@Valid @RequestBody SurveyTimeSlotRequest request) {
        SurveyTimeSlotResponse response = surveyTimeSlotService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Tạo khung giờ khảo sát thành công", response));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Cập nhật khung giờ khảo sát", description = "Không cho đổi giờ nếu khung giờ đang được dùng cho lịch khảo sát từ hôm nay trở đi.")
    public ResponseEntity<ApiResponse<SurveyTimeSlotResponse>> update(
            @PathVariable Integer id,
            @Valid @RequestBody SurveyTimeSlotRequest request) {
        SurveyTimeSlotResponse response = surveyTimeSlotService.update(id, request);
        return ResponseEntity.ok(ApiResponse.success("Cập nhật khung giờ khảo sát thành công", response));
    }

    @PatchMapping("/{id}/toggle")
    @Operation(summary = "Bật / tắt khung giờ khảo sát", description = "Tắt không ảnh hưởng lịch đã hẹn; bật lại phải không chồng lấn khung giờ đang dùng.")
    public ResponseEntity<ApiResponse<SurveyTimeSlotResponse>> toggle(@PathVariable Integer id) {
        SurveyTimeSlotResponse response = surveyTimeSlotService.toggle(id);
        return ResponseEntity.ok(ApiResponse.success("Đổi trạng thái khung giờ khảo sát thành công", response));
    }
}
