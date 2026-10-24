package com.example.house_moving_service.modules.identity.service;

import com.example.house_moving_service.modules.identity.dto.response.UserResponse;
import com.example.house_moving_service.modules.identity.entity.User;
import com.example.house_moving_service.modules.identity.mapper.UserMapper;
import com.example.house_moving_service.modules.identity.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;

    @Transactional(readOnly = true)
    public UserResponse getCurrentUser(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy người dùng!"));

        return userMapper.toUserResponse(user);
    }
}
