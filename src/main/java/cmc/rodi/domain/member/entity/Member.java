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
import java.util.List;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

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

    /** 누적 인정 주행거리(m). 레벨 승급 기준이며 <b>차감되지 않는다</b> — 연습기록을 지워도 그대로다(스펙 012). */
    @Column(name = "total_distance_meters", nullable = false)
    private long totalDistanceMeters;

    /**
     * 홈 정렬 필터(스펙 007). 회원이 선택한 연습유형 집합(카테고리는 클라 전용이라 풀린 값만 저장). NULL/빈 값 = 필터 없음(거리순). 인증된 목록·검색에서만
     * 적용된다.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "filter_tags", columnDefinition = "jsonb")
    private List<PracticeType> filterTags;

    /** 코스 등록 튜토리얼 완료 시각(스펙 017). NULL이면 아직 보지 않았다. */
    @Column(name = "course_tutorial_completed_at")
    private LocalDateTime courseTutorialCompletedAt;

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

    /**
     * 온보딩 완료 — 클라이언트가 계산한 레벨과 운전 목표를 반영한다.
     *
     * <p>배정된 레벨의 최소 기준값으로 <b>누적 거리를 맞춘다</b>. 그러지 않으면 Rookie로 시작한 회원의 게이지가 Seed 구간부터 그려져, 이미 인정받은
     * 경험이 없던 일이 된다(스펙 012).
     */
    public void applyOnboarding(Level level, String drivingGoal) {
        this.level = level;
        this.drivingGoal = drivingGoal;
        if (level != null) {
            this.totalDistanceMeters = Math.max(this.totalDistanceMeters, level.getStartMeters());
        }
    }

    /**
     * 인정 주행거리를 누적하고 도달한 레벨로 올린다. <b>승급했으면 true</b>를 돌려줘 호출부가 응답에 실을 수 있게 한다.
     *
     * <p>한 번에 두 단계 이상 오를 수 있고(긴 코스), <b>레벨은 내려가지 않는다</b> — 온보딩으로 받은 레벨이 누적 거리보다 높을 수 있어서, 계산된 레벨이
     * 현재보다 낮으면 무시한다.
     */
    public boolean addDistance(long meters) {
        if (meters <= 0) {
            return false;
        }
        this.totalDistanceMeters += meters;

        Level reached = Level.of(this.totalDistanceMeters);
        if (level == null || reached.ordinal() <= level.ordinal()) {
            return false;
        }
        this.level = reached;
        return true;
    }

    /** 현재 레벨 구간의 진행률(0~100). 레벨이 없으면(온보딩 전) 0. */
    public int levelProgressPercent() {
        return level == null ? 0 : level.progressPercent(totalDistanceMeters);
    }

    /** 운전 목표 수정(마이페이지). 빈값이면 목표 없음(null)으로 지운다. */
    public void updateDrivingGoal(String drivingGoal) {
        this.drivingGoal = (drivingGoal == null || drivingGoal.isBlank()) ? null : drivingGoal;
    }

    /** 홈 정렬 필터 전체 교체. 빈 값이면 필터 없음(null)으로 지운다. */
    public void updateFilterTags(List<PracticeType> filterTags) {
        this.filterTags =
                (filterTags == null || filterTags.isEmpty()) ? null : List.copyOf(filterTags);
    }

    /** 코스 등록 튜토리얼 완료. 여러 번 호출해도 최초 완료 시각을 유지한다. */
    public void completeCourseTutorial(LocalDateTime now) {
        if (courseTutorialCompletedAt == null) {
            courseTutorialCompletedAt = now;
        }
    }

    public boolean isCourseTutorialCompleted() {
        return courseTutorialCompletedAt != null;
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
