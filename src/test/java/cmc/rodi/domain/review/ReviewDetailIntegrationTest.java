package cmc.rodi.domain.review;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cmc.rodi.domain.member.entity.Level;
import cmc.rodi.domain.member.entity.Member;
import cmc.rodi.domain.member.repository.MemberRepository;
import cmc.rodi.domain.place.entity.Course;
import cmc.rodi.domain.place.repository.CourseRepository;
import cmc.rodi.domain.practice.dto.PracticeVisitRequest;
import cmc.rodi.domain.practice.service.PracticeService;
import cmc.rodi.domain.review.dto.ReviewDetailResponse;
import cmc.rodi.domain.review.dto.ReviewRequest;
import cmc.rodi.domain.review.entity.Congestion;
import cmc.rodi.domain.review.entity.Difficulty;
import cmc.rodi.domain.review.entity.PracticeMethod;
import cmc.rodi.domain.review.exception.ReviewErrorCode;
import cmc.rodi.domain.review.repository.ReviewRepository;
import cmc.rodi.domain.review.service.ReviewQueryService;
import cmc.rodi.domain.review.service.ReviewService;
import cmc.rodi.global.exception.BusinessException;
import cmc.rodi.global.exception.ErrorCode;
import cmc.rodi.support.TestcontainersConfiguration;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

/** 후기 상세 조회 — 수정 폼에 필요한 값 전체, 본인만, 비공개 포함, isEditable 레벨 판정. */
@SpringBootTest
@Transactional
@Import(TestcontainersConfiguration.class)
class ReviewDetailIntegrationTest {

    private static final GeometryFactory GEO = new GeometryFactory(new PrecisionModel(), 4326);

    @Autowired ReviewService reviewService;
    @Autowired ReviewQueryService reviewQueryService;
    @Autowired ReviewRepository reviewRepository;
    @Autowired CourseRepository courseRepository;
    @Autowired PracticeService practiceService;
    @Autowired MemberRepository memberRepository;

    private Course seedCourse(String name) {
        return courseRepository.save(
                Course.builder()
                        .name(name)
                        .location(GEO.createPoint(new Coordinate(127.2, 37.7)))
                        .distanceMeters(2_000) // 필요 거리 800m — GPS 인증을 태울 수 있게
                        .build());
    }

    /** 그 레벨에서 방문 인증까지 받아둔다 — 후기의 isVerifiedVisit이 true로 저장되는 조건. */
    private void certifyVisit(Course course, Member member) {
        Long practiceId = practiceService.register(course.getId(), member.getId()).practiceId();
        practiceService.recordVisit(practiceId, member.getId(), new PracticeVisitRequest(800));
    }

    private Member seedMember(String email, Level level) {
        Member member = memberRepository.save(Member.createBySocial(email));
        member.applyOnboarding(level, null);
        return member;
    }

    private Long write(Course course, Member member) {
        return reviewService
                .create(
                        course.getId(),
                        member.getId(),
                        new ReviewRequest(
                                false,
                                Difficulty.HARD,
                                Congestion.CROWDED,
                                PracticeMethod.ACCOMPANIED,
                                "퇴근시간엔 정체가 심했어요.",
                                "주말 오후엔 자전거 통행이 많습니다."))
                .reviewId();
    }

    @Test
    @DisplayName("수정 요청이 요구하는 값을 전부 돌려준다")
    void 수정_폼_프리필() {
        Course course = seedCourse("상세 코스");
        Member me = seedMember("detail@kakao.com", Level.ROOKIE);
        certifyVisit(course, me);
        Long reviewId = write(course, me);

        ReviewDetailResponse detail = reviewQueryService.getReview(reviewId, me.getId());

        // 목록 응답에는 없어서 수정 화면을 못 채우던 값들 — 여기서 빠지면 이 API의 존재 이유가 사라진다
        assertThat(detail.recommended()).isFalse();
        assertThat(detail.difficulty()).isEqualTo(Difficulty.HARD);
        assertThat(detail.congestion()).isEqualTo(Congestion.CROWDED);
        assertThat(detail.practiceMethod()).isEqualTo(PracticeMethod.ACCOMPANIED);
        assertThat(detail.caution()).isEqualTo("주말 오후엔 자전거 통행이 많습니다.");

        assertThat(detail.reviewId()).isEqualTo(reviewId);
        assertThat(detail.placeId()).isEqualTo(course.getId());
        assertThat(detail.placeName()).isEqualTo("상세 코스");
        assertThat(detail.content()).isEqualTo("퇴근시간엔 정체가 심했어요.");
        assertThat(detail.editable()).isTrue();
        assertThat(detail.hidden()).isFalse();
        // 저장된 스냅샷이 응답까지 그대로 오는지 — 목 없이 실제 인증 이력으로 확인한다
        assertThat(detail.verifiedVisit()).isTrue();
        assertThat(detail.createdAt()).isNotNull();
    }

    @Test
    @DisplayName("레벨이 바뀌면 isEditable만 false가 되고 조회는 그대로 된다")
    void 레벨_변경_후_조회() {
        Course course = seedCourse("레벨 코스");
        Member me = seedMember("levelup@kakao.com", Level.SEED);
        Long reviewId = write(course, me);

        me.addDistance(60_000); // SEED → ROOKIE

        ReviewDetailResponse detail = reviewQueryService.getReview(reviewId, me.getId());
        assertThat(detail.editable()).isFalse();
        assertThat(detail.content()).isEqualTo("퇴근시간엔 정체가 심했어요.");
    }

    @Test
    @DisplayName("비공개 처리된 내 후기도 조회되고 isHidden으로 표시된다")
    void 비공개_후기_조회() {
        Course course = seedCourse("비공개 코스");
        Member me = seedMember("hidden@kakao.com", Level.ROOKIE);
        Long reviewId = write(course, me);
        reviewRepository.findById(reviewId).orElseThrow().hide(LocalDateTime.now());

        ReviewDetailResponse detail = reviewQueryService.getReview(reviewId, me.getId());

        assertThat(detail.hidden()).isTrue();
        assertThat(detail.content()).isEqualTo("퇴근시간엔 정체가 심했어요.");
    }

    @Test
    @DisplayName("남의 후기는 403 — caution까지 주는 응답이라 열 수 없다")
    void 타인_후기_거부() {
        Course course = seedCourse("타인 코스");
        Member author = seedMember("author@kakao.com", Level.ROOKIE);
        Member other = seedMember("other@kakao.com", Level.ROOKIE);
        Long reviewId = write(course, author);

        assertThatThrownBy(() -> reviewQueryService.getReview(reviewId, other.getId()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ReviewErrorCode.NOT_REVIEW_OWNER);
    }

    @Test
    @DisplayName("없는 후기는 404")
    void 없는_후기() {
        Member me = seedMember("missing@kakao.com", Level.ROOKIE);

        assertThatThrownBy(() -> reviewQueryService.getReview(999_999L, me.getId()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.ENTITY_NOT_FOUND);
    }
}
