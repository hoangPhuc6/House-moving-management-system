package com.example.house_moving_service.modules.survey.controller.field;

import com.example.house_moving_service.common.dto.ApiResponse;
import com.example.house_moving_service.modules.quotation.dto.response.QuotationItemDto;
import com.example.house_moving_service.modules.survey.dto.request.CompleteSurveyRequest;
import com.example.house_moving_service.modules.survey.dto.request.FailSurveyRequest;
import com.example.house_moving_service.modules.survey.dto.request.RouteAccessRequest;
import com.example.house_moving_service.modules.survey.dto.request.SurveyItemRequest;
import com.example.house_moving_service.modules.survey.dto.response.FieldSurveyResponse;
import com.example.house_moving_service.modules.survey.dto.response.SurveyAttachmentResponse;
import com.example.house_moving_service.modules.survey.dto.response.SurveySummaryResponse;
import com.example.house_moving_service.modules.survey.enums.SurveyStatus;
import com.example.house_moving_service.modules.survey.service.FieldSurveyService;
import com.example.house_moving_service.modules.survey.service.SurveyAttachmentService;
import com.example.house_moving_service.security.access.MoverOnly;
import com.example.house_moving_service.security.userdetails.CustomUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

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
    private final SurveyAttachmentService surveyAttachmentService;

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

    @PatchMapping("/{id}/start")
    @Operation(
            summary = "Bắt đầu khảo sát",
            description = "Lịch phải CONFIRMED và của mình; chỉ trong ngày khảo sát, sớm nhất 60 phút (cấu hình) trước giờ hẹn, "
                    + "bắt đầu muộn không bị chặn. Ghi thời gian bắt đầu thực tế, đơn chuyển SURVEYING và mở bản nháp đồ đạc "
                    + "từ những gì khách đã khai."
    )
    public ResponseEntity<ApiResponse<FieldSurveyResponse>> startSurvey(
            @Parameter(description = "ID lịch khảo sát", required = true) @PathVariable Integer id,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(ApiResponse.success("Đã bắt đầu khảo sát", fieldSurveyService.start(id, userDetails.getId())));
    }

    @PatchMapping("/{id}/complete")
    @Operation(
            summary = "Hoàn tất khảo sát",
            description = "Lịch phải đang khảo sát (IN_PROGRESS) và bản nháp có ít nhất 1 món đồ. Ghi thời gian kết thúc thực tế; "
                    + "body không bắt buộc, resultNote là nhận xét chung được ghi vào ghi chú của lịch. Đơn giữ SURVEYING chờ báo giá."
    )
    public ResponseEntity<ApiResponse<FieldSurveyResponse>> completeSurvey(
            @Parameter(description = "ID lịch khảo sát", required = true) @PathVariable Integer id,
            @Valid @RequestBody(required = false) CompleteSurveyRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        FieldSurveyResponse response = fieldSurveyService.complete(id, request, userDetails.getId());
        return ResponseEntity.ok(ApiResponse.success("Đã hoàn tất khảo sát", response));
    }

    @PatchMapping("/{id}/fail")
    @Operation(
            summary = "Báo không khảo sát được",
            description = "Đã tới nơi / đang khảo sát nhưng không làm được (khách vắng, không vào được nhà...). Lịch phải "
                    + "CONFIRMED / IN_PROGRESS → cần khảo sát lại; bản nháp hết hiệu lực; đơn về chờ khảo sát để xếp lịch mới."
    )
    public ResponseEntity<ApiResponse<FieldSurveyResponse>> failSurvey(
            @Parameter(description = "ID lịch khảo sát", required = true) @PathVariable Integer id,
            @Valid @RequestBody FailSurveyRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        FieldSurveyResponse response = fieldSurveyService.fail(id, request, userDetails.getId());
        return ResponseEntity.ok(ApiResponse.success("Đã ghi nhận không khảo sát được", response));
    }

    @PostMapping(value = "/{id}/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
            summary = "Tải lên tệp đính kèm",
            description = "Lịch phải đang khảo sát (IN_PROGRESS). Ảnh jpg/png/webp/heic ≤ 10MB, video mp4/mov ≤ 50MB, PDF ≤ 10MB; "
                    + "tối đa 50 tệp mỗi lịch, trong đó tối đa 5 video. Field multipart: file."
    )
    public ResponseEntity<ApiResponse<SurveyAttachmentResponse>> uploadAttachment(
            @Parameter(description = "ID lịch khảo sát", required = true) @PathVariable Integer id,
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        SurveyAttachmentResponse response = surveyAttachmentService.upload(id, file, userDetails.getId(), false);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Đã tải lên tệp đính kèm", response));
    }

    @GetMapping("/{id}/attachments")
    @Operation(summary = "Danh sách tệp đính kèm", description = "accessUrl có hạn 15 phút, gọi lại để lấy URL mới.")
    public ResponseEntity<ApiResponse<List<SurveyAttachmentResponse>>> listAttachments(
            @Parameter(description = "ID lịch khảo sát", required = true) @PathVariable Integer id,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(ApiResponse.ok(surveyAttachmentService.list(id, userDetails.getId(), false)));
    }

    @DeleteMapping("/{id}/attachments/{attachmentId}")
    @Operation(summary = "Xóa tệp đính kèm", description = "Chỉ tệp do mình tải lên, khi lịch chưa hoàn tất.")
    public ResponseEntity<ApiResponse<Void>> deleteAttachment(
            @Parameter(description = "ID lịch khảo sát", required = true) @PathVariable Integer id,
            @Parameter(description = "ID tệp đính kèm", required = true) @PathVariable Integer attachmentId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        surveyAttachmentService.delete(id, attachmentId, userDetails.getId(), false);
        return ResponseEntity.ok(ApiResponse.success("Đã xóa tệp đính kèm"));
    }

    @PutMapping("/{id}/route-access")
    @Operation(
            summary = "Cập nhật điều kiện ra vào (tầng, thang máy, cự ly hẻm)",
            description = "Lịch phải đang khảo sát (IN_PROGRESS). Ghi đè giá trị khách khai ở cả điểm lấy và điểm giao hàng; "
                    + "giá trị cũ → mới được ghi vào ghi chú của lịch. Không có gì thay đổi thì không ghi gì."
    )
    public ResponseEntity<ApiResponse<FieldSurveyResponse>> updateRouteAccess(
            @Parameter(description = "ID lịch khảo sát", required = true) @PathVariable Integer id,
            @Valid @RequestBody RouteAccessRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        FieldSurveyResponse response = fieldSurveyService.updateRouteAccess(id, request, userDetails.getId());
        return ResponseEntity.ok(ApiResponse.success("Đã cập nhật điều kiện ra vào", response));
    }

    @PostMapping("/{id}/items")
    @Operation(summary = "Thêm đồ đạc", description = "Lịch phải đang khảo sát (IN_PROGRESS). Không có giá. Dòng thêm mới là đồ phát sinh tại nhà, "
            + "không gắn với đồ khách khai (bookingItemId = null).")
    public ResponseEntity<ApiResponse<QuotationItemDto>> addItem(
            @Parameter(description = "ID lịch khảo sát", required = true) @PathVariable Integer id,
            @Valid @RequestBody SurveyItemRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        QuotationItemDto item = fieldSurveyService.addItem(id, request, userDetails.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Đã thêm đồ đạc", item));
    }

    @PutMapping("/{id}/items/{itemId}")
    @Operation(summary = "Sửa đồ đạc", description = "Sửa một dòng trên bản nháp; không làm thay đổi đồ khách đã khai.")
    public ResponseEntity<ApiResponse<QuotationItemDto>> updateItem(
            @Parameter(description = "ID lịch khảo sát", required = true) @PathVariable Integer id,
            @Parameter(description = "ID dòng đồ đạc", required = true) @PathVariable Integer itemId,
            @Valid @RequestBody SurveyItemRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        QuotationItemDto item = fieldSurveyService.updateItem(id, itemId, request, userDetails.getId());
        return ResponseEntity.ok(ApiResponse.success("Đã cập nhật đồ đạc", item));
    }

    @DeleteMapping("/{id}/items/{itemId}")
    @Operation(summary = "Xóa đồ đạc", description = "Xóa một dòng trên bản nháp; không làm thay đổi đồ khách đã khai.")
    public ResponseEntity<ApiResponse<Void>> deleteItem(
            @Parameter(description = "ID lịch khảo sát", required = true) @PathVariable Integer id,
            @Parameter(description = "ID dòng đồ đạc", required = true) @PathVariable Integer itemId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        fieldSurveyService.deleteItem(id, itemId, userDetails.getId());
        return ResponseEntity.ok(ApiResponse.success("Đã xóa đồ đạc"));
    }
}
