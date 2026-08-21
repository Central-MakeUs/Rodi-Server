package cmc.rodi.domain.review.entity;

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
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 장소 후기(place 1:N, 코스·주차장 공통). 같은 회원이 같은 장소에 여러 번 쓸 수 있다(유니크 제약 없음).
 *
 * <p>{@code memberLevel}은 <b>작성 시점</b> 회원 레벨의 스냅샷이라 이후 회원이 레벨업해도 변하지 않는다. 레벨별 목록·난이도 분포의 기준이자, 레벨이
 * 바뀐 뒤 이전 레벨 후기를 수정하지 못하게 하는 판정 기준이다(삭제는 허용).
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "review")
public class Review extends BaseEntity {

    public static final int MAX_CONTENT_LENGTH = 150;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "place_id")
    private Place place;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id")
    private Member member;

    /** 추천/비추천. */
    @Column(name = "is_recommended", nullable = false)
    private boolean recommended;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Difficulty difficulty;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Congestion congestion;

    @Enumerated(EnumType.STRING)
    @Column(name = "practice_method", nullable = false, length = 20)
    private PracticeMethod practiceMethod;

    /** 후기 내용(선택). 글 없이 평가만 남길 수 있다 — 빈 문자열은 저장하지 않고 null로 정규화한다. */
    @Column(length = MAX_CONTENT_LENGTH)
    private String content;

    /** 주의사항(선택, 길이 제한 없음). */
    @Column(columnDefinition = "text")
    private String caution;

    /** 작성 당시 회원 레벨(스냅샷). 회원 레벨이 바뀌어도 불변. */
    @Enumerated(EnumType.STRING)
    @Column(name = "member_level", nullable = false, length = 20)
    private Level memberLevel;

    /** 신고 누적으로 비공개 처리된 시각(null=공개). 목록·요약에서 빠지고 작성자 본인에게만 보인다. */
    @Column(name = "hidden_at")
    private LocalDateTime hiddenAt;

    /**
     * 작성 당시 레벨에서 GPS 방문 인증을 받았는지(스냅샷). "다녀왔어요"만 누른 기록은 인증으로 보지 않는다.
     *
     * <p>스냅샷이라 이후 레벨이 오르거나 연습 항목을 지워도 값은 그대로다. 다만 <b>새 후기</b>는 그 레벨에서 다시 인증받아야 true가 된다(스펙 013).
     */
    @Column(name = "is_verified_visit", nullable = false)
    private boolean verifiedVisit;

    @Builder
    private Review(
            Place place,
            Member member,
            boolean recommended,
            Difficulty difficulty,
            Congestion congestion,
            PracticeMethod practiceMethod,
            String content,
            String caution,
            Level memberLevel,
            boolean verifiedVisit) {
        this.place = place;
        this.member = member;
        this.recommended = recommended;
        this.difficulty = difficulty;
        this.congestion = congestion;
        this.practiceMethod = practiceMethod;
        this.content = blankToNull(content);
        this.caution = blankToNull(caution);
        this.memberLevel = memberLevel;
        this.verifiedVisit = verifiedVisit;
    }

    /** 후기 내용 교체(전체 수정). 레벨·작성자·대상 장소는 바뀌지 않는다. 내용을 비워 보내면 지워진다(PUT은 전체 교체). */
    public void update(
            boolean recommended,
            Difficulty difficulty,
            Congestion congestion,
            PracticeMethod practiceMethod,
            String content,
            String caution) {
        this.recommended = recommended;
        this.difficulty = difficulty;
        this.congestion = congestion;
        this.practiceMethod = practiceMethod;
        this.content = blankToNull(content);
        this.caution = blankToNull(caution);
    }

    public boolean isOwnedBy(Long memberId) {
        return member.getId().equals(memberId);
    }

    /** 신고 누적으로 비공개 처리. 이미 비공개면 최초 판정 시각을 유지한다. */
    public void hide(LocalDateTime now) {
        if (hiddenAt == null) {
            hiddenAt = now;
        }
    }

    public boolean isHidden() {
        return hiddenAt != null;
    }

    /** 수정 가능 여부 — 작성 당시 레벨과 현재 레벨이 같아야 한다. */
    public boolean isEditableAt(Level currentLevel) {
        return memberLevel == currentLevel;
    }

    private static String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value;
    }
}
