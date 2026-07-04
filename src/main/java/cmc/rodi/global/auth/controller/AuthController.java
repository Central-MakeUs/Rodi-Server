package cmc.rodi.global.auth.controller;

import cmc.rodi.global.auth.dto.LogoutRequest;
import cmc.rodi.global.auth.dto.SocialLoginRequest;
import cmc.rodi.global.auth.dto.TokenRefreshRequest;
import cmc.rodi.global.auth.dto.TokenResponse;
import cmc.rodi.global.auth.entity.SocialProvider;
import cmc.rodi.global.auth.service.AuthService;
import cmc.rodi.global.common.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 인증 API. 소셜 로그인·토큰 재발급·로그아웃. 문서 스펙은 {@link AuthControllerDocs}. */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController implements AuthControllerDocs {

    private final AuthService authService;

    @Override
    @PostMapping("/oauth/{provider}")
    public ApiResponse<TokenResponse> login(
            @PathVariable String provider, @Valid @RequestBody SocialLoginRequest request) {
        TokenResponse response =
                authService.login(SocialProvider.from(provider), request.credential());
        return ApiResponse.success(response);
    }

    @Override
    @PostMapping("/token/refresh")
    public ApiResponse<TokenResponse> reissue(@Valid @RequestBody TokenRefreshRequest request) {
        return ApiResponse.success(authService.reissue(request.refreshToken()));
    }

    @Override
    @PostMapping("/logout")
    public ApiResponse<Void> logout(@Valid @RequestBody LogoutRequest request) {
        authService.logout(request.refreshToken());
        return ApiResponse.success(null);
    }
}
