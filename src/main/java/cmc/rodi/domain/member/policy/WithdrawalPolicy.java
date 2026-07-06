package cmc.rodi.domain.member.policy;

import java.time.Duration;

/** 단계적 탈퇴 정책 기간. (복구 3일 / 재가입 10일 — PM 정책 2026-07-07) */
public final class WithdrawalPolicy {

    /** 탈퇴 요청 후 이 기간 내에는 복구 가능. 경과 시 익명화 대상. */
    public static final Duration RECOVERABLE_WINDOW = Duration.ofDays(3);

    /** 탈퇴 요청 후 이 기간 경과 시 소셜 식별자 해제 → 동일 계정 재가입 허용. */
    public static final Duration RE_REGISTERABLE_WINDOW = Duration.ofDays(10);

    private WithdrawalPolicy() {}
}
