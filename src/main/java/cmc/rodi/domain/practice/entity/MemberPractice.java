package cmc.rodi.domain.practice.entity;

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

    /** 방문 처리. 호출될 때마다 횟수가 오르므로 한 번의 방문에서 중복 호출하지 않는 건 클라이언트 몫이다. */
    public void markVisited(LocalDateTime now) {
        this.status = PracticeStatus.VISITED;
        this.visitCount += 1;
        this.visitedAt = now;
        clearSkipReason();
    }

    /** 미방문 처리. 사유는 한 번 저장하면 수정할 수 없다(호출부가 {@link #hasSkipReason()}로 막는다). */
    public void markNotVisited(SkipReason reason, String detail) {
        this.status = PracticeStatus.NOT_VISITED;
        this.skipReason = reason;
        this.skipDetail = reason.requiresTextInput() ? detail : null;
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
