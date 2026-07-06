package cmc.rodi.global.auth.controller;

import cmc.rodi.global.auth.dto.LogoutRequest;
import cmc.rodi.global.auth.dto.SocialLoginRequest;
import cmc.rodi.global.auth.dto.SocialLoginResponse;
import cmc.rodi.global.auth.dto.TokenRefreshRequest;
import cmc.rodi.global.auth.dto.TokenResponse;
import cmc.rodi.global.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * 인증 API의 Swagger 문서 스펙. 문서 어노테이션을 컨트롤러 로직과 분리해 가독성을 유지한다. 실제 매핑·구현은 {@link AuthController}가 담당한다.
 */
@Tag(name = "Auth", description = "소셜 로그인/토큰 재발급/로그아웃")
public interface AuthControllerDocs {

    @Operation(
            summary = "소셜 로그인",
            description =
                    "앱에서 받은 소셜 credential(카카오=access token, 애플=authorizationCode)을 검증해 로그인/가입한다. "
                            + "응답 status=SUCCESS면 토큰 발급(신규는 isNewMember=true), "
                            + "status=WITHDRAWAL_PENDING이면 탈퇴 유예기간 내 재로그인이라 토큰 대신 복구 안내를 준다. "
                            + "미지원 provider는 AUTH_400_1, 검증 실패는 AUTH_401_5, 재가입 대기(유예 경과)는 MEMBER_409_1.")
    ApiResponse<SocialLoginResponse> login(
            @Parameter(description = "소셜 공급자", example = "kakao") @PathVariable String provider,
            @RequestBody SocialLoginRequest request);

    @Operation(
            summary = "토큰 재발급",
            description =
                    "refresh token으로 access token을 재발급한다(회전). "
                            + "이미 폐기된 토큰 재제출 시 재사용 탐지로 회원의 전체 세션이 폐기된다.")
    ApiResponse<TokenResponse> reissue(@RequestBody TokenRefreshRequest request);

    @Operation(summary = "로그아웃", description = "전달한 refresh token 세션을 폐기한다.")
    ApiResponse<Void> logout(@RequestBody LogoutRequest request);
}
