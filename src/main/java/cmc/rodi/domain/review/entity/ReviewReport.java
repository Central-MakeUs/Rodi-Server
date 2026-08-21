package cmc.rodi.domain.review.entity;

import cmc.rodi.domain.member.entity.Member;
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
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 후기 신고. 같은 후기·같은 신고자는 1건(중복 신고는 멱등). 접수·보관만 하고 후기 노출은 바꾸지 않는다. */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(
        name = "review_report",
        uniqueConstraints = @UniqueConstraint(columnNames = {"review_id", "reporter_id"}))
public class ReviewReport extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "review_id")
    private Review review;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reporter_id")
    private Member reporter;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ReportReason reason;

    @Column(columnDefinition = "text")
    private String detail;

    @Builder
    private ReviewReport(Review review, Member reporter, ReportReason reason, String detail) {
        this.review = review;
        this.reporter = reporter;
        this.reason = reason;
        this.detail = (detail == null || detail.isBlank()) ? null : detail;
    }
}
