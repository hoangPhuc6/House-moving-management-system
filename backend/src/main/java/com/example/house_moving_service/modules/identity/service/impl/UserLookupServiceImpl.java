package com.example.house_moving_service.modules.identity.service.impl;

import com.example.house_moving_service.modules.identity.entity.User;
import com.example.house_moving_service.modules.identity.repository.UserRepository;
import com.example.house_moving_service.modules.identity.service.UserLookupService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

// Bean riêng, tách khỏi UserService. Không gắn @Transactional để tham gia transaction của module gọi
@Service
@RequiredArgsConstructor
public class UserLookupServiceImpl implements UserLookupService {

    private final UserRepository userRepository;

    @Override
    public User getUserReference(Long userId) {
        return userRepository.getReferenceById(userId);
    }
}
