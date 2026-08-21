package cmc.rodi.global.auth.dto;

import cmc.rodi.global.auth.vo.Tokens;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 토큰 재발급 응답.
 *
 * <p>온보딩 완료 여부를 함께 내려 앱이 토큰만 갱신하고 들어온 경우에도 화면을 분기할 수 있게 한다. 재발급은 가입이 아니므로 {@code isNewMember}는 두지
 * 않는다 — 항상 false여서 정보가 없다.
 */
public record TokenResponse(
        @Schema(description = "API 인증용 access token(JWT)") String accessToken,
        @Schema(description = "access token 재발급용 refresh token") String refreshToken,
        @Schema(description = "온보딩을 마쳤는지 — 온보딩 화면 분기 기준") @JsonProperty("isOnboarded")
                boolean isOnboarded,
        @Schema(description = "코스 등록 튜토리얼을 완료했는지 — 코스 등록 버튼 진입 전 분기 기준")
                @JsonProperty("isCourseTutorialCompleted")
                boolean isCourseTutorialCompleted) {

    public static TokenResponse of(
            Tokens tokens, boolean isOnboarded, boolean isCourseTutorialCompleted) {
        return new TokenResponse(
                tokens.accessToken(),
                tokens.refreshToken(),
                isOnboarded,
                isCourseTutorialCompleted);
    }
}
