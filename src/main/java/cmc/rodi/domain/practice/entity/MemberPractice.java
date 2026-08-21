package cmc.rodi.domain.practice.entity;

import cmc.rodi.domain.member.entity.Level;
import cmc.rodi.domain.member.entity.Member;
import cmc.rodi.domain.place.entity.Place;
import cmc.rodi.global.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 연습 항목(회원 ↔ 장소). 코스 상세 [연습하기]로 담고 이후 방문 여부를 기록한다. 코스·주차장 공통(place FK).
 *
 * <p>같은 장소는 <b>한 행</b>만 두고 재연습은 {@code visitCount}로 누적한다 — 목록이 같은 코스로 도배되지 않고 "이 코스 3번 연습함"을 바로 보여줄
 * 수 있다. 방문 판정은 클라이언트가 이동 추적으로 수행하며, 이 엔티티는 그 결과를 기록만 한다.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(
        name = "member_practice",
        uniqueConstraints = @UniqueConstraint(columnNames = {"member_id", "place_id"}))
public class MemberPractice extends BaseEntity {

    /** 직전 방문 후 이 시간 안의 재호출은 같은 방문으로 본다(재시도·연타 흡수, ADR 0012). */
    public static final int VISIT_COOLDOWN_MINUTES = 10;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id")
    private Member member;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "place_id")
    private Place place;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PracticeStatus status;

    /** 다녀온 횟수. VISITED로 바뀔 때마다 +1. */
    @Column(name = "visit_count", nullable = false)
    private int visitCount;

    /** 마지막 방문 시각(한 번도 안 다녀왔으면 null). */
    @Column(name = "visited_at")
    private LocalDateTime visitedAt;

    /** 이 항목에서 누적된 인정 주행거리(m). 앱이 GPS로 측정해 보낸 값의 합. */
    @Column(name = "certified_distance_meters", nullable = false)
    private long certifiedDistanceMeters;

    /**
     * 마지막으로 인증에 성공했을 때의 회원 레벨(null=인증 이력 없음).
     *
     * <p>후기의 인증 배지는 <b>작성 당시 레벨에서 인증했는지</b>로 판정한다 — 레벨이 오르면 그 레벨에서 다시 인증해야 한다. 그래서 boolean이 아니라 레벨을
     * 남긴다. 레벨은 내려가지 않으므로 이 값은 항상 회원의 현재 레벨 이하다.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "verified_level", length = 20)
    private Level verifiedLevel;

    @Enumerated(EnumType.STRING)
    @Column(name = "skip_reason", length = 30)
    private SkipReason skipReason;

    @Column(name = "skip_detail", length = SkipReason.TEXT_INPUT_MAX_LENGTH)
    private String skipDetail;

    @Builder
    private MemberPractice(Member member, Place place) {
        this.member = member;
        this.place = place;
        this.status = PracticeStatus.PLANNED;
        this.visitCount = 0;
    }

    public boolean isOwnedBy(Long memberId) {
        return member.getId().equals(memberId);
    }

    /** 목록 정렬 기준값 — 방문 이력이 없으면 담은 시각을 쓴다(목록 맨 아래로 밀리지 않게). */
    public LocalDateTime recentActivityAt() {
        return visitedAt != null ? visitedAt : getCreatedAt();
    }

    /** 이미 담긴 장소를 다시 담았을 때 — 상태만 예정으로 되돌린다. 연습 횟수는 유지. */
    public void replan() {
        this.status = PracticeStatus.PLANNED;
        clearSkipReason();
    }

    /**
     * 방문 처리. 호출될 때마다 횟수가 오르므로 한 번의 방문에서 중복 호출하지 않는 건 클라이언트 몫이다.
     *
     * <p>{@code certifiedMeters}는 앱이 GPS로 측정한 인정 주행거리다(측정 없이 "다녀왔어요"만 누르면 0). 필요 거리에 도달하면 인증된다.
     *
     * @param currentLevel 이 방문을 시작한 시점의 회원 레벨. 이 주행 거리로 곧바로 승급하더라도 <b>승급 전 레벨</b>로 인증을 기록한다 — 새 레벨의
     *     인증은 새 레벨에서 받는다.
     * @return 이번 방문으로 인증되었는지
     */
    public boolean markVisited(LocalDateTime now, int certifiedMeters, Level currentLevel) {
        int meters = countableMeters(certifiedMeters);
        this.status = PracticeStatus.VISITED;
        this.visitCount += 1;
        this.visitedAt = now;
        this.certifiedDistanceMeters += meters;
        clearSkipReason();

        boolean certifiedNow = VisitCertification.isCertified(courseDistanceMeters(), meters);
        if (certifiedNow) {
            this.verifiedLevel = currentLevel;
        }
        return certifiedNow;
    }

    /**
     * 이 장소에서 의미가 있는 측정값(m). 주행거리가 없는 장소(주차장)는 <b>0</b>이다 — 인증도 레벨 누적도 성립하지 않는데 값만 쌓이면, 아무 데도 쓰이지 않는
     * 수치가 컬럼과 응답에 남는다.
     */
    public int countableMeters(int certifiedMeters) {
        Integer distance = courseDistanceMeters();
        return (distance == null || distance <= 0) ? 0 : Math.max(0, certifiedMeters);
    }

    /** 그 레벨에서 인증받은 항목인지 — 후기의 인증 배지 판정 기준. */
    public boolean isVerifiedAt(Level level) {
        return verifiedLevel != null && verifiedLevel == level;
    }

    /**
     * 직전 방문 직후의 중복 호출인지. 방문 기록은 앱이 이동 추적 결과로 자동 호출해 재시도·연타가 그대로 들어오는데, 거리가 레벨로 환산되고 레벨은 내려가지 않아 되돌릴
     * 수 없다(ADR 0012).
     *
     * <p>같은 코스를 {@value #VISIT_COOLDOWN_MINUTES}분 안에 두 번 완주할 일은 없다고 보고, 그 안의 호출은 같은 방문으로 취급한다.
     */
    public boolean isWithinVisitCooldown(LocalDateTime now) {
        return visitedAt != null && visitedAt.plusMinutes(VISIT_COOLDOWN_MINUTES).isAfter(now);
    }

    /** 이번 방문의 인증에 필요한 거리(m). 주행거리가 없는 장소(주차장)는 0 — 인증 대상이 아니다. */
    public int requiredCertificationMeters() {
        return VisitCertification.requiredMeters(courseDistanceMeters());
    }

    /** 이번 방문에서 레벨 누적에 반영할 거리(m). 코스 전체 거리를 넘지 않고, 주차장은 0(스펙 012). */
    public long accruableMeters(int certifiedMeters) {
        return VisitCertification.accruableMeters(courseDistanceMeters(), certifiedMeters);
    }

    /** 프록시로 와도 안전하도록 다형 메서드로 읽는다({@link Place#drivingDistanceMeters()} 주석 참고). */
    private Integer courseDistanceMeters() {
        return place.drivingDistanceMeters();
    }

    /** 미방문 처리. 사유는 별도 API로 뒤이어 제출되므로 여기서는 상태만 바꾼다. */
    public void markNotVisited() {
        this.status = PracticeStatus.NOT_VISITED;
    }

    /** 미방문 사유 저장. 한 번 저장하면 수정할 수 없다(호출부가 {@link #hasSkipReason()}로 막는다). */
    public void applySkipReason(SkipReason reason, String detail) {
        this.skipReason = reason;
        this.skipDetail = reason.requiresTextInput() ? detail : null;
    }

    public boolean isNotVisited() {
        return status == PracticeStatus.NOT_VISITED;
    }

    public boolean hasSkipReason() {
        return skipReason != null;
    }

    /** 한 번이라도 다녀왔는지 — 후기 방문 인증(스펙 011 #6)의 판정 기준. */
    public boolean hasVisited() {
        return visitCount > 0;
    }

    private void clearSkipReason() {
        this.skipReason = null;
        this.skipDetail = null;
    }
}
