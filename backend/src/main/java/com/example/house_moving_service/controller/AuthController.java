package com.example.house_moving_service.controller;

import com.example.house_moving_service.dto.request.LoginRequest;
import com.example.house_moving_service.dto.request.RefreshTokenRequest;
import com.example.house_moving_service.dto.request.RegisterRequest;
import com.example.house_moving_service.dto.response.ApiResponse;
import com.example.house_moving_service.dto.response.AuthResponse;
import com.example.house_moving_service.dto.response.TokenRefreshResponse;
import com.example.house_moving_service.dto.response.UserResponse;
import com.example.house_moving_service.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")

    public ResponseEntity<ApiResponse<AuthResponse>> register(@Valid @RequestBody RegisterRequest request) {
        AuthResponse response = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Đăng ký tài khoản thành công", response));
    }


    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest request) {
        AuthResponse response = authService.login(request);
        return ResponseEntity.ok(ApiResponse.success("Đăng nhập thành công", response));
    }

    @PostMapping("/refresh-token")
    public ResponseEntity<ApiResponse<TokenRefreshResponse>> refreshToken(@Valid @RequestBody RefreshTokenRequest req) {
        TokenRefreshResponse response = authService.refreshToken(req.getRefreshToken());
        return ApiResponse.success(response, "Cấp lại Access Token thành công");
    }
}
