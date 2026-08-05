package cmc.rodi.domain.review;

import static org.assertj.core.api.Assertions.assertThat;

import cmc.rodi.domain.member.entity.Level;
import cmc.rodi.domain.member.entity.Member;
import cmc.rodi.domain.member.repository.MemberRepository;
import cmc.rodi.domain.member.service.MemberBlockService;
import cmc.rodi.domain.place.entity.Course;
import cmc.rodi.domain.place.repository.CourseRepository;
import cmc.rodi.domain.review.dto.ReviewListRequest;
import cmc.rodi.domain.review.dto.ReviewRequest;
import cmc.rodi.domain.review.dto.ReviewSummaryResponse;
import cmc.rodi.domain.review.entity.Congestion;
import cmc.rodi.domain.review.entity.Difficulty;
import cmc.rodi.domain.review.entity.PracticeMethod;
import cmc.rodi.domain.review.service.ReviewQueryService;
import cmc.rodi.domain.review.service.ReviewService;
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

/** 후기 요약 — 선택 레벨 기준 난이도·혼잡도 분포(0 포함), 추천 수, 레벨별 분포, 차단 무관. */
@SpringBootTest
@Transactional
@Import(TestcontainersConfiguration.class)
class ReviewSummaryIntegrationTest {

    private static final GeometryFactory GEO = new GeometryFactory(new PrecisionModel(), 4326);

    @Autowired ReviewService reviewService;
    @Autowired ReviewQueryService reviewQueryService;
    @Autowired MemberBlockService memberBlockService;
    @Autowired CourseRepository courseRepository;
    @Autowired MemberRepository memberRepository;

    private Course seedCourse() {
        return courseRepository.save(
                Course.builder()
                        .name("요약 코스")
                        .location(GEO.createPoint(new Coordinate(126.9, 37.4)))
                        .build());
    }

    private Member seedMember(String email, Level level) {
        Member member = memberRepository.save(Member.createBySocial(email));
        member.applyOnboarding(level, null);
        return member;
    }

    private void writeReview(
            Course course,
            Member member,
            boolean recommended,
            Difficulty difficulty,
            Congestion congestion) {
        reviewService.create(
                course.getId(),
                member.getId(),
                new ReviewRequest(
                        recommended, difficulty, congestion, PracticeMethod.SOLO, "후기 내용", null));
    }

    private ReviewSummaryResponse summary(Long placeId, Long memberId, String level) {
        return reviewQueryService.getSummary(placeId, memberId, ReviewListRequest.ofLevel(level));
    }

    @Test
    @DisplayName("선택 레벨 기준 난이도·혼잡도 분포와 추천 수를 반환하고, 0건 값도 키가 0으로 존재한다")
    void 레벨별_분포() {
        Course course = seedCourse();
        Member rookie1 = seedMember("s1@kakao.com", Level.ROOKIE);
        Member rookie2 = seedMember("s2@kakao.com", Level.ROOKIE);
        Member owner = seedMember("s3@kakao.com", Level.OWNER);

        writeReview(course, rookie1, true, Difficulty.VERY_EASY, Congestion.QUIET);
        writeReview(course, rookie1, true, Difficulty.VERY_EASY, Congestion.NORMAL); // 같은 사람 2건
        writeReview(course, rookie2, false, Difficulty.HARD, Congestion.CROWDED);
        writeReview(course, owner, true, Difficulty.NORMAL, Congestion.NORMAL);

        ReviewSummaryResponse rookie = summary(course.getId(), rookie1.getId(), null);
        assertThat(rookie.level()).isEqualTo("ROOKIE");
        assertThat(rookie.totalCount()).isEqualTo(3);
        assertThat(rookie.recommendCount()).isEqualTo(2);
        assertThat(rookie.notRecommendCount()).isEqualTo(1);
        assertThat(rookie.difficultyCounts())
                .hasSize(5)
                .containsEntry(Difficulty.VERY_EASY, 2L)
                .containsEntry(Difficulty.HARD, 1L)
                .containsEntry(Difficulty.EASY, 0L)
                .containsEntry(Difficulty.NORMAL, 0L)
                .containsEntry(Difficulty.VERY_HARD, 0L);
        assertThat(rookie.congestionCounts())
                .hasSize(3)
                .containsEntry(Congestion.QUIET, 1L)
                .containsEntry(Congestion.NORMAL, 1L)
                .containsEntry(Congestion.CROWDED, 1L);

        // 합계 정합성
        assertThat(rookie.difficultyCounts().values().stream().mapToLong(Long::longValue).sum())
                .isEqualTo(rookie.totalCount());
        assertThat(rookie.congestionCounts().values().stream().mapToLong(Long::longValue).sum())
                .isEqualTo(rookie.totalCount());
        assertThat(rookie.recommendCount() + rookie.notRecommendCount())
                .isEqualTo(rookie.totalCount());
    }

    @Test
    @DisplayName("level=ALL이면 전체를 집계하고, levelCounts는 레벨 필터와 무관하게 5개 레벨 전체 분포다")
    void 전체_집계와_레벨분포() {
        Course course = seedCourse();
        Member rookie = seedMember("a1@kakao.com", Level.ROOKIE);
        Member owner = seedMember("a2@kakao.com", Level.OWNER);
        writeReview(course, rookie, true, Difficulty.EASY, Congestion.QUIET);
        writeReview(course, owner, true, Difficulty.EASY, Congestion.QUIET);

        ReviewSummaryResponse all = summary(course.getId(), rookie.getId(), "ALL");
        assertThat(all.level()).isEqualTo("ALL");
        assertThat(all.totalCount()).isEqualTo(2);

        ReviewSummaryResponse byLevel = summary(course.getId(), rookie.getId(), null);
        assertThat(byLevel.totalCount()).isEqualTo(1);
        // levelCounts는 두 응답 모두 동일(필터 무관), 후기 없는 레벨은 0
        assertThat(byLevel.levelCounts())
                .hasSize(Level.values().length)
                .containsEntry(Level.ROOKIE, 1L)
                .containsEntry(Level.OWNER, 1L)
                .containsEntry(Level.SEED, 0L);
        assertThat(all.levelCounts()).isEqualTo(byLevel.levelCounts());
    }

    @Test
    @DisplayName("차단해도 요약 수치는 그대로다(목록에서만 제외)")
    void 차단은_요약에_영향없음() {
        Course course = seedCourse();
        Member me = seedMember("b1@kakao.com", Level.SEED);
        Member noisy = seedMember("b2@kakao.com", Level.SEED);
        writeReview(course, me, true, Difficulty.EASY, Congestion.QUIET);
        writeReview(course, noisy, false, Difficulty.HARD, Congestion.CROWDED);

        long before = summary(course.getId(), me.getId(), null).totalCount();
        memberBlockService.block(me.getId(), noisy.getId());

        assertThat(summary(course.getId(), me.getId(), null).totalCount()).isEqualTo(before);
        assertThat(summary(course.getId(), me.getId(), null).notRecommendCount()).isEqualTo(1);
    }
}
