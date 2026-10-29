package com.example.house_moving_service.modules.quotation.mapper;

import com.example.house_moving_service.modules.quotation.dto.response.QuotationDraftDto;
import com.example.house_moving_service.modules.quotation.dto.response.QuotationItemDto;
import com.example.house_moving_service.modules.quotation.dto.response.QuotationItemResponse;
import com.example.house_moving_service.modules.quotation.dto.response.QuotationResponse;
import com.example.house_moving_service.modules.quotation.entity.Quotation;
import com.example.house_moving_service.modules.quotation.entity.QuotationItem;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

@Component
public class QuotationMapper {

    public QuotationDraftDto toDraftDto(Quotation quotation) {
        if (quotation == null) {
            return null;
        }
        return QuotationDraftDto.builder()
                .id(quotation.getId())
                .bookingId(quotation.getBooking() != null ? quotation.getBooking().getId() : null)
                .version(quotation.getVersion())
                .status(quotation.getStatus())
                .note(quotation.getNote())
                .createdAt(quotation.getCreatedAt())
                .build();
    }

    public QuotationItemDto toItemDto(QuotationItem item) {
        if (item == null) {
            return null;
        }
        return QuotationItemDto.builder()
                .id(item.getId())
                .quotationId(item.getQuotation() != null ? item.getQuotation().getId() : null)
                .bookingItemId(item.getBookingItem() != null ? item.getBookingItem().getId() : null)
                .itemName(item.getItemName())
                .quantity(item.getQuantity())
                .unit(item.getUnit())
                .unitPrice(item.getUnitPrice())
                .lineAmount(item.getLineAmount())
                .estimatedVolumeM3(item.getEstimatedVolumeM3())
                .isFragile(item.getIsFragile())
                .packingRequired(item.getPackingRequired())
                .disassemblyRequired(item.getDisassemblyRequired())
                .note(item.getNote())
                .build();
    }

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
