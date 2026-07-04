package cmc.rodi.global.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/** 소셜 로그인 요청. 앱 SDK에서 받은 공급자 access token을 서버가 검증한다. */
public record SocialLoginRequest(
        @Schema(description = "소셜 공급자가 발급한 access token", example = "kakao-access-token-xxx")
                @NotBlank
                String credential) {}
