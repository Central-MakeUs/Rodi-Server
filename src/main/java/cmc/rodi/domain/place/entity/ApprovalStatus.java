package cmc.rodi.domain.place.entity;

/**
 * 사용자 등록 코스의 승인 상태(스펙 014). 관리자가 승인해야 전체 목록·검색에 노출된다.
 *
 * <p>운영자가 시딩한 코스는 {@link #APPROVED}로 시작한다(V24 backfill).
 */
public enum ApprovalStatus {
    PENDING, // 승인 대기 — 등록자만 볼 수 있다
    APPROVED, // 승인 — 전체 노출
    REJECTED // 반려 — 등록자에게 상태만 보인다(사유는 추후)
}
