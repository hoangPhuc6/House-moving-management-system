package com.example.house_moving_service.modules.survey.controller.field;

import com.example.house_moving_service.common.dto.ApiResponse;
import com.example.house_moving_service.modules.survey.dto.response.FieldSurveyResponse;
import com.example.house_moving_service.modules.survey.dto.response.SurveySummaryResponse;
import com.example.house_moving_service.modules.survey.enums.SurveyStatus;
import com.example.house_moving_service.modules.survey.service.FieldSurveyService;
import com.example.house_moving_service.security.access.MoverOnly;
import com.example.house_moving_service.security.userdetails.CustomUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/field/surveys")
@MoverOnly
@RequiredArgsConstructor
@Tag(name = "Field - Surveys", description = "Lịch khảo sát của nhân viên hiện trường (MOVER) được phân công")
@SecurityRequirement(name = "bearerAuth")
public class FieldSurveyController {

    private final FieldSurveyService fieldSurveyService;

    @GetMapping
    @Operation(
            summary = "Lịch khảo sát của tôi",
            description = "Mặc định từ hôm nay đến 7 ngày sau, tối đa 31 ngày mỗi lần; lọc thêm theo trạng thái. "
                    + "Sắp theo ngày rồi giờ bắt đầu."
    )
    public ResponseEntity<ApiResponse<List<SurveySummaryResponse>>> getMySurveys(
            @Parameter(description = "Từ ngày (yyyy-MM-dd), mặc định hôm nay")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @Parameter(description = "Đến ngày (yyyy-MM-dd), mặc định từ ngày + 7")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) SurveyStatus status,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(ApiResponse.ok(fieldSurveyService.getMySurveys(userDetails.getId(), from, to, status)));
    }

    @GetMapping("/{id}")
    @Operation(
            summary = "Chi tiết một lịch khảo sát của tôi",
            description = "Kèm liên hệ, tuyến đường (điều kiện ra vào), đồ khách đã khai và đồ đạc đang ghi trên bản nháp. "
                    + "Lịch không phải của mình → 403."
    )
    public ResponseEntity<ApiResponse<FieldSurveyResponse>> getMySurvey(
            @Parameter(description = "ID lịch khảo sát", required = true) @PathVariable Integer id,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(ApiResponse.ok(fieldSurveyService.getMySurvey(id, userDetails.getId())));
    }
}
