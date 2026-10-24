package com.example.house_moving_service.modules.identity.mapper;

import com.example.house_moving_service.modules.identity.dto.response.AuthResponse;
import com.example.house_moving_service.modules.identity.dto.response.UserResponse;
import com.example.house_moving_service.modules.identity.entity.User;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class UserMapper {

    /**
     * Chuyển đổi từ Entity User sang DTO UserResponse
     */
    public UserResponse toUserResponse(User user) {
        if (user == null) {
            return null;
        }

        return UserResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .fullName(getFullName(user))
                .phoneNumber(getPhoneNumber(user))
                .isActive(user.getIsActive())
                .roles(getRoleCodes(user))
                .createdAt(user.getCreatedAt())
                .build();
    }

    /**
     * Chuyển đổi từ Entity User và Token sang DTO AuthResponse
     */
    public AuthResponse toAuthResponse(User user, String accessToken, String refreshToken) {
        if (user == null) {
            return null;
        }

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .userId(user.getId())
                .email(user.getEmail())
                .fullName(getFullName(user))
                .roles(getRoleCodes(user))
                .build();
    }

    /**
     * Lấy họ tên hiển thị từ Customer hoặc Staff profile
     */
    public String getFullName(User user) {
        if (user.getCustomer() != null && user.getCustomer().getFullName() != null) {
            return user.getCustomer().getFullName();
        }
        if (user.getStaff() != null && user.getStaff().getFullName() != null) {
            return user.getStaff().getFullName();
        }
        return user.getEmail();
    }

    /**
     * Lấy số điện thoại từ Customer profile
     */
    public String getPhoneNumber(User user) {
        if (user.getCustomer() != null) {
            return user.getCustomer().getPhoneNumber();
        }
        return null;
    }

    /**
     * Lấy danh sách tên mã vai trò (Role Codes)
     */
    public List<String> getRoleCodes(User user) {
        if (user.getRoles() == null) {
            return List.of();
        }
        return user.getRoles().stream()
                .map(role -> role.getCode().name())
                .toList();
    }
}
