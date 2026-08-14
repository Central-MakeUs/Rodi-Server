package cmc.rodi.global.auth.dto;

import cmc.rodi.global.auth.vo.Tokens;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

/**
 * 소셜 로그인·복구 응답. 클라이언트는 {@code status} 하나로 분기한다.
 *
 * <ul>
 *   <li>{@code SUCCESS} — 토큰 발급
 *   <li>{@code WITHDRAWAL_PENDING} — 탈퇴 유예기간 내 재로그인. 토큰 대신 복구 안내
 *   <li>{@code WITHDRAWAL_LOCKED} — 복구 기간이 지나 재가입을 기다리는 중. 토큰 대신 재가입 가능 시각
 * </ul>
 *
 * <p>토큰이 없는 두 상태를 <b>오류가 아니라 200</b>으로 내리는 이유 — 전역 예외 핸들러가 {@code ApiResponse<Void>}를 돌려줘 에러 응답에는
 * 날짜를 실을 수 없다. 앱이 "언제부터 다시 가입할 수 있는지"를 그리려면 값이 필요하다.
 */
public record SocialLoginResponse(
        @Schema(description = "SUCCESS | WITHDRAWAL_PENDING | WITHDRAWAL_LOCKED") Status status,
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
        @Schema(description = "탈퇴 요청 시각(PENDING·LOCKED만)") LocalDateTime withdrawalRequestedAt,
        @Schema(description = "복구 가능 마감 시각(PENDING만)") LocalDateTime recoverableUntil,
        @Schema(description = "같은 계정으로 다시 가입할 수 있는 시각(LOCKED만)") LocalDateTime reRegisterableAt) {

    public enum Status {
        SUCCESS,
        WITHDRAWAL_PENDING,
        /** 복구 기간은 지났고 재가입은 아직 이른 구간. 오류가 아니라 안내라 200으로 내려간다. */
        WITHDRAWAL_LOCKED
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
                recoverableUntil,
                null);
    }

    /** 복구 기간이 지나 재가입을 기다리는 계정. 토큰을 주지 않지만 오류도 아니다 — 사용자가 할 수 있는 일이 "기다리기"뿐이라, 언제까지인지를 값으로 준다. */
    public static SocialLoginResponse withdrawalLocked(
            LocalDateTime withdrawalRequestedAt, LocalDateTime reRegisterableAt) {
        return new SocialLoginResponse(
                Status.WITHDRAWAL_LOCKED,
                null,
                null,
                false,
                false,
                null,
                withdrawalRequestedAt,
                null,
                reRegisterableAt);
    }
}
