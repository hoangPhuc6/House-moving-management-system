package com.example.house_moving_service.security.access;

/**
 * Quy ước vùng URL — SecurityConfig chặn thô theo vùng, quyền chi tiết đặt bằng annotation trong package này.
 * <ul>
 *   <li>{@link #ADMIN}: cấu hình hệ thống (dịch vụ, nhóm dịch vụ, voucher, tài khoản) — chỉ ADMIN.</li>
 *   <li>{@link #MANAGEMENT}: vận hành (đơn hàng, báo giá, phân công) — ADMIN và DISPATCHER.</li>
 *   <li>Còn lại dưới /api/v1: phía người dùng — yêu cầu đăng nhập, role cụ thể đặt ở controller.</li>
 * </ul>
 */
public final class AccessZones {

    public static final String AUTH = "/api/auth/**";
    public static final String ADMIN = "/api/v1/admin/**";
    public static final String MANAGEMENT = "/api/v1/management/**";

    public static final String[] DOCS = {
            "/v3/api-docs/**",
            "/swagger-ui/**",
            "/swagger-ui.html"
    };

    private AccessZones() {
    }
}
