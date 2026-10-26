package com.example.house_moving_service.modules.identity.service;

import com.example.house_moving_service.modules.identity.entity.User;
import com.example.house_moving_service.modules.identity.repository.UserRepository;
import com.example.house_moving_service.modules.identity.service.impl.UserLookupServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserLookupServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserLookupServiceImpl userLookupService;

    @Test
    @DisplayName("getUserReference: Should return proxy via getReferenceById without loading the user")
    void testGetUserReference() {
        User proxy = User.builder().id(5L).build();
        when(userRepository.getReferenceById(5L)).thenReturn(proxy);

        assertThat(userLookupService.getUserReference(5L)).isSameAs(proxy);
        verify(userRepository, never()).findById(any());
    }
}
