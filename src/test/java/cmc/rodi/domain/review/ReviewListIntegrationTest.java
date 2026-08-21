package cmc.rodi.domain.review;

import static org.assertj.core.api.Assertions.assertThat;

import cmc.rodi.domain.member.entity.Level;
import cmc.rodi.domain.member.entity.Member;
import cmc.rodi.domain.member.repository.MemberRepository;
import cmc.rodi.domain.member.service.MemberBlockService;
import cmc.rodi.domain.place.entity.Course;
import cmc.rodi.domain.place.repository.CourseRepository;
import cmc.rodi.domain.review.dto.ReviewItem;
import cmc.rodi.domain.review.dto.ReviewListRequest;
import cmc.rodi.domain.review.dto.ReviewRequest;
import cmc.rodi.domain.review.entity.Congestion;
import cmc.rodi.domain.review.entity.Difficulty;
import cmc.rodi.domain.review.entity.PracticeMethod;
import cmc.rodi.domain.review.service.ReviewQueryService;
import cmc.rodi.domain.review.service.ReviewService;
import cmc.rodi.global.common.pagination.CursorPage;
import cmc.rodi.support.TestcontainersConfiguration;
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

/** 후기 목록 — 레벨 필터 기본값(내 레벨)·ALL·커서 2페이지·totalCount·isMine/isEditable·차단 제외. */
@SpringBootTest
@Transactional
@Import(TestcontainersConfiguration.class)
class ReviewListIntegrationTest {

    private static final GeometryFactory GEO = new GeometryFactory(new PrecisionModel(), 4326);

    @Autowired ReviewService reviewService;
    @Autowired ReviewQueryService reviewQueryService;
    @Autowired MemberBlockService memberBlockService;
    @Autowired CourseRepository courseRepository;
    @Autowired MemberRepository memberRepository;

    private Course seedCourse() {
        return courseRepository.save(
                Course.builder()
                        .name("목록 코스")
                        .location(GEO.createPoint(new Coordinate(127.1, 37.6)))
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
                true, Difficulty.EASY, Congestion.NORMAL, PracticeMethod.SOLO, content, null);
    }

    private CursorPage<ReviewItem> list(
            Long placeId, Long memberId, String level, int size, String cursor) {
        return reviewQueryService.getReviews(
                placeId, memberId, new ReviewListRequest(level, size, cursor));
    }

    @Test
    @DisplayName("level 생략 시 조회자 본인 레벨로 필터하고, ALL이면 전체가 나온다")
    void 레벨_필터_기본값() {
        Course course = seedCourse();
        Member rookie = seedMember("rookie@kakao.com", Level.ROOKIE);
        Member owner = seedMember("owner@kakao.com", Level.OWNER);
        reviewService.create(course.getId(), rookie.getId(), request("루키 후기"));
        reviewService.create(course.getId(), owner.getId(), request("오너 후기"));

        CursorPage<ReviewItem> mine = list(course.getId(), rookie.getId(), null, 10, null);
        assertThat(mine.items()).hasSize(1);
        assertThat(mine.items().get(0).content()).isEqualTo("루키 후기");
        assertThat(mine.totalCount()).isEqualTo(1);

        CursorPage<ReviewItem> all = list(course.getId(), rookie.getId(), "ALL", 10, null);
        assertThat(all.items()).hasSize(2);
        assertThat(all.totalCount()).isEqualTo(2);

        CursorPage<ReviewItem> explicit = list(course.getId(), rookie.getId(), "OWNER", 10, null);
        assertThat(explicit.items()).hasSize(1);
        assertThat(explicit.items().get(0).content()).isEqualTo("오너 후기");
    }

    @Test
    @DisplayName("레벨 없는 회원이 level 없이 조회하면 전체가 나온다")
    void 레벨없는_회원은_전체() {
        Course course = seedCourse();
        Member rookie = seedMember("r2@kakao.com", Level.ROOKIE);
        Member noLevel = seedMember("nolevel2@kakao.com", null);
        reviewService.create(course.getId(), rookie.getId(), request("후기"));

        assertThat(list(course.getId(), noLevel.getId(), null, 10, null).items()).hasSize(1);
    }

