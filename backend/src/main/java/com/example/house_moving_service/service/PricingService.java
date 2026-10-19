package com.example.house_moving_service.service;

import com.example.house_moving_service.dto.request.pricing.PricingCalculationRequest;
import com.example.house_moving_service.dto.response.pricing.PricingCalculationResponse;

public interface PricingService {

    /**
     * Cỗ máy tính toán giá dùng chung cho toàn bộ hệ thống
     *
     * @param request Chứa thông tin lịch trình, lộ trình, danh sách dịch vụ và mã voucher
     * @return PricingCalculationResponse Bảng phân rã chi phí chi tiết, tóm tắt tài chính và voucher áp dụng
     */
    PricingCalculationResponse calculatePrice(PricingCalculationRequest request);

    /**
     * Cỗ máy tính toán giá có định danh người dùng phục vụ truy vết và auditing
     *
     * @param request Chứa thông tin lịch trình, lộ trình, danh sách dịch vụ và mã voucher
     * @param userId  ID người dùng yêu cầu tính giá (nếu đã đăng nhập)
     * @return PricingCalculationResponse Bảng phân rã chi phí chi tiết, tóm tắt tài chính và voucher áp dụng
     */
    PricingCalculationResponse calculatePrice(PricingCalculationRequest request, Long userId);
}

