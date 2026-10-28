package cmc.rodi.domain.review;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cmc.rodi.domain.member.entity.Level;
import cmc.rodi.domain.member.entity.Member;
import cmc.rodi.domain.member.repository.MemberRepository;
import cmc.rodi.domain.place.entity.Course;
import cmc.rodi.domain.place.repository.CourseRepository;
import cmc.rodi.domain.review.entity.Congestion;
import cmc.rodi.domain.review.entity.CourseIssue;
import cmc.rodi.domain.review.entity.Difficulty;
import cmc.rodi.domain.review.entity.PracticeMethod;
import cmc.rodi.domain.review.entity.Review;
import cmc.rodi.domain.review.repository.ReviewRepository;
import cmc.rodi.support.TestcontainersConfiguration;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;
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

/** 코스 문제 항목 스키마(V30): 별로에요 후기 저장, 추천 후기 난이도·혼잡도 필수 CHECK, 문제 항목 코드 CHECK, 후기 삭제 시 함께 삭제. */
@SpringBootTest
@Transactional
@Import(TestcontainersConfiguration.class)
class ReviewCourseIssueSchemaIntegrationTest {

    private static final GeometryFactory GEO = new GeometryFactory(new PrecisionModel(), 4326);

    @Autowired ReviewRepository reviewRepository;
    @Autowired CourseRepository courseRepository;
    @Autowired MemberRepository memberRepository;
    @PersistenceContext EntityManager em;

    private Review.ReviewBuilder baseReview() {
        Course course =
                courseRepository.save(
                        Course.builder()
                                .name("스키마 코스")
                                .location(GEO.createPoint(new Coordinate(127.0, 37.5)))
                                .build());
        Member member = memberRepository.save(Member.createBySocial("schema@kakao.com"));
        member.applyOnboarding(Level.SEED, null);
        return Review.builder()
                .place(course)
                .member(member)
                .practiceMethod(PracticeMethod.SOLO)
                .memberLevel(Level.SEED);
    }

    private long issueRowCount(Long reviewId) {
        return ((Number)
                        em.createNativeQuery(
                                        "SELECT count(*) FROM review_course_issue WHERE review_id = ?1")
                                .setParameter(1, reviewId)
                                .getSingleResult())
                .longValue();
    }

    @Test
    @DisplayName("별로에요 후기는 난이도·혼잡도 없이 문제 항목·기타 입력과 함께 저장된다")
    void 별로에요_후기_저장() {
        Long id =
                reviewRepository
                        .saveAndFlush(
                                baseReview()
                                        .recommended(false)
                                        .courseIssues(
                                                List.of(
                                                        CourseIssue.ROAD_INFO_MISMATCH,
                                                        CourseIssue.OTHER))
                                        .courseIssueDetail("입구 차단봉이 내려가 있어요")
                                        .build())
                        .getId();
        em.clear();

        Review saved = reviewRepository.findById(id).orElseThrow();
        assertThat(saved.getDifficulty()).isNull();
        assertThat(saved.getCongestion()).isNull();
        assertThat(saved.getCourseIssues())
                .containsExactlyInAnyOrder(CourseIssue.ROAD_INFO_MISMATCH, CourseIssue.OTHER);
        assertThat(saved.getCourseIssueDetail()).isEqualTo("입구 차단봉이 내려가 있어요");
    }

    @Test
    @DisplayName("CHECK: 추천 후기에 난이도가 없으면 DB가 거부한다")
    void 추천_후기_난이도_필수() {
        Review review = baseReview().recommended(true).congestion(Congestion.NORMAL).build();

        assertThatThrownBy(() -> reviewRepository.saveAndFlush(review))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("CHECK: 추천 후기에 혼잡도가 없으면 DB가 거부한다")
    void 추천_후기_혼잡도_필수() {
        Review review = baseReview().recommended(true).difficulty(Difficulty.EASY).build();

        assertThatThrownBy(() -> reviewRepository.saveAndFlush(review))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("CHECK: 문제 항목에 enum 밖의 값을 넣으면 DB가 거부한다")
    void 문제_항목_코드_검사() {
        Long id = reviewRepository.saveAndFlush(baseReview().recommended(false).build()).getId();

        assertThatThrownBy(
                        () ->
                                em.createNativeQuery(
                                                "INSERT INTO review_course_issue (review_id, issue)"
                                                        + " VALUES (?1, 'UNKNOWN')")
                                        .setParameter(1, id)
                                        .executeUpdate())
                .rootCause()
                .hasMessageContaining("ck_review_course_issue_issue");
    }

    @Test
    @DisplayName("후기를 삭제하면 문제 항목 행도 함께 지워진다")
    void 후기_삭제시_문제_항목_삭제() {
        Review review =
                reviewRepository.saveAndFlush(
                        baseReview()
                                .recommended(false)
                                .courseIssues(List.of(CourseIssue.CONSTRUCTION))
                                .build());
        Long id = review.getId();
        assertThat(issueRowCount(id)).isEqualTo(1);

        reviewRepository.delete(review);
        reviewRepository.flush();

        assertThat(issueRowCount(id)).isZero();
    }
}