    @Test
    @DisplayName("최신순 커서로 2페이지가 중복 없이 이어지고, totalCount는 첫 페이지에만 채워진다")
    void 커서_페이지네이션() {
        Course course = seedCourse();
        Member me = seedMember("cursor@kakao.com", Level.SEED);
        for (int i = 1; i <= 5; i++) {
            reviewService.create(course.getId(), me.getId(), request("후기 " + i));
        }

        CursorPage<ReviewItem> firstPage = list(course.getId(), me.getId(), null, 3, null);
        assertThat(firstPage.items()).hasSize(3);
        assertThat(firstPage.hasNext()).isTrue();
        assertThat(firstPage.totalCount()).isEqualTo(5);
        assertThat(firstPage.items().get(0).content()).isEqualTo("후기 5"); // 최신순

        CursorPage<ReviewItem> secondPage =
                list(course.getId(), me.getId(), null, 3, firstPage.nextCursor());
        assertThat(secondPage.items()).hasSize(2);
        assertThat(secondPage.hasNext()).isFalse();
        assertThat(secondPage.nextCursor()).isNull();
        assertThat(secondPage.totalCount()).isNull(); // 이후 페이지는 채우지 않는다

        List<Long> ids =
                java.util.stream.Stream.concat(
                                firstPage.items().stream(), secondPage.items().stream())
                        .map(ReviewItem::reviewId)
                        .toList();
        assertThat(ids).doesNotHaveDuplicates().hasSize(5);
    }

    @Test
    @DisplayName("isMine·isEditable이 조회 회원 기준으로 채워진다")
    void 항목_플래그() {
        Course course = seedCourse();
        Member me = seedMember("flag@kakao.com", Level.ROOKIE);
        Member other = seedMember("flag2@kakao.com", Level.ROOKIE);
        Long mineId = reviewService.create(course.getId(), me.getId(), request("내 후기")).reviewId();
        reviewService.create(course.getId(), other.getId(), request("남 후기"));

        List<ReviewItem> items = list(course.getId(), me.getId(), null, 10, null).items();
        ReviewItem mine =
                items.stream().filter(i -> i.reviewId().equals(mineId)).findFirst().orElseThrow();
        ReviewItem others =
                items.stream().filter(i -> !i.reviewId().equals(mineId)).findFirst().orElseThrow();

        assertThat(mine.mine()).isTrue();
        assertThat(mine.editable()).isTrue();
        assertThat(others.mine()).isFalse();
        assertThat(others.editable()).isFalse();
    }

    @Test
    @DisplayName("레벨업하면 이전 레벨 후기는 isEditable=false가 된다")
    void 레벨업하면_수정불가_표시() {
        Course course = seedCourse();
        Member me = seedMember("levelup@kakao.com", Level.ROOKIE);
        reviewService.create(course.getId(), me.getId(), request("루키 때 쓴 글"));

        me.applyOnboarding(Level.OWNER, null);

        List<ReviewItem> items = list(course.getId(), me.getId(), "ROOKIE", 10, null).items();
        assertThat(items).hasSize(1);
        assertThat(items.get(0).mine()).isTrue();
        assertThat(items.get(0).editable()).isFalse();
    }

    @Test
    @DisplayName("차단한 회원의 후기는 내 목록에서만 빠지고, 해제하면 다시 보인다")
    void 차단_목록_제외() {
        Course course = seedCourse();
        Member me = seedMember("blocker@kakao.com", Level.SEED);
        Member noisy = seedMember("noisy@kakao.com", Level.SEED);
        reviewService.create(course.getId(), me.getId(), request("내 후기"));
        reviewService.create(course.getId(), noisy.getId(), request("차단 대상 후기"));

        memberBlockService.block(me.getId(), noisy.getId());
        CursorPage<ReviewItem> blocked = list(course.getId(), me.getId(), null, 10, null);
        assertThat(blocked.items()).hasSize(1);
        assertThat(blocked.totalCount()).isEqualTo(1);
        // 차단당한 쪽 화면은 그대로 2건
        assertThat(list(course.getId(), noisy.getId(), null, 10, null).items()).hasSize(2);

        memberBlockService.unblock(me.getId(), noisy.getId());
        assertThat(list(course.getId(), me.getId(), null, 10, null).items()).hasSize(2);
    }

    @Test
    @DisplayName("탈퇴·익명화된 회원의 후기는 남고 닉네임만 null이다")
    void 익명화_회원_후기() {
        Course course = seedCourse();
        Member reader = seedMember("reader@kakao.com", Level.SEED);
        Member gone = seedMember("gone@kakao.com", Level.SEED);
        gone.assignNickname("사라질 닉네임");
        reviewService.create(course.getId(), gone.getId(), request("탈퇴 예정자 후기"));

        gone.anonymize(java.time.LocalDateTime.now());

        List<ReviewItem> items = list(course.getId(), reader.getId(), null, 10, null).items();
        assertThat(items).hasSize(1);
        assertThat(items.get(0).nickname()).isNull();
    }
}
