package cmc.rodi.global.auth.dto;

import cmc.rodi.global.auth.vo.Tokens;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

/**
 * 소셜 로그인 응답. {@code status=SUCCESS}면 토큰이 채워지고, {@code WITHDRAWAL_PENDING}(탈퇴 유예기간 내 재로그인)이면 토큰 대신
 * 복구 안내 정보가 채워진다. 클라이언트는 status로 분기한다.
 */
public record SocialLoginResponse(
        @Schema(description = "SUCCESS | WITHDRAWAL_PENDING") Status status,
        @Schema(description = "API 인증용 access token(JWT). PENDING이면 null") String accessToken,
        @Schema(description = "재발급용 refresh token. PENDING이면 null") String refreshToken,
        @Schema(description = "이번 요청으로 새로 가입했는지. PENDING이면 false") @JsonProperty("isNewMember")
                boolean isNewMember,
        @Schema(
                        description =
                                "온보딩을 마쳤는지 — 온보딩 화면 분기 기준. 신규 가입과 토큰 없는 응답(PENDING)은 false."
                                        + " isNewMember로는 가입 후 온보딩 중 이탈한 회원의 재로그인을 가려낼 수 없다.")
                @JsonProperty("isOnboarded")
                boolean isOnboarded,
        @Schema(description = "가입 시 부여된 닉네임. PENDING이면 null") String nickname,
        @Schema(description = "탈퇴 요청 시각(PENDING만)") LocalDateTime withdrawalRequestedAt,
        @Schema(description = "복구 가능 마감 시각(PENDING만)") LocalDateTime recoverableUntil) {

    public enum Status {
        SUCCESS,
        WITHDRAWAL_PENDING
    }

    public static SocialLoginResponse success(
            Tokens tokens, boolean isNewMember, boolean isOnboarded, String nickname) {
        return new SocialLoginResponse(
                Status.SUCCESS,
                tokens.accessToken(),
                tokens.refreshToken(),
                isNewMember,
                isOnboarded,
                nickname,
                null,
                null);
    }

    public static SocialLoginResponse withdrawalPending(
            LocalDateTime withdrawalRequestedAt, LocalDateTime recoverableUntil) {
        return new SocialLoginResponse(
                Status.WITHDRAWAL_PENDING,
                null,
                null,
                false,
                false,
                null,
                withdrawalRequestedAt,
                recoverableUntil);
    }
}
