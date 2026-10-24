package com.example.house_moving_service.service;

import com.example.house_moving_service.dto.request.LoginRequest;
import com.example.house_moving_service.dto.request.RegisterRequest;
import com.example.house_moving_service.dto.response.AuthResponse;
import com.example.house_moving_service.dto.response.TokenRefreshResponse;
import com.example.house_moving_service.dto.response.UserResponse;
import com.example.house_moving_service.entity.Customer;
import com.example.house_moving_service.entity.RefreshToken;
import com.example.house_moving_service.entity.Role;
import com.example.house_moving_service.entity.User;
import com.example.house_moving_service.enums.RoleCode;
import com.example.house_moving_service.mapper.UserMapper;
import com.example.house_moving_service.repository.CustomerRepository;
import com.example.house_moving_service.repository.RoleRepository;
import com.example.house_moving_service.repository.UserRepository;
import com.example.house_moving_service.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

        private final UserRepository userRepository;
        private final CustomerRepository customerRepository;
        private final PasswordEncoder passwordEncoder;
        private final AuthenticationManager authenticationManager;
        private final JwtTokenProvider tokenProvider;
        private final RoleRepository roleRepository;
        private final UserMapper userMapper;
        private final RefreshTokenService refreshTokenService;

        @Transactional
        public UserResponse register(RegisterRequest request) {
                if (userRepository.existsByEmail(request.getEmail())) {
                        throw new IllegalArgumentException("Email đã được sử dụng!");
                }

                Role customerRole = roleRepository.findByCode(RoleCode.CUSTOMER)
                                .orElseThrow(() -> new IllegalStateException(
                                                "Role CUSTOMER chưa được khởi tạo trong hệ thống!"));

                // Tạo User
                User user = User.builder()
                                .email(request.getEmail())
                                .password(passwordEncoder.encode(request.getPassword()))
                                .isActive(true)
                                .build();
                user.addRole(customerRole);
                user = userRepository.save(user);

                Customer customer = Customer.builder()
                                .user(user)
                                .fullName(request.getFullName())
                                .phoneNumber(request.getPhoneNumber())
                                .birthday(request.getBirthday())
                                .build();
                customerRepository.save(customer);
                user.setCustomer(customer);

                return userMapper.toUserResponse(user);
        }

        @Transactional
        public AuthResponse login(LoginRequest request) {
                authenticationManager.authenticate(
                                new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword()));

                User user = userRepository.findByEmail(request.getEmail())
                                .orElseThrow(() -> new IllegalArgumentException(
                                                "Email hoặc mật khẩu không chính xác!"));

                String token = tokenProvider.generateToken(user);

                String refreshToken = refreshTokenService.createRefreshToken(user);

                return userMapper.toAuthResponse(user, token, refreshToken);
        }

        @Transactional
        public TokenRefreshResponse refreshToken(String refreshToken) {
            RefreshToken validToken = refreshTokenService.verifyRefreshToken(refreshToken);
            User user = validToken.getUser();
            String accessToken = tokenProvider.generateToken(user);
            return TokenRefreshResponse.builder()
                    .accessToken(accessToken)
                    .refreshToken(refreshToken)
                    .build();
        }
}
