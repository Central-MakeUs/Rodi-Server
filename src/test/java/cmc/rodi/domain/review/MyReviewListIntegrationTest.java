package cmc.rodi.domain.review;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import cmc.rodi.domain.member.entity.Level;
import cmc.rodi.domain.member.entity.Member;
import cmc.rodi.domain.member.repository.MemberRepository;
import cmc.rodi.domain.place.entity.Course;
import cmc.rodi.domain.place.repository.CourseRepository;
import cmc.rodi.domain.review.dto.MyReviewItem;
import cmc.rodi.domain.review.dto.ReviewRequest;
import cmc.rodi.domain.review.entity.Congestion;
import cmc.rodi.domain.review.entity.Difficulty;
import cmc.rodi.domain.review.entity.PracticeMethod;
import cmc.rodi.domain.review.repository.ReviewRepository;
import cmc.rodi.domain.review.service.ReviewQueryService;
import cmc.rodi.domain.review.service.ReviewService;
import cmc.rodi.global.common.pagination.CursorPage;
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

/** 내가 쓴 후기 목록 — 레벨 무관 전체 노출, 비공개 포함, 장소명·isEditable, 커서 2페이지, 남의 후기 제외. */
@SpringBootTest
@Transactional
@Import(TestcontainersConfiguration.class)
class MyReviewListIntegrationTest {

    private static final GeometryFactory GEO = new GeometryFactory(new PrecisionModel(), 4326);

    @Autowired ReviewService reviewService;
    @Autowired ReviewQueryService reviewQueryService;
    @Autowired ReviewRepository reviewRepository;
    @Autowired CourseRepository courseRepository;
    @Autowired MemberRepository memberRepository;

    private Course seedCourse(String name) {
        return courseRepository.save(
                Course.builder()
                        .name(name)
                        .location(GEO.createPoint(new Coordinate(127.2, 37.7)))
                        .build());
    }

    private Member seedMember(String email, Level level) {
        Member member = memberRepository.save(Member.createBySocial(email));
        member.applyOnboarding(level, null);
        return member;
    }

    private Long write(Course course, Member member, String content) {
        return reviewService
                .create(
                        course.getId(),
                        member.getId(),
                        new ReviewRequest(
                                true,
                                Difficulty.EASY,
                                Congestion.NORMAL,
                                PracticeMethod.SOLO,
                                content,
                                null))
                .reviewId();
    }

    @Test
    @DisplayName("레벨이 바뀌어도 내 후기가 전부 나오고, 수정 가능 여부만 레벨을 따른다")
    void 레벨_무관_전체_노출() {
        Course course = seedCourse("내 후기 코스");
        Member me = seedMember("mine@kakao.com", Level.OWNER);
        write(course, me, "오너일 때 쓴 후기");

        me.applyOnboarding(Level.ROOKIE, null); // 레벨이 바뀌어도 목록에는 남는다
        write(course, me, "루키일 때 쓴 후기");

        CursorPage<MyReviewItem> page = reviewQueryService.getMyReviews(me.getId(), 10, null);

        assertThat(page.items())
                .extracting(MyReviewItem::content, MyReviewItem::editable)
                .containsExactly(
                        tuple("루키일 때 쓴 후기", true), // 현재 레벨과 같음
                        tuple("오너일 때 쓴 후기", false));
        assertThat(page.totalCount()).isEqualTo(2);
        assertThat(page.items().get(0).placeId()).isEqualTo(course.getId());
        assertThat(page.items().get(0).placeName()).isEqualTo("내 후기 코스");
    }

    @Test
    @DisplayName("신고 누적으로 비공개된 내 후기도 isHidden=true로 함께 나온다")
    void 비공개_후기_포함() {
        Course course = seedCourse("비공개 코스");
        Member me = seedMember("hidden@kakao.com", Level.SEED);
        Long reviewId = write(course, me, "숨겨진 후기");
        reviewRepository.findById(reviewId).orElseThrow().hide(LocalDateTime.now());

        CursorPage<MyReviewItem> page = reviewQueryService.getMyReviews(me.getId(), 10, null);

        assertThat(page.items()).hasSize(1);
        assertThat(page.items().get(0).hidden()).isTrue();
    }

    @Test
    @DisplayName("남의 후기는 섞이지 않고, 커서로 이어지며 중복이 없다")
    void 커서_페이지네이션() {
        Course course = seedCourse("페이지 코스");
        Member me = seedMember("page@kakao.com", Level.SEED);
        Member other = seedMember("other@kakao.com", Level.SEED);
        write(course, me, "내 후기 1");
        write(course, me, "내 후기 2");
        write(course, me, "내 후기 3");
        write(course, other, "남의 후기");

        CursorPage<MyReviewItem> first = reviewQueryService.getMyReviews(me.getId(), 2, null);
        assertThat(first.items()).hasSize(2);
        assertThat(first.hasNext()).isTrue();
        assertThat(first.totalCount()).isEqualTo(3); // 남의 후기는 세지 않는다

        CursorPage<MyReviewItem> second =
                reviewQueryService.getMyReviews(me.getId(), 2, first.nextCursor());
        assertThat(second.items()).hasSize(1);
        assertThat(second.hasNext()).isFalse();
        assertThat(second.totalCount()).isNull(); // 이후 페이지는 총계 없음
        assertThat(second.items().get(0).content()).isEqualTo("내 후기 1");
    }
}
