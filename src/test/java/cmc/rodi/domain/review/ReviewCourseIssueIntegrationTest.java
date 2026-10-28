package cmc.rodi.domain.review;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import cmc.rodi.domain.member.entity.Level;
import cmc.rodi.domain.member.entity.Member;
import cmc.rodi.domain.member.repository.MemberRepository;
import cmc.rodi.domain.place.entity.Course;
import cmc.rodi.domain.place.repository.CourseRepository;
import cmc.rodi.domain.review.dto.ReviewDetailResponse;
import cmc.rodi.domain.review.dto.ReviewFormResponse;
import cmc.rodi.domain.review.dto.ReviewFormResponse.Question;
import cmc.rodi.domain.review.dto.ReviewFormResponse.Step;
import cmc.rodi.domain.review.dto.ReviewRequest;
import cmc.rodi.domain.review.entity.Congestion;
import cmc.rodi.domain.review.entity.CourseIssue;
import cmc.rodi.domain.review.entity.Difficulty;
import cmc.rodi.domain.review.entity.PracticeMethod;
import cmc.rodi.domain.review.entity.Review;
import cmc.rodi.domain.review.repository.ReviewRepository;
import cmc.rodi.domain.review.service.ReviewQueryService;
import cmc.rodi.domain.review.service.ReviewService;
import cmc.rodi.global.common.form.FormOption;
import cmc.rodi.global.common.form.FormType;
import cmc.rodi.global.exception.BusinessException;
import cmc.rodi.global.exception.ErrorCode;
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
import org.springframework.transaction.annotation.Transactional;

/** '별로에요' 코스 문제 항목(스펙 024) — 추천 여부에 따른 저장 분기, 수정 시 반대쪽 값 삭제, 상세 응답, 후기 작성 폼. */
@SpringBootTest
@Transactional
@Import(TestcontainersConfiguration.class)
class ReviewCourseIssueIntegrationTest {

    private static final GeometryFactory GEO = new GeometryFactory(new PrecisionModel(), 4326);

    @Autowired ReviewService reviewService;
    @Autowired ReviewQueryService reviewQueryService;
    @Autowired ReviewRepository reviewRepository;
    @Autowired CourseRepository courseRepository;
    @Autowired MemberRepository memberRepository;
    @PersistenceContext EntityManager em;

    private Course seedCourse() {
        return courseRepository.save(
                Course.builder()
                        .name("문제 코스")
                        .location(GEO.createPoint(new Coordinate(127.0, 37.5)))
                        .build());
    }

    private Member seedMember(String email) {
        Member member = memberRepository.save(Member.createBySocial(email));
        member.applyOnboarding(Level.ROOKIE, null);
        return member;
    }

    private static ReviewRequest recommended() {
        return new ReviewRequest(
                true,
                Difficulty.EASY,
                Congestion.NORMAL,
                PracticeMethod.SOLO,
                "좋았어요",
                "주말엔 붐벼요",
                null,
                null);
    }

    /** 별로에요인데 난이도·혼잡도·주의사항까지 함께 온 요청 — 앱이 선택을 바꾸며 이전 값을 남겨 보낸 경우. */
    private static ReviewRequest notRecommended(List<CourseIssue> issues, String detail) {
        return new ReviewRequest(
                false,
                Difficulty.HARD,
                Congestion.CROWDED,
                PracticeMethod.ACCOMPANIED,
                "막혀 있었어요",
                "버려질 주의사항",
                issues,
                detail);
    }

    /** DB에서 다시 읽는다 — 컬렉션까지 실제로 저장됐는지 보려면 영속성 컨텍스트를 비워야 한다. */
    private Review reload(Long reviewId) {
        em.flush();
        em.clear();
        return reviewRepository.findById(reviewId).orElseThrow();
    }

    @Test
    @DisplayName("별로에요는 문제 항목·기타 입력만 저장하고 난이도·혼잡도·주의사항은 버린다")
    void 별로에요_작성() {
        Course course = seedCourse();
        Member me = seedMember("bad@kakao.com");

        Long id =
                reviewService
                        .create(
                                course.getId(),
                                me.getId(),
                                notRecommended(
                                        List.of(CourseIssue.ROAD_INFO_MISMATCH, CourseIssue.OTHER),
                                        "입구 차단봉이 내려가 있어요"))
                        .reviewId();

        Review saved = reload(id);
        assertThat(saved.isRecommended()).isFalse();
        assertThat(saved.getCourseIssues())
                .containsExactlyInAnyOrder(CourseIssue.ROAD_INFO_MISMATCH, CourseIssue.OTHER);
        assertThat(saved.getCourseIssueDetail()).isEqualTo("입구 차단봉이 내려가 있어요");
        assertThat(saved.getDifficulty()).isNull();
        assertThat(saved.getCongestion()).isNull();
        assertThat(saved.getCaution()).isNull();
        // 2/2 화면 값은 분기와 무관하게 남는다
        assertThat(saved.getPracticeMethod()).isEqualTo(PracticeMethod.ACCOMPANIED);
        assertThat(saved.getContent()).isEqualTo("막혀 있었어요");
    }

