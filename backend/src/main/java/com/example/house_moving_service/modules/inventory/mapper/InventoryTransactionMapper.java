package com.example.house_moving_service.modules.inventory.mapper;

import com.example.house_moving_service.modules.booking.entity.Booking;
import com.example.house_moving_service.modules.inventory.dto.response.InventoryTransactionResponse;
import com.example.house_moving_service.modules.inventory.entity.InventoryTransaction;
import com.example.house_moving_service.modules.inventory.entity.Material;
import org.springframework.stereotype.Component;

@Component
public class InventoryTransactionMapper {

    public InventoryTransactionResponse toResponse(InventoryTransaction tx) {
        Material material = tx.getMaterial();
        Booking booking = tx.getBooking();
        return InventoryTransactionResponse.builder()
                .id(tx.getId())
                .materialId(material != null ? material.getId() : null)
                .materialCode(material != null ? material.getCode() : null)
                .materialName(material != null ? material.getName() : null)
                .type(tx.getTransactionType())
                .quantity(tx.getQuantity())
                .quantityChange(tx.getQuantityChange())
                .balanceAfter(tx.getBalanceAfter())
                .unitCost(tx.getUnitCost())
                .bookingId(booking != null ? booking.getId() : null)
                .bookingCode(booking != null ? booking.getBookingCode() : null)
                .scheduleId(tx.getSchedule() != null ? tx.getSchedule().getId() : null)
                .allocationId(tx.getAllocation() != null ? tx.getAllocation().getId() : null)
                .note(tx.getNote())
                .createdBy(UserNames.of(tx.getCreatedBy()))
                .createdAt(tx.getCreatedAt())
                .build();
    }
}
