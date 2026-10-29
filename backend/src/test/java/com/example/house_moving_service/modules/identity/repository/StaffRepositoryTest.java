package com.example.house_moving_service.modules.identity.repository;

import com.example.house_moving_service.modules.identity.entity.Role;
import com.example.house_moving_service.modules.identity.entity.Staff;
import com.example.house_moving_service.modules.identity.entity.User;
import com.example.house_moving_service.modules.identity.enums.RoleCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class StaffRepositoryTest {

    @Autowired
    private StaffRepository staffRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    private Long mover1Id;
    private Long mover2Id;
    private Long dispatcherId;

    @BeforeEach
    void setUp() {
        // Tài khoản seed V7
        mover1Id = userRepository.findByEmail("mover1@housemoving.vn").orElseThrow().getId();
        mover2Id = userRepository.findByEmail("mover2@housemoving.vn").orElseThrow().getId();
        dispatcherId = userRepository.findByEmail("dispatcher@housemoving.vn").orElseThrow().getId();
    }

    @Test
    @DisplayName("existsActiveByIdAndRole: MOVER seed hợp lệ với role MOVER")
    void testExistsActive_SeedMover_True() {
        assertThat(staffRepository.existsActiveByIdAndRole(mover1Id, RoleCode.MOVER)).isTrue();
    }

    @Test
    @DisplayName("existsActiveByIdAndRole: DISPATCHER không hợp lệ với role MOVER")
    void testExistsActive_DispatcherWithMoverRole_False() {
        assertThat(staffRepository.existsActiveByIdAndRole(dispatcherId, RoleCode.MOVER)).isFalse();
        assertThat(staffRepository.existsActiveByIdAndRole(dispatcherId, RoleCode.DISPATCHER)).isTrue();
    }

    @Test
    @DisplayName("existsActiveByIdAndRole: hồ sơ staff không ACTIVE bị loại")
    void testExistsActive_InactiveEmployment_False() {
        Long userId = createMover("mover-resigned@test.vn", true, "RESIGNED");

        assertThat(staffRepository.existsActiveByIdAndRole(userId, RoleCode.MOVER)).isFalse();
    }

    @Test
    @DisplayName("existsActiveByIdAndRole: tài khoản bị khóa (is_active = false) bị loại")
    void testExistsActive_LockedAccount_False() {
        Long userId = createMover("mover-locked@test.vn", false, "ACTIVE");

        assertThat(staffRepository.existsActiveByIdAndRole(userId, RoleCode.MOVER)).isFalse();
    }

    @Test
    @DisplayName("existsActiveByIdAndRole: user không có hồ sơ staff hoặc id không tồn tại trả false")
    void testExistsActive_NotStaff_False() {
        Long customerId = userRepository.findByEmail("customer1@gmail.com").orElseThrow().getId();

        assertThat(staffRepository.existsActiveByIdAndRole(customerId, RoleCode.MOVER)).isFalse();
        assertThat(staffRepository.existsActiveByIdAndRole(-1L, RoleCode.MOVER)).isFalse();
    }

    @Test
    @DisplayName("findAllActiveByRole: chỉ trả MOVER hợp lệ, loại người bị khóa / nghỉ việc")
    void testFindAllActiveByRole_Mover() {
        Long resignedId = createMover("mover-resigned@test.vn", true, "RESIGNED");
        Long lockedId = createMover("mover-locked@test.vn", false, "ACTIVE");

        List<Long> ids = staffRepository.findAllActiveByRole(RoleCode.MOVER).stream()
                .map(Staff::getId)
                .toList();

        assertThat(ids).contains(mover1Id, mover2Id);
        assertThat(ids).doesNotContain(dispatcherId, resignedId, lockedId);
    }

    private Long createMover(String email, boolean accountActive, String employmentStatus) {
        Role moverRole = roleRepository.findByCode(RoleCode.MOVER).orElseThrow();
        User user = User.builder()
                .email(email)
                .password("not-used")
                .isActive(accountActive)
                .build();
        user.getRoles().add(moverRole);
        user = userRepository.save(user);

        staffRepository.save(Staff.builder()
                .user(user)
                .staffCode("TST-" + user.getId())
                .fullName("Nhân viên test " + email)
                .employmentStatus(employmentStatus)
                .build());
        return user.getId();
    }
}