    @Test
    @DisplayName("기타를 고르지 않으면 직접 입력은 저장하지 않는다")
    void 기타_없으면_직접입력_버림() {
        Course course = seedCourse();
        Member me = seedMember("noother@kakao.com");

        Long id =
                reviewService
                        .create(
                                course.getId(),
                                me.getId(),
                                notRecommended(List.of(CourseIssue.CONSTRUCTION), "기타가 아님"))
                        .reviewId();

        assertThat(reload(id).getCourseIssueDetail()).isNull();
    }

    @Test
    @DisplayName("추천해요로 보낸 문제 항목은 저장하지 않는다")
    void 추천해요_문제_항목_버림() {
        Course course = seedCourse();
        Member me = seedMember("good@kakao.com");
        ReviewRequest request =
                new ReviewRequest(
                        true,
                        Difficulty.EASY,
                        Congestion.NORMAL,
                        PracticeMethod.SOLO,
                        null,
                        null,
                        List.of(CourseIssue.OTHER),
                        "버려질 입력");

        Long id = reviewService.create(course.getId(), me.getId(), request).reviewId();

        Review saved = reload(id);
        assertThat(saved.getCourseIssues()).isEmpty();
        assertThat(saved.getCourseIssueDetail()).isNull();
        assertThat(saved.getDifficulty()).isEqualTo(Difficulty.EASY);
    }

    @Test
    @DisplayName("별로에요 → 추천해요로 수정하면 문제 항목·기타 입력이 지워지고 난이도·혼잡도가 저장된다")
    void 수정_별로에요에서_추천해요로() {
        Course course = seedCourse();
        Member me = seedMember("flip1@kakao.com");
        Long id =
                reviewService
                        .create(
                                course.getId(),
                                me.getId(),
                                notRecommended(List.of(CourseIssue.OTHER), "직접 입력"))
                        .reviewId();
        reload(id);

        reviewService.update(id, me.getId(), recommended());

        Review updated = reload(id);
        assertThat(updated.isRecommended()).isTrue();
        assertThat(updated.getCourseIssues()).isEmpty();
        assertThat(updated.getCourseIssueDetail()).isNull();
        assertThat(updated.getDifficulty()).isEqualTo(Difficulty.EASY);
        assertThat(updated.getCongestion()).isEqualTo(Congestion.NORMAL);
        assertThat(updated.getCaution()).isEqualTo("주말엔 붐벼요");
    }

    @Test
    @DisplayName("추천해요 → 별로에요로 수정하면 난이도·혼잡도·주의사항이 지워지고 문제 항목이 저장된다")
    void 수정_추천해요에서_별로에요로() {
        Course course = seedCourse();
        Member me = seedMember("flip2@kakao.com");
        Long id = reviewService.create(course.getId(), me.getId(), recommended()).reviewId();

        reviewService.update(
                id, me.getId(), notRecommended(List.of(CourseIssue.VEHICLE_RESTRICTED), null));

        Review updated = reload(id);
        assertThat(updated.isRecommended()).isFalse();
        assertThat(updated.getCourseIssues()).containsExactly(CourseIssue.VEHICLE_RESTRICTED);
        assertThat(updated.getDifficulty()).isNull();
        assertThat(updated.getCongestion()).isNull();
        assertThat(updated.getCaution()).isNull();
    }

    @Test
    @DisplayName("별로에요끼리 수정하면 문제 항목이 통째로 교체된다")
    void 수정_문제_항목_교체() {
        Course course = seedCourse();
        Member me = seedMember("swap@kakao.com");
        Long id =
                reviewService
                        .create(
                                course.getId(),
                                me.getId(),
                                notRecommended(
                                        List.of(CourseIssue.CONSTRUCTION, CourseIssue.OTHER), "막힘"))
                        .reviewId();
        reload(id);

        reviewService.update(
                id, me.getId(), notRecommended(List.of(CourseIssue.NOT_FOR_PRACTICE), null));

        Review updated = reload(id);
        assertThat(updated.getCourseIssues()).containsExactly(CourseIssue.NOT_FOR_PRACTICE);
        assertThat(updated.getCourseIssueDetail()).isNull();
    }

