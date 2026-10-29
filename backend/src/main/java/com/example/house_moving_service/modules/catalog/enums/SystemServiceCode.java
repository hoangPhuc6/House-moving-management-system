package com.example.house_moving_service.modules.catalog.enums;

/**
 * Mã cố định (cột services.code) của các dịch vụ do hệ thống tự cộng khi tính giá. Gán bằng migration, không sửa qua API.
 * Dịch vụ có mã được sửa giá nhưng không tắt / xóa được.
 */
public final class SystemServiceCode {

    // Phụ phí khuân vác thang bộ, tính theo tầng khi không có thang máy
    public static final String SURCHARGE_STAIRS = "SURCHARGE_STAIRS";
    // Phụ phí hẻm sâu, khi cự ly đi bộ từ chỗ đậu xe vượt ngưỡng
    public static final String SURCHARGE_DEEP_ALLEY = "SURCHARGE_DEEP_ALLEY";
    // Phụ phí mỗi km vượt quá số km đã gồm trong giá xe
    public static final String SURCHARGE_EXTRA_KM = "SURCHARGE_EXTRA_KM";

    private SystemServiceCode() {
    }
}
