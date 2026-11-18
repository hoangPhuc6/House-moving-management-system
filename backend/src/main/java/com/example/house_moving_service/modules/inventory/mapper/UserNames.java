package com.example.house_moving_service.modules.inventory.mapper;

import com.example.house_moving_service.modules.identity.entity.User;

/** Tên hiển thị người thao tác kho: họ tên nhân viên / khách, không có thì email. */
final class UserNames {

    private UserNames() {
    }

    static String of(User user) {
        if (user == null) {
            return null;
        }
        if (user.getStaff() != null && user.getStaff().getFullName() != null) {
            return user.getStaff().getFullName();
        }
        if (user.getCustomer() != null && user.getCustomer().getFullName() != null) {
            return user.getCustomer().getFullName();
        }
        return user.getEmail();
    }
}
