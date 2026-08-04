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

    private static final int MAX_CONTENT_LENGTH = 1000;

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

    @Column(nullable = false, length = MAX_CONTENT_LENGTH)
    private String content;

    /** 주의사항(선택, 길이 제한 없음). */
    @Column(columnDefinition = "text")
    private String caution;

    /** 작성 당시 회원 레벨(스냅샷). 회원 레벨이 바뀌어도 불변. */
    @Enumerated(EnumType.STRING)
    @Column(name = "member_level", nullable = false, length = 20)
    private Level memberLevel;

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
            Level memberLevel) {
        this.place = place;
        this.member = member;
        this.recommended = recommended;
        this.difficulty = difficulty;
        this.congestion = congestion;
        this.practiceMethod = practiceMethod;
        this.content = content;
        this.caution = blankToNull(caution);
        this.memberLevel = memberLevel;
    }

    /** 후기 내용 교체(전체 수정). 레벨·작성자·대상 장소는 바뀌지 않는다. */
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
        this.content = content;
        this.caution = blankToNull(caution);
    }

    public boolean isOwnedBy(Long memberId) {
        return member.getId().equals(memberId);
    }

    /** 수정 가능 여부 — 작성 당시 레벨과 현재 레벨이 같아야 한다. */
    public boolean isEditableAt(Level currentLevel) {
        return memberLevel == currentLevel;
    }

    private static String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value;
    }
}
