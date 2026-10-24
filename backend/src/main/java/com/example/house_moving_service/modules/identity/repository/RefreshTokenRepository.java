package com.example.house_moving_service.modules.identity.repository;

import com.example.house_moving_service.modules.identity.entity.RefreshToken;
import com.example.house_moving_service.modules.identity.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {
    Optional<RefreshToken> findByToken(String token);
    void deleteByUser(User user);
    Optional<RefreshToken> findByTokenAndRevokedFalse(String token);
}
