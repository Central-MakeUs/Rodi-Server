package cmc.rodi.domain.member.entity;

import cmc.rodi.global.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

    /** 닉네임은 가입 시 후보 풀에서 무작위로 부여한다(회원 간 유일). */
    @Column(unique = true, length = 30)
    private String nickname;

    @Column(length = 255)
    private String email;

    /** 배정 레벨. 온보딩에서 클라이언트가 변환해 보낸 값을 저장(마이페이지·추천에 사용). 온보딩 전 NULL. */
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private Level level;

    /** 운전 목표(마이페이지 노출). 온보딩 추가 정보라 선택(NULL 가능). */
    @Column(name = "driving_goal", length = 30)
    private String drivingGoal;

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

    /** 가입 시 후보 풀에서 고른 닉네임을 부여한다. */
    public void assignNickname(String nickname) {
        this.nickname = nickname;
    }

    /** 온보딩 완료 — 클라이언트가 계산한 레벨과 운전 목표를 반영한다. */
    public void applyOnboarding(Level level, String drivingGoal) {
        this.level = level;
        this.drivingGoal = drivingGoal;
    }

    /** 운전 목표 수정(마이페이지). 빈값이면 목표 없음(null)으로 지운다. */
    public void updateDrivingGoal(String drivingGoal) {
        this.drivingGoal = (drivingGoal == null || drivingGoal.isBlank()) ? null : drivingGoal;
    }

    /** 익명화 — 유예기간 경과 후 개인정보 제거. */
    public void anonymize(LocalDateTime now) {
        this.nickname = null;
        this.email = null;
        this.drivingGoal = null;
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
