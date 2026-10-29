package com.example.house_moving_service.modules.pricing.service;

import com.example.house_moving_service.modules.pricing.dto.PricingInput;
import com.example.house_moving_service.modules.pricing.dto.request.PricingCalculationRequest;
import com.example.house_moving_service.modules.pricing.dto.response.PricingCalculationResponse;

public interface PricingService {

    /**
     * Tính toán giá dùng chung cho toàn bộ hệ thống
     *
     * @param request Chứa thông tin lịch trình, lộ trình, danh sách dịch vụ và mã voucher
     * @return PricingCalculationResponse Bảng phân rã chi phí chi tiết, tóm tắt tài chính và voucher áp dụng
     */
    PricingCalculationResponse calculatePrice(PricingCalculationRequest request);

    /**
     * Tính toán giá có định danh người dùng phục vụ truy vết và auditing
     *
     * @param request Chứa thông tin lịch trình, lộ trình, danh sách dịch vụ và mã voucher
     * @param userId  ID người dùng yêu cầu tính giá (nếu đã đăng nhập)
     * @return PricingCalculationResponse Bảng phân rã chi phí chi tiết, tóm tắt tài chính và voucher áp dụng
     */
    PricingCalculationResponse calculatePrice(PricingCalculationRequest request, Long userId);

    /**
     * Tính giá cho báo giá chính thức (module Quotation), sau khi khảo sát xong.
     * Không kiểm tra lịch, không gọi bản đồ: khoảng cách và điều kiện ra vào do bên gọi truyền vào
     * (thường từ booking_routes đã được MOVER cập nhật). Bên gọi tự kiểm tra khảo sát đã hoàn tất.
     *
     * @param input Khoảng cách, điều kiện ra vào 2 điểm, dịch vụ nhân viên chọn, mã voucher
     * @return Kết quả cùng cấu trúc giá tham khảo, với isEstimated = false
     */
    PricingCalculationResponse calculate(PricingInput input);
}

