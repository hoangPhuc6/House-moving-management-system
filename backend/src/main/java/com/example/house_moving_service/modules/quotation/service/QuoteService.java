package com.example.house_moving_service.modules.quotation.service;

import com.example.house_moving_service.modules.quotation.dto.request.CreateQuoteRequest;
import com.example.house_moving_service.modules.quotation.dto.request.RejectQuotationRequest;
import com.example.house_moving_service.modules.quotation.dto.response.QuotationResponse;

import java.util.List;

public interface QuoteService {

    /**
     * Lập báo giá chính thức sau khảo sát: tính lại giá theo dịch vụ/đồ đạc thực tế, tạo phiên bản
     * báo giá mới (SENT) và chuyển đơn sang QUOTED. Báo giá SENT trước đó bị chuyển sang EXPIRED.
     * Tổng tiền của đơn chỉ được chốt khi khách chấp nhận báo giá.
     */
    QuotationResponse createQuote(Integer bookingId, CreateQuoteRequest request, Long staffUserId);

    /**
     * Tất cả phiên bản báo giá của đơn (mới nhất trước) cho nhân viên.
     */
    List<QuotationResponse> getQuotations(Integer bookingId);

    /**
     * Các phiên bản báo giá của đơn cho khách hàng (chỉ chủ đơn).
     */
    List<QuotationResponse> getCustomerQuotations(Integer bookingId, Long currentUserId);

    /**
     * Khách chấp nhận báo giá: báo giá -> ACCEPTED, chốt tổng tiền vào đơn và chuyển đơn sang APPROVED.
     */
    QuotationResponse acceptQuotation(Integer bookingId, Integer quotationId, Long currentUserId);

    /**
     * Khách từ chối báo giá: báo giá -> REJECTED, đơn giữ QUOTED chờ nhân viên báo giá lại.
     */
    QuotationResponse rejectQuotation(Integer bookingId, Integer quotationId, RejectQuotationRequest request, Long currentUserId);
}
