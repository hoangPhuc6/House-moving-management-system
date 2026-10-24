package com.example.house_moving_service.mapper;

import com.example.house_moving_service.dto.response.booking.BookingDetailResponse;
import com.example.house_moving_service.dto.response.booking.BookingItemResponse;
import com.example.house_moving_service.dto.response.booking.BookingRouteResponse;
import com.example.house_moving_service.dto.response.booking.BookingServiceResponse;
import com.example.house_moving_service.dto.response.booking.BookingStatusHistoryResponse;
import com.example.house_moving_service.dto.response.booking.BookingSummaryResponse;
import com.example.house_moving_service.dto.response.booking.BookingVoucherResponse;
import com.example.house_moving_service.entity.Booking;
import com.example.house_moving_service.entity.BookingItem;
import com.example.house_moving_service.entity.BookingRoute;
import com.example.house_moving_service.entity.BookingStatusHistory;
import com.example.house_moving_service.entity.BookingVoucher;
import com.example.house_moving_service.modules.identity.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Component
@RequiredArgsConstructor
public class BookingMapper {

    private final UserMapper userMapper;

    /**
     * Chuyển đổi sang BookingDetailResponse nhận trực tiếp các sub-entities đã lưu
     * nhằm triệt tiêu hoàn toàn nguy cơ LazyInitializationException
     */
    public BookingDetailResponse toDetailResponse(
            Booking booking,
            BookingRoute route,
            List<BookingItem> items,
            List<com.example.house_moving_service.entity.BookingService> services,
            List<BookingVoucher> vouchers,
            List<BookingStatusHistory> statusHistories
    ) {
        if (booking == null) {
            return null;
        }

        String customerName = null;
        Long customerId = null;
        if (booking.getCustomer() != null) {
            customerId = booking.getCustomer().getId();
            customerName = booking.getCustomer().getFullName();
        }

        return BookingDetailResponse.builder()
                .id(booking.getId())
                .bookingCode(booking.getBookingCode())
                .customerId(customerId)
                .customerName(customerName)
                .contactName(booking.getContactName())
                .contactPhone(booking.getContactPhone())
                .status(booking.getStatus())
                .totalAmount(booking.getTotalAmount())
                .discountAmount(booking.getDiscountAmount())
                .note(booking.getNote())
                .desiredStartAt(booking.getDesiredStartAt())
                .desiredEndAt(booking.getDesiredEndAt())
                .createdAt(booking.getCreatedAt())
                .updatedAt(booking.getUpdatedAt())
                .route(toRouteResponse(route))
                .items(items != null ? items.stream().map(this::toItemResponse).toList() : Collections.emptyList())
                .services(services != null ? services.stream().map(this::toServiceResponse).toList() : Collections.emptyList())
                .vouchers(vouchers != null ? vouchers.stream().map(this::toVoucherResponse).toList() : Collections.emptyList())
                .statusHistories(statusHistories != null ? statusHistories.stream().map(this::toStatusHistoryResponse).toList() : Collections.emptyList())
                .build();
    }

    /**
     * Chuyển đổi Entity Booking sang BookingDetailResponse dựa trên các quan hệ có sẵn của Entity
     */
    public BookingDetailResponse toDetailResponse(Booking booking) {
        if (booking == null) {
            return null;
        }
        return toDetailResponse(
                booking,
                booking.getRoute(),
                booking.getItems(),
                booking.getServices(),
                booking.getVouchers(),
                booking.getStatusHistories()
        );
    }

    /**
     * Chuyển đổi sang tóm tắt danh sách đơn hàng BookingSummaryResponse
     */
    public BookingSummaryResponse toSummaryResponse(Booking booking) {
        if (booking == null) {
            return null;
        }

        String pickupAddress = booking.getRoute() != null ? booking.getRoute().getPickupAddress() : null;
        String dropoffAddress = booking.getRoute() != null ? booking.getRoute().getDropoffAddress() : null;

        return BookingSummaryResponse.builder()
                .id(booking.getId())
                .bookingCode(booking.getBookingCode())
                .status(booking.getStatus())
                .totalAmount(booking.getTotalAmount())
                .discountAmount(booking.getDiscountAmount())
                .pickupAddress(pickupAddress)
                .dropoffAddress(dropoffAddress)
                .desiredStartAt(booking.getDesiredStartAt())
                .createdAt(booking.getCreatedAt())
                .build();
    }

