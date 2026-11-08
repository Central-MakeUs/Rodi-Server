package cmc.rodi.domain.place.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cmc.rodi.domain.member.entity.Member;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.PrecisionModel;

/** 코스 신고 항목·기타 입력 규칙 — 스펙 025. */
class CourseReportTest {

    private static final GeometryFactory GEO = new GeometryFactory(new PrecisionModel(), 4326);

    private final Course course =
            Course.builder()
                    .name("신고 코스")
                    .location(GEO.createPoint(new Coordinate(127.0, 37.5)))
                    .build();
    private final Member reporter = Member.createBySocial("report@kakao.com");

    @Test
    @DisplayName("직접 신고는 후기 연결이 없고, 후기 신고는 후기 id를 갖는다")
    void 출처_구분() {
        CourseReport direct =
                CourseReport.direct(course, reporter, List.of(CourseIssue.CONSTRUCTION), null);
        CourseReport fromReview =
                CourseReport.fromReview(
                        course, reporter, 42L, List.of(CourseIssue.CONSTRUCTION), null);

        assertThat(direct.isFromReview()).isFalse();
        assertThat(direct.getReviewId()).isNull();
        assertThat(fromReview.isFromReview()).isTrue();
        assertThat(fromReview.getReviewId()).isEqualTo(42L);
    }

    @Test
    @DisplayName("중복 항목은 하나로 합친다")
    void 중복_항목_합침() {
        CourseReport report =
                CourseReport.direct(
                        course,
                        reporter,
                        List.of(CourseIssue.ROAD_INFO_MISMATCH, CourseIssue.ROAD_INFO_MISMATCH),
                        null);

        assertThat(report.getIssues()).containsExactly(CourseIssue.ROAD_INFO_MISMATCH);
    }

    @Test
    @DisplayName("기타가 있으면 직접 입력을 앞뒤 공백을 잘라 남긴다")
    void 기타_입력_저장() {
        CourseReport report =
                CourseReport.direct(course, reporter, List.of(CourseIssue.OTHER), "  입구가 잠겨 있어요 ");

        assertThat(report.getDetail()).isEqualTo("입구가 잠겨 있어요");
    }

    @Test
    @DisplayName("기타가 없으면 직접 입력을 버린다")
    void 기타_없으면_입력_무시() {
        CourseReport report =
                CourseReport.direct(
                        course, reporter, List.of(CourseIssue.NOT_FOR_PRACTICE), "무시될 내용");

        assertThat(report.getDetail()).isNull();
    }

    @Test
    @DisplayName("항목이 비었거나 기타인데 직접 입력이 없으면 만들 수 없다")
    void 잘못된_입력_거부() {
        assertThatThrownBy(() -> CourseReport.direct(course, reporter, List.of(), null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(
                        () ->
                                CourseReport.direct(
                                        course, reporter, List.of(CourseIssue.OTHER), " "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("항목 교체는 이전 항목·입력을 통째로 바꾼다")
    void 항목_교체() {
        CourseReport report =
                CourseReport.fromReview(course, reporter, 1L, List.of(CourseIssue.OTHER), "처음 입력");

        report.replaceIssues(List.of(CourseIssue.VEHICLE_RESTRICTED), "버려질 입력");

        assertThat(report.getIssues()).containsExactly(CourseIssue.VEHICLE_RESTRICTED);
        assertThat(report.getDetail()).isNull();
    }
}
