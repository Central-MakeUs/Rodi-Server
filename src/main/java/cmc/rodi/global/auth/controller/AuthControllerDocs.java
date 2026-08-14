package cmc.rodi.global.auth.controller;

import cmc.rodi.global.auth.dto.LogoutRequest;
import cmc.rodi.global.auth.dto.SocialLoginRequest;
import cmc.rodi.global.auth.dto.SocialLoginResponse;
import cmc.rodi.global.auth.dto.TokenRefreshRequest;
import cmc.rodi.global.auth.dto.TokenResponse;
import cmc.rodi.global.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * 인증 API의 Swagger 문서 스펙. 문서 어노테이션을 컨트롤러 로직과 분리해 가독성을 유지한다. 실제 매핑·구현은 {@link AuthController}가 담당한다.
 */
@Tag(name = "Auth", description = "소셜 로그인/토큰 재발급/로그아웃")
public interface AuthControllerDocs {

    // provider·credential 모두 카카오/애플 예시를 드롭다운으로 제공한다(카카오=access token, 애플=authorizationCode)
    String KAKAO_BODY = "{\n  \"credential\": \"kakao-access-token-xxx\"\n}";
    String APPLE_BODY = "{\n  \"credential\": \"apple-authorization-code-xxx\"\n}";

    @Operation(
            summary = "소셜 로그인",
            description =
                    "앱에서 받은 소셜 credential(카카오=access token, 애플=authorizationCode)을 검증해 로그인/가입한다. "
                            + "응답 status=SUCCESS면 토큰 발급(신규는 isNewMember=true). "
                            + "온보딩 화면 분기는 isOnboarded로 판단한다 — 가입 후 온보딩 중 이탈한 회원은 재로그인 시 "
                            + "isNewMember=false지만 isOnboarded=false다. "
                            + "status=WITHDRAWAL_PENDING이면 탈퇴 유예기간 내 재로그인이라 토큰 대신 복구 안내를 준다. "
                            + "status=WITHDRAWAL_LOCKED면 복구 기간이 지나 재가입을 기다리는 중이라 "
                            + "토큰 대신 reRegisterableAt(재가입 가능 시각)이 온다 — 오류가 아니라 200이다. "
                            + "미지원 provider는 AUTH_400_1, 검증 실패는 AUTH_401_5.")
    ApiResponse<SocialLoginResponse> login(
            @Parameter(
                            description = "소셜 공급자",
                            examples = {
                                @ExampleObject(name = "카카오", value = "kakao"),
                                @ExampleObject(name = "애플", value = "apple")
                            })
                    @PathVariable
                    String provider,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                            content =
                                    @Content(
                                            mediaType = "application/json",
                                            examples = {
                                                @ExampleObject(
                                                        name = "카카오",
                                                        description = "카카오 access token",
                                                        value = KAKAO_BODY),
                                                @ExampleObject(
                                                        name = "애플",
                                                        description = "애플 authorizationCode",
                                                        value = APPLE_BODY)
                                            }))
                    @RequestBody
                    SocialLoginRequest request);

    @Operation(
            summary = "계정 복구",
            description =
                    "탈퇴 유예기간(3일) 내에 동일 소셜 credential로 계정을 복구하고 토큰을 발급한다. "
                            + "로그인과 같은 응답이라 isOnboarded도 함께 온다 — 복구 직후에도 온보딩 분기가 필요하다. "
                            + "유예가 지났으면 로그인과 같은 200 WITHDRAWAL_LOCKED + reRegisterableAt이 온다. "
                            + "복구 대상이 없으면 MEMBER_404_1.")
    ApiResponse<SocialLoginResponse> restore(
            @Parameter(
                            description = "소셜 공급자",
                            examples = {
                                @ExampleObject(name = "카카오", value = "kakao"),
                                @ExampleObject(name = "애플", value = "apple")
                            })
                    @PathVariable
                    String provider,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                            content =
                                    @Content(
                                            mediaType = "application/json",
                                            examples = {
                                                @ExampleObject(
                                                        name = "카카오",
                                                        description = "카카오 access token",
                                                        value = KAKAO_BODY),
                                                @ExampleObject(
                                                        name = "애플",
                                                        description = "애플 authorizationCode",
                                                        value = APPLE_BODY)
                                            }))
                    @RequestBody
                    SocialLoginRequest request);

    @Operation(
            summary = "토큰 재발급",
            description =
                    "refresh token으로 access token을 재발급한다(회전). "
                            + "이미 폐기된 토큰 재제출 시 재사용 탐지로 회원의 전체 세션이 폐기된다. "
                            + "토큰만 갱신하고 들어온 앱도 화면을 분기할 수 있게 isOnboarded를 함께 준다. "
                            + "재발급은 가입이 아니므로 isNewMember는 없다.")
    ApiResponse<TokenResponse> reissue(@RequestBody TokenRefreshRequest request);

    @Operation(summary = "로그아웃", description = "전달한 refresh token 세션을 폐기한다.")
    ApiResponse<Void> logout(@RequestBody LogoutRequest request);
}
