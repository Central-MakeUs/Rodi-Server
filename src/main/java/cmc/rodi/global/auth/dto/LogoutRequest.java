package cmc.rodi.global.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/** 로그아웃 요청. 폐기할 refresh token을 전달한다(해당 세션만 종료). */
public record LogoutRequest(
        @Schema(description = "폐기할 refresh token") @NotBlank String refreshToken) {}
