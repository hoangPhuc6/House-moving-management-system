package com.example.house_moving_service.modules.inventory.enums;

/**
 * Nhóm vật tư (materials.category).
 * CONSUMABLE: thùng carton, băng keo, màng PE... dùng xong là hết, tính tiền khách theo số đã dùng.
 * TOOL: xe đẩy, dây chằng, chăn bọc... phải hoàn kho, không tính tiền (quantity_used luôn = 0).
 */
public enum MaterialCategory {
    CONSUMABLE,
    TOOL
}
