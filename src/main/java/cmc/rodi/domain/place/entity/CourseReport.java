package cmc.rodi.domain.place.entity;

import cmc.rodi.domain.member.entity.Member;
import cmc.rodi.global.common.entity.BaseEntity;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
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
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 코스 신고(스펙 025). 코스 상세에서 직접 한 신고와 '별로에요' 후기에서 온 신고를 함께 담는다 — 관리자가 한 곳에서 본다.
 *
 * <p>{@code reviewId}가 있으면 그 후기에서 온 신고다(후기당 1건, 후기 삭제 시 DB가 함께 지운다). 직접 신고는 같은 회원·같은 코스에 1건이며
 * partial unique index가 막는다. 후기는 id로만 들고 있다 — review 도메인이 이미 place를 참조하므로 엔티티 연관을 걸면 도메인 간 순환이 생긴다.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "course_report")
public class CourseReport extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "course_id")
    private Course course;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reporter_id")
    private Member reporter;

    /** 이 신고를 만든 '별로에요' 후기(null=직접 신고). */
    @Column(name = "review_id")
    private Long reviewId;

    /** 문제 항목(1개 이상). course_report_issue(course_report_id, issue)에 enum 이름으로 저장한다. */
    @ElementCollection
    @CollectionTable(
            name = "course_report_issue",
            joinColumns = @JoinColumn(name = "course_report_id"))
    @Column(name = "issue", length = 30)
    @Enumerated(EnumType.STRING)
    private Set<CourseIssue> issues = new LinkedHashSet<>();

    /** 기타 직접 입력. {@link CourseIssue#OTHER}가 있을 때만 남는다. 길이는 grapheme 기준이라 요청 검증이 책임진다. */
    @Column(columnDefinition = "text")
    private String detail;

    private CourseReport(
            Course course,
            Member reporter,
            Long reviewId,
            Collection<CourseIssue> issues,
            String detail) {
        this.course = course;
        this.reporter = reporter;
        this.reviewId = reviewId;
        replaceIssues(issues, detail);
    }

    /** 코스 상세에서 직접 한 신고. */
    public static CourseReport direct(
            Course course, Member reporter, Collection<CourseIssue> issues, String detail) {
        return new CourseReport(course, reporter, null, issues, detail);
    }

    /** '별로에요' 후기에서 온 신고. 신고자는 후기 작성자다. */
    public static CourseReport fromReview(
            Course course,
            Member reporter,
            Long reviewId,
            Collection<CourseIssue> issues,
            String detail) {
        return new CourseReport(course, reporter, reviewId, issues, detail);
    }

    /**
     * 문제 항목·기타 입력을 통째로 바꾼다('별로에요' 후기 수정). 중복 항목은 하나로 합치고, 기타가 없으면 직접 입력은 버린다.
     *
     * @throws IllegalArgumentException 항목이 비었거나 기타인데 직접 입력이 없을 때 — 요청 검증이 먼저 막는 규칙의 최종 방어선
     */
    public void replaceIssues(Collection<CourseIssue> issues, String detail) {
        if (issues == null || issues.isEmpty()) {
            throw new IllegalArgumentException("코스 문제 항목은 1개 이상이어야 합니다");
        }
        boolean hasOther = issues.contains(CourseIssue.OTHER);
        String normalized = (detail == null || detail.isBlank()) ? null : detail.trim();
        if (hasOther && normalized == null) {
            throw new IllegalArgumentException("기타 항목은 직접 입력이 필요합니다");
        }
        this.issues.clear();
        this.issues.addAll(issues);
        this.detail = hasOther ? normalized : null;
    }

    public boolean isFromReview() {
        return reviewId != null;
    }
}
