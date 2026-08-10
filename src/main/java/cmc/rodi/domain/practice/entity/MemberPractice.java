package cmc.rodi.domain.practice.entity;

import cmc.rodi.domain.member.entity.Member;
import cmc.rodi.domain.place.entity.Course;
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

    /** 한 번이라도 방문 인증에 성공했는지. 후기의 방문 인증 배지 판정 기준이며 내려가지 않는다. */
    @Column(nullable = false)
    private boolean verified;

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
     * <p>{@code certifiedMeters}는 앱이 GPS로 측정한 인정 주행거리다(측정 없이 "다녀왔어요"만 누르면 0). 필요 거리에 도달하면 인증되고, 한 번
     * 인증된 항목은 이후 미인증 방문이 있어도 인증 상태를 유지한다.
     *
     * @return 이번 방문으로 인증되었는지(이미 인증된 항목이면 이번 회차 기준)
     */
    public boolean markVisited(LocalDateTime now, int certifiedMeters) {
        this.status = PracticeStatus.VISITED;
        this.visitCount += 1;
        this.visitedAt = now;
        this.certifiedDistanceMeters += certifiedMeters;
        clearSkipReason();

        boolean certifiedNow =
                VisitCertification.isCertified(courseDistanceMeters(), certifiedMeters);
        if (certifiedNow) {
            this.verified = true;
        }
        return certifiedNow;
    }

    /** 이번 방문의 인증에 필요한 거리(m). 주행거리가 없는 장소(주차장)는 0 — 인증 대상이 아니다. */
    public int requiredCertificationMeters() {
        return VisitCertification.requiredMeters(courseDistanceMeters());
    }

    /** 이번 방문에서 레벨 누적에 반영할 거리(m). 코스 전체 거리를 넘지 않고, 주차장은 0(스펙 012). */
    public long accruableMeters(int certifiedMeters) {
        return VisitCertification.accruableMeters(courseDistanceMeters(), certifiedMeters);
    }

    private Integer courseDistanceMeters() {
        return place instanceof Course course ? course.getDistanceMeters() : null;
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