    @Test
    @DisplayName("상세 조회 — 별로에요는 문제 항목을 폼 order 순으로, 추천해요는 빈 배열로 준다")
    void 상세_조회() {
        Course course = seedCourse();
        Member me = seedMember("detail@kakao.com");
        Long badId =
                reviewService
                        .create(
                                course.getId(),
                                me.getId(),
                                // 폼 순서와 다르게 보낸다
                                notRecommended(
                                        List.of(
                                                CourseIssue.OTHER,
                                                CourseIssue.VEHICLE_RESTRICTED,
                                                CourseIssue.CONSTRUCTION),
                                        "직접 입력"))
                        .reviewId();
        Long goodId = reviewService.create(course.getId(), me.getId(), recommended()).reviewId();
        em.flush();
        em.clear();

        ReviewDetailResponse bad = reviewQueryService.getReview(badId, me.getId());
        assertThat(bad.courseIssues())
                .containsExactly(
                        CourseIssue.VEHICLE_RESTRICTED,
                        CourseIssue.CONSTRUCTION,
                        CourseIssue.OTHER);
        assertThat(bad.courseIssueDetail()).isEqualTo("직접 입력");
        assertThat(bad.difficulty()).isNull();
        assertThat(bad.congestion()).isNull();

        ReviewDetailResponse good = reviewQueryService.getReview(goodId, me.getId());
        assertThat(good.courseIssues()).isEmpty();
        assertThat(good.courseIssueDetail()).isNull();
    }

    @Test
    @DisplayName("후기 작성 폼 — 2단계·문항 7개, 추천 여부에 따른 showWhen, 시안 문구, 제목에 코스명")
    void 후기_작성_폼() {
        Course course = seedCourse();

        ReviewFormResponse form = reviewService.getReviewForm(course.getId());

        assertThat(form.title()).isEqualTo("후기 남기기");
        assertThat(form.placeName()).isEqualTo("문제 코스");
        assertThat(form.steps()).extracting(Step::order).containsExactly(1, 2);
        assertThat(form.steps().get(0).title()).isEqualTo("문제 코스의 연습은 어떠셨나요?");
        assertThat(form.steps().get(1).title()).isNull();
        assertThat(form.steps().stream().flatMap(step -> step.questions().stream()))
                .extracting(Question::code, Question::order)
                .containsExactly(
                        tuple("isRecommended", 1),
                        tuple("difficulty", 2),
                        tuple("congestion", 3),
                        tuple("caution", 4),
                        tuple("courseIssues", 5),
                        tuple("practiceMethod", 6),
                        tuple("content", 7));

        List<Question> step1 = form.steps().get(0).questions();
        assertThat(step1.get(0).showWhen()).isNull();
        assertThat(step1.get(0).options())
                .extracting(FormOption::code, FormOption::label)
                .containsExactly(tuple("false", "별로에요"), tuple("true", "추천해요"));
        // 추천해요 쪽 문항
        assertThat(step1.subList(1, 4))
                .allSatisfy(
                        q -> {
                            assertThat(q.showWhen().question()).isEqualTo("isRecommended");
                            assertThat(q.showWhen().anyOf()).containsExactly("true");
                        });
        assertThat(step1.get(1).options())
                .extracting(FormOption::label)
                .containsExactly("매우 쉬움", "쉬움", "보통", "어려움", "매우 어려움");
        assertThat(step1.get(2).options())
                .extracting(FormOption::label)
                .containsExactly("한산해요", "보통이에요", "복잡해요");
        assertThat(step1.get(3).type()).isEqualTo(FormType.TEXT);
        assertThat(step1.get(3).required()).isFalse();
        assertThat(step1.get(3).maxLength()).isNull();
        // 별로에요 쪽 문항
        Question issues = step1.get(4);
        assertThat(issues.type()).isEqualTo(FormType.MULTI_SELECT);
        assertThat(issues.required()).isTrue();
        assertThat(issues.showWhen().anyOf()).containsExactly("false");
        assertThat(issues.options())
                .extracting(FormOption::code)
                .containsExactly(
                        "VEHICLE_RESTRICTED",
                        "ROAD_INFO_MISMATCH",
                        "CONSTRUCTION",
                        "NOT_FOR_PRACTICE",
                        "OTHER");
        assertThat(issues.options())
                .filteredOn(FormOption::requiresTextInput)
                .singleElement()
                .satisfies(
                        other -> {
                            assertThat(other.code()).isEqualTo("OTHER");
                            assertThat(other.textInputMaxLength()).isEqualTo(30);
                            assertThat(other.textInputPlaceholder()).isEqualTo("이유를 작성해주세요.");
                        });

        List<Question> step2 = form.steps().get(1).questions();
        assertThat(step2).allSatisfy(q -> assertThat(q.showWhen()).isNull());
        assertThat(step2.get(0).options())
                .extracting(FormOption::code, FormOption::label)
                .containsExactly(tuple("SOLO", "혼자 연습"), tuple("ACCOMPANIED", "동승자와 연습"));
        assertThat(step2.get(1).maxLength()).isEqualTo(150);
        assertThat(step2.get(1).required()).isFalse();
    }

    @Test
    @DisplayName("후기 작성 폼 — 없는 장소면 ENTITY_NOT_FOUND")
    void 후기_작성_폼_없는_장소() {
        assertThatThrownBy(() -> reviewService.getReviewForm(Long.MAX_VALUE))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.ENTITY_NOT_FOUND);
    }
}
