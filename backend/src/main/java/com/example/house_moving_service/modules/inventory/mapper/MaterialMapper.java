package com.example.house_moving_service.modules.inventory.mapper;

import com.example.house_moving_service.modules.inventory.dto.response.MaterialResponse;
import com.example.house_moving_service.modules.inventory.entity.Material;
import org.springframework.stereotype.Component;

@Component
public class MaterialMapper {

    public MaterialResponse toResponse(Material material) {
        return MaterialResponse.builder()
                .id(material.getId())
                .code(material.getCode())
                .name(material.getName())
                .category(material.getCategory())
                .unit(material.getUnit())
                .unitPrice(material.getUnitPrice())
                .quantityOnHand(material.getQuantityOnHand())
                .minStock(material.getMinStock())
                .lowStock(material.isLowStock())
                .description(material.getDescription())
                .isActive(material.getIsActive())
                .createdAt(material.getCreatedAt())
                .updatedAt(material.getUpdatedAt())
                .build();
    }
}
