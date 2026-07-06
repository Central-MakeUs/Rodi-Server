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
        @Schema(description = "신규 가입 여부(온보딩 분기). PENDING이면 false") @JsonProperty("isNewMember")
                boolean isNewMember,
        @Schema(description = "탈퇴 요청 시각(PENDING만)") LocalDateTime withdrawalRequestedAt,
        @Schema(description = "복구 가능 마감 시각(PENDING만)") LocalDateTime recoverableUntil) {

    public enum Status {
        SUCCESS,
        WITHDRAWAL_PENDING
    }

    public static SocialLoginResponse success(Tokens tokens, boolean isNewMember) {
        return new SocialLoginResponse(
                Status.SUCCESS,
                tokens.accessToken(),
                tokens.refreshToken(),
                isNewMember,
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
                withdrawalRequestedAt,
                recoverableUntil);
    }
}
