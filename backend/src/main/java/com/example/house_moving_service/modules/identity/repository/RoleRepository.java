package com.example.house_moving_service.modules.identity.repository;

import com.example.house_moving_service.modules.identity.entity.Role;
import com.example.house_moving_service.modules.identity.enums.RoleCode;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RoleRepository extends JpaRepository<Role, Long> {
    Optional<Role> findByCode(RoleCode code);

    boolean existsByCode(RoleCode code);
}
