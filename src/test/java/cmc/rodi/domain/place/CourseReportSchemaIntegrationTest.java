package cmc.rodi.domain.place;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cmc.rodi.domain.member.entity.Level;
import cmc.rodi.domain.member.entity.Member;
import cmc.rodi.domain.member.repository.MemberRepository;
import cmc.rodi.domain.place.entity.Course;
import cmc.rodi.domain.place.entity.CourseIssue;
import cmc.rodi.domain.place.entity.CourseReport;
import cmc.rodi.domain.place.repository.CourseReportRepository;
import cmc.rodi.domain.place.repository.CourseRepository;
import cmc.rodi.domain.review.entity.PracticeMethod;
import cmc.rodi.domain.review.entity.Review;
import cmc.rodi.domain.review.repository.ReviewRepository;
import cmc.rodi.support.TestcontainersConfiguration;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

/** 코스 신고 스키마(V31): 저장·조회, 직접 신고 중복 제한, 후기 신고 공존, 후기당 1건, 항목 코드 CHECK, 후기 삭제 시 함께 삭제. */
@SpringBootTest
@Transactional
@Import(TestcontainersConfiguration.class)
class CourseReportSchemaIntegrationTest {

    private static final GeometryFactory GEO = new GeometryFactory(new PrecisionModel(), 4326);

    @Autowired CourseReportRepository courseReportRepository;
    @Autowired CourseRepository courseRepository;
    @Autowired MemberRepository memberRepository;
    @Autowired ReviewRepository reviewRepository;
    @PersistenceContext EntityManager em;

    private Course course;
    private Member reporter;

    @BeforeEach
    void setUp() {
        course =
                courseRepository.save(
                        Course.builder()
                                .name("신고 스키마 코스")
                                .location(GEO.createPoint(new Coordinate(127.0, 37.5)))
                                .build());
        reporter = memberRepository.save(Member.createBySocial("report-schema@kakao.com"));
        reporter.applyOnboarding(Level.SEED, null);
    }

    private Review notRecommendedReview() {
        return reviewRepository.saveAndFlush(
                Review.builder()
                        .place(course)
                        .member(reporter)
                        .recommended(false)
                        .practiceMethod(PracticeMethod.SOLO)
                        .memberLevel(Level.SEED)
                        .build());
    }

    private CourseReport direct() {
        return CourseReport.direct(course, reporter, List.of(CourseIssue.CONSTRUCTION), null);
    }

    @Test
    @DisplayName("문제 항목·기타 입력과 함께 저장되고 다시 읽힌다")
    void 저장_조회() {
        Long id =
                courseReportRepository
                        .saveAndFlush(
                                CourseReport.direct(
                                        course,
                                        reporter,
                                        List.of(CourseIssue.ROAD_INFO_MISMATCH, CourseIssue.OTHER),
                                        "주말엔 입구가 잠겨 있어요"))
                        .getId();
        em.clear();

        CourseReport saved = courseReportRepository.findById(id).orElseThrow();
        assertThat(saved.getIssues())
                .containsExactlyInAnyOrder(CourseIssue.ROAD_INFO_MISMATCH, CourseIssue.OTHER);
        assertThat(saved.getDetail()).isEqualTo("주말엔 입구가 잠겨 있어요");
        assertThat(
                        courseReportRepository.existsByCourseIdAndReporterIdAndReviewIdIsNull(
                                course.getId(), reporter.getId()))
                .isTrue();
    }

    @Test
    @DisplayName("같은 회원·같은 코스의 직접 신고는 두 번 저장되지 않는다")
    void 직접_신고_중복_제한() {
        courseReportRepository.saveAndFlush(direct());

        assertThatThrownBy(() -> courseReportRepository.saveAndFlush(direct()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("직접 신고한 코스에도 후기 신고는 후기마다 저장된다")
    void 후기_신고는_직접_신고와_공존() {
        courseReportRepository.saveAndFlush(direct());
        Review first = notRecommendedReview();
        Review second = notRecommendedReview();

        courseReportRepository.saveAndFlush(
                CourseReport.fromReview(
                        course, reporter, first.getId(), List.of(CourseIssue.CONSTRUCTION), null));
        courseReportRepository.saveAndFlush(
                CourseReport.fromReview(
                        course, reporter, second.getId(), List.of(CourseIssue.CONSTRUCTION), null));

        assertThat(courseReportRepository.count()).isEqualTo(3);
        assertThat(
                        courseReportRepository.existsByCourseIdAndReporterIdAndReviewIdIsNull(
                                course.getId(), reporter.getId()))
                .isTrue();
    }

    @Test
    @DisplayName("한 후기에 신고는 1건뿐이다")
    void 후기당_신고_1건() {
        Review review = notRecommendedReview();
        courseReportRepository.saveAndFlush(
                CourseReport.fromReview(
                        course, reporter, review.getId(), List.of(CourseIssue.CONSTRUCTION), null));

        assertThatThrownBy(
                        () ->
                                courseReportRepository.saveAndFlush(
                                        CourseReport.fromReview(
                                                course,
                                                reporter,
                                                review.getId(),
                                                List.of(CourseIssue.NOT_FOR_PRACTICE),
                                                null)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("CHECK: 문제 항목에 enum 밖의 값을 넣으면 DB가 거부한다")
    void 문제_항목_코드_검사() {
        Long id = courseReportRepository.saveAndFlush(direct()).getId();

        assertThatThrownBy(
                        () ->
                                em.createNativeQuery(
                                                "INSERT INTO course_report_issue (course_report_id, issue)"
                                                        + " VALUES (?1, 'UNKNOWN')")
                                        .setParameter(1, id)
                                        .executeUpdate())
                .rootCause()
                .hasMessageContaining("ck_course_report_issue_issue");
    }

    @Test
    @DisplayName("후기를 삭제하면 연결된 신고도 함께 지워진다")
    void 후기_삭제시_신고_삭제() {
        Review review = notRecommendedReview();
        courseReportRepository.saveAndFlush(
                CourseReport.fromReview(
                        course, reporter, review.getId(), List.of(CourseIssue.CONSTRUCTION), null));
        em.clear();

        reviewRepository.deleteById(review.getId());
        reviewRepository.flush();
        em.clear();

        assertThat(courseReportRepository.findByReviewId(review.getId())).isEmpty();
    }
}
