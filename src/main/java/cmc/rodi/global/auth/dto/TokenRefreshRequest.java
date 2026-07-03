package cmc.rodi.global.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/** access token 재발급 요청. 발급받은 refresh token을 그대로 전달한다. */
public record TokenRefreshRequest(
        @Schema(description = "발급받은 refresh token") @NotBlank String refreshToken) {}