    public BookingRouteResponse toRouteResponse(BookingRoute route) {
        if (route == null) {
            return null;
        }
        return BookingRouteResponse.builder()
                .id(route.getId())
                .pickupAddress(route.getPickupAddress())
                .pickupLat(route.getPickupLat())
                .pickupLng(route.getPickupLng())
                .pickupPropertyType(route.getPickupPropertyType())
                .pickupNote(route.getPickupNote())
                .dropoffAddress(route.getDropoffAddress())
                .dropoffLat(route.getDropoffLat())
                .dropoffLng(route.getDropoffLng())
                .dropoffPropertyType(route.getDropoffPropertyType())
                .dropoffNote(route.getDropoffNote())
                .estimatedDistanceKm(route.getEstimatedDistanceKm())
                .build();
    }

    public BookingItemResponse toItemResponse(BookingItem item) {
        if (item == null) {
            return null;
        }
        return BookingItemResponse.builder()
                .id(item.getId())
                .itemName(item.getItemName())
                .quantity(item.getQuantity())
                .estimatedVolumeM3(item.getEstimatedVolumeM3())
                .isFragile(item.getIsFragile())
                .packingRequired(item.getPackingRequired())
                .disassemblyRequired(item.getDisassemblyRequired())
                .note(item.getNote())
                .build();
    }

    public BookingServiceResponse toServiceResponse(com.example.house_moving_service.entity.BookingService bs) {
        if (bs == null) {
            return null;
        }
        Integer serviceId = bs.getService() != null ? bs.getService().getId() : null;
        String serviceName = bs.getService() != null ? bs.getService().getName() : null;
        BigDecimal priceApplied = bs.getPriceApplied();
        Integer quantity = bs.getQuantity() != null ? bs.getQuantity() : 1;
        BigDecimal totalPrice = priceApplied != null
                ? priceApplied.multiply(BigDecimal.valueOf(quantity)).setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        return BookingServiceResponse.builder()
                .id(bs.getId())
                .serviceId(serviceId)
                .serviceName(serviceName)
                .priceApplied(priceApplied)
                .quantity(quantity)
                .totalPrice(totalPrice)
                .note(bs.getNote())
                .build();
    }

    public BookingVoucherResponse toVoucherResponse(BookingVoucher bv) {
        if (bv == null) {
            return null;
        }
        Integer voucherId = bv.getVoucher() != null ? bv.getVoucher().getId() : null;
        String code = bv.getVoucher() != null ? bv.getVoucher().getCode() : null;
        String title = bv.getVoucher() != null ? bv.getVoucher().getTitle() : null;

        return BookingVoucherResponse.builder()
                .id(bv.getId())
                .voucherId(voucherId)
                .voucherCode(code)
                .voucherTitle(title)
                .discountAmount(bv.getDiscountAmount())
                .appliedDate(bv.getAppliedDate())
                .build();
    }

    public BookingStatusHistoryResponse toStatusHistoryResponse(BookingStatusHistory bsh) {
        if (bsh == null) {
            return null;
        }
        Long changedById = bsh.getChangedBy() != null ? bsh.getChangedBy().getId() : null;
        String changedByName = bsh.getChangedBy() != null ? userMapper.getFullName(bsh.getChangedBy()) : null;

        return BookingStatusHistoryResponse.builder()
                .id(bsh.getId())
                .oldStatus(bsh.getOldStatus())
                .newStatus(bsh.getNewStatus())
                .changedById(changedById)
                .changedByName(changedByName)
                .changedAt(bsh.getChangedAt())
                .note(bsh.getNote())
                .build();
    }
}

