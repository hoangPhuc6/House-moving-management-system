package com.example.house_moving_service.service;

import com.example.house_moving_service.dto.response.UserResponse;
import com.example.house_moving_service.entity.User;
import com.example.house_moving_service.mapper.UserMapper;
import com.example.house_moving_service.repository.UserRepository;
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
