package com.example.house_moving_service.repository;

import com.example.house_moving_service.entity.RefreshToken;
import com.example.house_moving_service.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {
    Optional<RefreshToken> findByToken(String token);
    void deleteByUser(User user);
    Optional<RefreshToken> findByTokenAndRevokedFalse(String token);
}
