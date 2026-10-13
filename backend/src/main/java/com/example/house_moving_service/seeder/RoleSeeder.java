package com.example.house_moving_service.seeder;

import com.example.house_moving_service.entity.Role;
import com.example.house_moving_service.enums.RoleCode;
import com.example.house_moving_service.repository.RoleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class RoleSeeder implements CommandLineRunner {

    private final RoleRepository roleRepository;

    @Override
    public void run(String... args) {
        seedRoles();
    }

    private void seedRoles() {
        List<Role> defaultRoles = List.of(
                Role.builder()
                        .code(RoleCode.ADMIN)
                        .name("Quản trị viên")
                        .description("Toàn quyền quản trị hệ thống")
                        .isActive(true)
                        .build(),
                Role.builder()
                        .code(RoleCode.DISPATCHER)
                        .name("Nhân viên điều phối")
                        .description("Điều phối đơn hàng, xe tải và nhân lực vận chuyển")
                        .isActive(true)
                        .build(),
                Role.builder()
                        .code(RoleCode.MOVER)
                        .name("Nhân viên vận chuyển")
                        .description("Trực tiếp bốc vác, tháo dỡ và vận chuyển đồ đạc")
                        .isActive(true)
                        .build(),
                Role.builder()
                        .code(RoleCode.CUSTOMER)
                        .name("Khách hàng")
                        .description("Khách hàng sử dụng dịch vụ chuyển nhà")
                        .isActive(true)
                        .build());

        for (Role role : defaultRoles) {
            if (!roleRepository.existsByCode(role.getCode())) {
                roleRepository.save(role);
                log.info("Seeded role thành công: {} - {}", role.getCode(), role.getName());
            }
        }
    }
}
