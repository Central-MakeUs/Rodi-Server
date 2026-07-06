package cmc.rodi.domain.member.entity;

import cmc.rodi.global.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 회원. 소셜 로그인으로 가입하며, 신원은 {@code social_account}가 별도 관리한다. 탈퇴는 soft delete(ADR 0004). */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "member")
public class Member extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 닉네임은 가입 시엔 비어 있고 온보딩에서 설정한다. */
    @Column(unique = true, length = 30)
    private String nickname;

    @Column(length = 255)
    private String email;

    /** 탈퇴 요청 시각(soft delete). 유예기간 동안 복구 대상(ADR 0004). */
    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    /** 개인정보 익명화 시각. 유예기간 경과 후 배치가 설정한다. */
    @Column(name = "anonymized_at")
    private LocalDateTime anonymizedAt;

    @Builder
    private Member(String nickname, String email) {
        this.nickname = nickname;
        this.email = email;
    }

    /** 소셜 로그인으로 신규 가입. 닉네임 없이 식별정보만으로 생성하고, 닉네임은 온보딩에서 채운다. */
    public static Member createBySocial(String email) {
        return Member.builder().email(email).build();
    }

    /** 탈퇴 요청 — soft delete. 유예기간 내 복구 가능. */
    public void withdraw(LocalDateTime now) {
        this.deletedAt = now;
    }

    /** 복구 — 탈퇴 요청 취소(유예기간 내에서만 의미 있음). */
    public void restore() {
        this.deletedAt = null;
    }

    /** 익명화 — 유예기간 경과 후 개인정보 제거. */
    public void anonymize(LocalDateTime now) {
        this.nickname = null;
        this.email = null;
        this.anonymizedAt = now;
    }

    public boolean isWithdrawn() {
        return deletedAt != null;
    }

    /** 탈퇴 진행 상태 파생. 복구 유예기간(recoverableWindow) 내면 PENDING, 경과면 LOCKED. */
    public MemberStatus withdrawalState(LocalDateTime now, Duration recoverableWindow) {
        if (deletedAt == null) {
            return MemberStatus.ACTIVE;
        }
        if (now.isBefore(deletedAt.plus(recoverableWindow))) {
            return MemberStatus.WITHDRAWAL_PENDING;
        }
        return MemberStatus.WITHDRAWAL_LOCKED;
    }
}
