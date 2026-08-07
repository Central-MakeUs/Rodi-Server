package cmc.rodi.domain.review;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cmc.rodi.domain.member.entity.Level;
import cmc.rodi.domain.member.entity.Member;
import cmc.rodi.domain.member.exception.MemberErrorCode;
import cmc.rodi.domain.member.repository.MemberBlockRepository;
import cmc.rodi.domain.member.repository.MemberRepository;
import cmc.rodi.domain.member.service.MemberBlockService;
import cmc.rodi.domain.place.entity.Course;
import cmc.rodi.domain.place.repository.CourseRepository;
import cmc.rodi.domain.review.dto.ReviewItem;
import cmc.rodi.domain.review.dto.ReviewListRequest;
import cmc.rodi.domain.review.dto.ReviewReportRequest;
import cmc.rodi.domain.review.dto.ReviewRequest;
import cmc.rodi.domain.review.entity.Congestion;
import cmc.rodi.domain.review.entity.Difficulty;
import cmc.rodi.domain.review.entity.PracticeMethod;
import cmc.rodi.domain.review.entity.ReportReason;
import cmc.rodi.domain.review.exception.ReviewErrorCode;
import cmc.rodi.domain.review.repository.ReviewReportRepository;
import cmc.rodi.domain.review.repository.ReviewRepository;
import cmc.rodi.domain.review.service.ReviewQueryService;
import cmc.rodi.domain.review.service.ReviewService;
import cmc.rodi.global.common.form.FormOption;
import cmc.rodi.global.common.form.FormResponse;
import cmc.rodi.global.common.form.FormType;
import cmc.rodi.global.common.pagination.CursorPage;
import cmc.rodi.global.exception.BusinessException;
import cmc.rodi.global.exception.ErrorCode;
import cmc.rodi.support.TestcontainersConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/** 후기 신고·회원 차단의 멱등성과 거부 규칙, 그리고 미인증 401. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Import(TestcontainersConfiguration.class)
class ReviewReportBlockIntegrationTest {

    private static final GeometryFactory GEO = new GeometryFactory(new PrecisionModel(), 4326);

    @Autowired ReviewService reviewService;
    @Autowired ReviewQueryService reviewQueryService;
    @Autowired MemberBlockService memberBlockService;
    @Autowired ReviewRepository reviewRepository;
    @Autowired ReviewReportRepository reviewReportRepository;
    @Autowired MemberBlockRepository memberBlockRepository;
    @Autowired CourseRepository courseRepository;
    @Autowired MemberRepository memberRepository;
    @Autowired MockMvc mockMvc;

    private Course seedCourse() {
        return courseRepository.save(
                Course.builder()
                        .name("소셜 코스")
                        .location(GEO.createPoint(new Coordinate(128.0, 36.0)))
                        .build());
    }

    private Member seedMember(String email) {
        Member member = memberRepository.save(Member.createBySocial(email));
        member.applyOnboarding(Level.SEED, null);
        return member;
    }

    private Long seedReview(Course course, Member author) {
        return reviewService
                .create(
                        course.getId(),
                        author.getId(),
                        new ReviewRequest(
                                true,
                                Difficulty.EASY,
                                Congestion.QUIET,
                                PracticeMethod.SOLO,
                                "후기 내용",
                                null))
                .reviewId();
    }

    @Test
    @DisplayName("서로 다른 5명에게 신고되면 비공개 — 4명까지는 공개, 5번째에 숨겨진다")
    void 신고_누적_자동_비공개() {
        Course course = seedCourse();
        Member author = seedMember("hide-author@kakao.com");
        Long reviewId = seedReview(course, author);
        ReviewReportRequest request = new ReviewReportRequest(ReportReason.SPAM, null);

        for (int i = 1; i <= 4; i++) {
            reviewService.report(reviewId, seedMember("r" + i + "@kakao.com").getId(), request);
        }
        assertThat(reviewRepository.findById(reviewId).orElseThrow().isHidden()).isFalse();

        reviewService.report(reviewId, seedMember("r5@kakao.com").getId(), request);
        assertThat(reviewRepository.findById(reviewId).orElseThrow().isHidden()).isTrue();
    }

    @Test
    @DisplayName("비공개 후기는 남의 목록·요약에서 빠지고, 작성자 본인 목록에만 isHidden=true로 남는다")
    void 비공개_후기_노출범위() {
        Course course = seedCourse();
        Member author = seedMember("hidden-author@kakao.com");
        Member reader = seedMember("reader@kakao.com");
        Long reviewId = seedReview(course, author);
        ReviewReportRequest request = new ReviewReportRequest(ReportReason.ABUSE, null);
        for (int i = 1; i <= 5; i++) {
            reviewService.report(reviewId, seedMember("v" + i + "@kakao.com").getId(), request);
        }

        // 남에게는 목록에서 사라진다
        CursorPage<ReviewItem> othersView =
                reviewQueryService.getReviews(
                        course.getId(), reader.getId(), new ReviewListRequest("ALL", 10, null));
        assertThat(othersView.items()).isEmpty();
        assertThat(othersView.totalCount()).isZero();

        // 작성자 본인에게는 남고 비공개 표시가 붙는다
        CursorPage<ReviewItem> myView =
                reviewQueryService.getReviews(
                        course.getId(), author.getId(), new ReviewListRequest("ALL", 10, null));
        assertThat(myView.items()).hasSize(1);
        assertThat(myView.items().get(0).hidden()).isTrue();

        // 요약 집계에서는 본인에게도 빠진다
        assertThat(
                        reviewQueryService
                                .getSummary(
                                        course.getId(),
                                        author.getId(),
                                        ReviewListRequest.ofLevel("ALL"))
                                .totalCount())
                .isZero();
    }

    @Test
    @DisplayName("신고 사유 폼은 5개 선택지를 order 순으로 주고, 기타만 직접 입력을 요구한다")
    void 신고사유_폼() {
        FormResponse form = reviewService.getReportForm();

        assertThat(form.questionId()).isEqualTo("REVIEW_REPORT_REASON");
        assertThat(form.type()).isEqualTo(FormType.SINGLE_SELECT);
        assertThat(form.title()).isEqualTo("신고 사유");
        assertThat(form.required()).isTrue();
        assertThat(form.options())
                .extracting(FormOption::code)
                .containsExactly("SPAM", "ABUSE", "IRRELEVANT", "FALSE_INFO", "OTHER");
        assertThat(form.options()).extracting(FormOption::order).containsExactly(1, 2, 3, 4, 5);

        FormOption other = form.options().get(4);
        assertThat(other.label()).isEqualTo("기타");
        assertThat(other.requiresTextInput()).isTrue();
        assertThat(other.textInputPlaceholder()).isEqualTo("이유를 작성해주세요");
        assertThat(other.textInputMaxLength()).isEqualTo(100);
        // 나머지는 텍스트 입력 필드가 아예 없다(응답에서도 생략)
        assertThat(form.options().subList(0, 4))
                .allSatisfy(
                        option -> {
                            assertThat(option.requiresTextInput()).isFalse();
                            assertThat(option.textInputPlaceholder()).isNull();
                            assertThat(option.textInputMaxLength()).isNull();
                        });
    }

    @Test
    @DisplayName("신고는 중복해도 멱등이고, 본인 후기 신고는 400")
    void 신고_규칙() {
        Course course = seedCourse();
        Member author = seedMember("report-author@kakao.com");
        Member reporter = seedMember("reporter@kakao.com");
        Long reviewId = seedReview(course, author);
        ReviewReportRequest request = new ReviewReportRequest(ReportReason.ABUSE, "비방 표현");

        reviewService.report(reviewId, reporter.getId(), request);
        assertThatCode(() -> reviewService.report(reviewId, reporter.getId(), request))
                .doesNotThrowAnyException();
        assertThat(reviewReportRepository.existsByReviewIdAndReporterId(reviewId, reporter.getId()))
                .isTrue();
        assertThat(reviewReportRepository.count()).isEqualTo(1);

        assertThatThrownBy(() -> reviewService.report(reviewId, author.getId(), request))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ReviewErrorCode.SELF_REPORT_NOT_ALLOWED);
    }

    @Test
    @DisplayName("차단·해제는 멱등이고, 자기 자신 차단은 400·없는 회원은 404")
    void 차단_규칙() {
        Member me = seedMember("block-me@kakao.com");
        Member other = seedMember("block-other@kakao.com");

        memberBlockService.block(me.getId(), other.getId());
        assertThatCode(() -> memberBlockService.block(me.getId(), other.getId()))
                .doesNotThrowAnyException();
        assertThat(memberBlockRepository.existsByBlockerIdAndBlockedId(me.getId(), other.getId()))
                .isTrue();

        memberBlockService.unblock(me.getId(), other.getId());
        assertThat(memberBlockRepository.existsByBlockerIdAndBlockedId(me.getId(), other.getId()))
                .isFalse();
        assertThatCode(() -> memberBlockService.unblock(me.getId(), other.getId()))
                .doesNotThrowAnyException();

        assertThatThrownBy(() -> memberBlockService.block(me.getId(), me.getId()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(MemberErrorCode.SELF_BLOCK_NOT_ALLOWED);
        assertThatThrownBy(() -> memberBlockService.block(me.getId(), 999_999L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.ENTITY_NOT_FOUND);
    }

    @Test
    @DisplayName("후기 API는 미인증이면 401")
    void 미인증_401() throws Exception {
        mockMvc.perform(get("/api/v1/places/1/reviews")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/places/1/reviews/summary"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(
                        post("/api/v1/places/1/reviews")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(
                        post("/api/v1/reviews/1/report")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"reason\": \"SPAM\"}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/members/1/block")).andExpect(status().isUnauthorized());
    }
}
