package com.example.house_moving_service.modules.pricing.mapper;

import com.example.house_moving_service.modules.pricing.dto.response.QuotationItemResponse;
import com.example.house_moving_service.modules.pricing.dto.response.QuotationResponse;
import com.example.house_moving_service.modules.pricing.entity.Quotation;
import com.example.house_moving_service.modules.pricing.entity.QuotationItem;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

@Component
public class QuotationMapper {

    public QuotationResponse toResponse(Quotation quotation) {
        if (quotation == null) {
            return null;
        }

        List<QuotationItemResponse> items = quotation.getItems() == null
                ? Collections.emptyList()
                : quotation.getItems().stream().map(this::toItemResponse).toList();

        return QuotationResponse.builder()
                .id(quotation.getId())
                .bookingId(quotation.getBooking() != null ? quotation.getBooking().getId() : null)
                .bookingCode(quotation.getBooking() != null ? quotation.getBooking().getBookingCode() : null)
                .version(quotation.getVersion())
                .amount(quotation.getAmount())
                .discountAmount(quotation.getDiscountAmount())
                .finalAmount(quotation.getFinalAmount())
                .status(quotation.getStatus())
                .note(quotation.getNote())
                .validUntil(quotation.getValidUntil())
                .createdById(quotation.getCreatedBy() != null ? quotation.getCreatedBy().getId() : null)
                .acceptedAt(quotation.getAcceptedAt())
                .createdAt(quotation.getCreatedAt())
                .items(items)
                .build();
    }

    private QuotationItemResponse toItemResponse(QuotationItem item) {
        return QuotationItemResponse.builder()
                .id(item.getId())
                .bookingItemId(item.getBookingItem() != null ? item.getBookingItem().getId() : null)
                .itemName(item.getItemName())
                .quantity(item.getQuantity())
                .unit(item.getUnit())
                .unitPrice(item.getUnitPrice())
                .lineAmount(item.getLineAmount())
                .estimatedVolumeM3(item.getEstimatedVolumeM3())
                .note(item.getNote())
                .build();
    }
}
