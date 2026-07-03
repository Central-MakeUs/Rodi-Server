package cmc.rodi.global.auth.dto;

import cmc.rodi.global.auth.vo.Tokens;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

/** 로그인·재발급 응답. 신규 가입 여부를 함께 내려 클라이언트가 온보딩 화면을 분기한다. */
public record TokenResponse(
        @Schema(description = "API 인증용 access token(JWT)") String accessToken,
        @Schema(description = "access token 재발급용 refresh token") String refreshToken,
        @Schema(description = "이번 요청으로 새로 가입한 회원이면 true(온보딩 필요)") @JsonProperty("isNewMember")
                boolean isNewMember) {

    public static TokenResponse of(Tokens tokens, boolean isNewMember) {
        return new TokenResponse(tokens.accessToken(), tokens.refreshToken(), isNewMember);
    }
}
