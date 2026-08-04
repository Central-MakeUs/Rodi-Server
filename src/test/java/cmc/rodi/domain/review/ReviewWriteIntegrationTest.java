package cmc.rodi.domain.review;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cmc.rodi.domain.member.entity.Level;
import cmc.rodi.domain.member.entity.Member;
import cmc.rodi.domain.member.repository.MemberRepository;
import cmc.rodi.domain.place.entity.Course;
import cmc.rodi.domain.place.repository.CourseRepository;
import cmc.rodi.domain.review.dto.ReviewRequest;
import cmc.rodi.domain.review.entity.Congestion;
import cmc.rodi.domain.review.entity.Difficulty;
import cmc.rodi.domain.review.entity.PracticeMethod;
import cmc.rodi.domain.review.entity.Review;
import cmc.rodi.domain.review.exception.ReviewErrorCode;
import cmc.rodi.domain.review.repository.ReviewRepository;
import cmc.rodi.domain.review.service.ReviewService;
import cmc.rodi.global.exception.BusinessException;
import cmc.rodi.global.exception.ErrorCode;
import cmc.rodi.support.TestcontainersConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

/** 후기 작성·수정·삭제 규칙 — 레벨 스냅샷, 여러 후기 허용, 레벨 변경 후 수정 불가, 소유자 검사. */
@SpringBootTest
@Transactional
@Import(TestcontainersConfiguration.class)
class ReviewWriteIntegrationTest {

    private static final GeometryFactory GEO = new GeometryFactory(new PrecisionModel(), 4326);

    @Autowired ReviewService reviewService;
    @Autowired ReviewRepository reviewRepository;
    @Autowired CourseRepository courseRepository;
    @Autowired MemberRepository memberRepository;

    private Course seedCourse() {
        return courseRepository.save(
                Course.builder()
                        .name("후기 코스")
                        .location(GEO.createPoint(new Coordinate(127.0, 37.5)))
                        .build());
    }

    private Member seedMember(String email, Level level) {
        Member member = memberRepository.save(Member.createBySocial(email));
        if (level != null) {
            member.applyOnboarding(level, null);
        }
        return member;
    }

    private static ReviewRequest request(String content) {
        return new ReviewRequest(
                true,
                Difficulty.EASY,
                Congestion.NORMAL,
                PracticeMethod.ACCOMPANIED,
                content,
                "주말 오후엔 자전거가 많습니다.");
    }

    @Test
    @DisplayName("작성 시 작성 당시 레벨이 스냅샷으로 저장되고, 같은 장소에 여러 후기를 쓸 수 있다")
    void 작성_레벨스냅샷과_복수작성() {
        Course course = seedCourse();
        Member me = seedMember("write@kakao.com", Level.ROOKIE);

        Long firstId = reviewService.create(course.getId(), me.getId(), request("첫 방문")).reviewId();
        Long secondId =
                reviewService.create(course.getId(), me.getId(), request("두 번째 방문")).reviewId();

        Review first = reviewRepository.findById(firstId).orElseThrow();
        assertThat(first.getMemberLevel()).isEqualTo(Level.ROOKIE);
        assertThat(first.getCaution()).isEqualTo("주말 오후엔 자전거가 많습니다.");
        assertThat(firstId).isNotEqualTo(secondId);
        assertThat(reviewRepository.count()).isEqualTo(2);
    }

    @Test
    @DisplayName("레벨 없는 회원(온보딩 미완료)의 작성은 409, 없는 장소는 404")
    void 작성_거부() {
        Course course = seedCourse();
        Member noLevel = seedMember("nolevel@kakao.com", null);
        Member me = seedMember("ok@kakao.com", Level.SEED);

        assertThatThrownBy(
                        () ->
                                reviewService.create(
                                        course.getId(), noLevel.getId(), request("레벨 없음")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ReviewErrorCode.LEVEL_REQUIRED);

        assertThatThrownBy(() -> reviewService.create(999_999L, me.getId(), request("없는 장소")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.ENTITY_NOT_FOUND);
    }

    @Test
    @DisplayName("작성 당시 레벨 = 현재 레벨이면 수정되고, 레벨업 이후 이전 후기 수정은 409(레벨 스냅샷은 불변)")
    void 수정_레벨규칙() {
        Course course = seedCourse();
        Member me = seedMember("edit@kakao.com", Level.ROOKIE);
        Long reviewId = reviewService.create(course.getId(), me.getId(), request("처음")).reviewId();

        reviewService.update(reviewId, me.getId(), request("수정된 내용"));
        assertThat(reviewRepository.findById(reviewId).orElseThrow().getContent())
                .isEqualTo("수정된 내용");

        me.applyOnboarding(Level.OWNER, null); // 레벨업

        assertThatThrownBy(() -> reviewService.update(reviewId, me.getId(), request("또 수정")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ReviewErrorCode.LEVEL_CHANGED);
        assertThat(reviewRepository.findById(reviewId).orElseThrow().getMemberLevel())
                .isEqualTo(Level.ROOKIE);
    }

    @Test
    @DisplayName("타인 후기 수정·삭제는 403")
    void 소유자_검사() {
        Course course = seedCourse();
        Member owner = seedMember("owner@kakao.com", Level.SEED);
        Member other = seedMember("other@kakao.com", Level.SEED);
        Long reviewId =
                reviewService.create(course.getId(), owner.getId(), request("내 글")).reviewId();

        assertThatThrownBy(() -> reviewService.update(reviewId, other.getId(), request("남의 글 수정")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ReviewErrorCode.NOT_REVIEW_OWNER);
        assertThatThrownBy(() -> reviewService.delete(reviewId, other.getId()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ReviewErrorCode.NOT_REVIEW_OWNER);
    }

    @Test
    @DisplayName("삭제는 레벨이 달라져도 성공한다")
    void 삭제_레벨무관() {
        Course course = seedCourse();
        Member me = seedMember("del@kakao.com", Level.ROOKIE);
        Long reviewId =
                reviewService.create(course.getId(), me.getId(), request("지울 글")).reviewId();

        me.applyOnboarding(Level.EXPLORER, null); // 레벨업해도 삭제는 가능

        assertThatCode(() -> reviewService.delete(reviewId, me.getId())).doesNotThrowAnyException();
        assertThat(reviewRepository.findById(reviewId)).isEmpty();
    }
}
