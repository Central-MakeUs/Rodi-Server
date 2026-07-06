package cmc.rodi.domain.member.entity;

/** 회원의 탈퇴 진행 상태(파생값). deletedAt·경과시간으로 결정된다. */
public enum MemberStatus {
    ACTIVE,
    WITHDRAWAL_PENDING, // 탈퇴 요청 후 유예기간 내 — 복구 가능
    WITHDRAWAL_LOCKED // 유예기간 경과 — 복구 불가(재가입 대기)
}
