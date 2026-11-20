package cmc.rodi.global.common.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cmc.rodi.domain.member.entity.Level;
import cmc.rodi.domain.member.entity.Member;
import cmc.rodi.domain.member.repository.MemberRepository;
import cmc.rodi.domain.place.entity.Course;
import cmc.rodi.domain.place.repository.CourseRepository;
import cmc.rodi.domain.review.entity.Review;
import cmc.rodi.domain.review.repository.ReviewRepository;
import cmc.rodi.support.TestcontainersConfiguration;
import com.jayway.jsonpath.JsonPath;
import jakarta.persistence.EntityManager;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * 텍스트 길이 제한을 grapheme cluster로 세는지 확인한다(스펙 018).
 *
 * <p>단순 카운트는 {@link GraphemeSizeValidatorTest}가 보고, 여기서는 <b>DB까지 실제로 저장되는지</b>를 본다 — 검증만 고치고 컬럼을
 * 넓히지 않으면 통과한 값이 저장 단계에서 터지기 때문이다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Import(TestcontainersConfiguration.class)
class TextLengthGraphemeIntegrationTest {

    private static final GeometryFactory GEO = new GeometryFactory(new PrecisionModel(), 4326);

    /** 코드포인트 7개짜리 한 글자 — VARCHAR(30)이면 30개가 들어가지 않는다. */
    private static final String FAMILY = "👨‍👩‍👧‍👦";

    @Autowired MockMvc mockMvc;
    @Autowired MemberRepository memberRepository;
    @Autowired CourseRepository courseRepository;
    @Autowired ReviewRepository reviewRepository;
    @Autowired EntityManager entityManager;

    private Member me;

    @BeforeEach
    void 로그인() {
        me = memberRepository.save(Member.createBySocial("grapheme@kakao.com"));
        SecurityContextHolder.getContext()
                .setAuthentication(
                        new UsernamePasswordAuthenticationToken(me.getId(), null, List.of()));
    }

    @AfterEach
    void 로그아웃() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("운전 목표 — 이모지 30개(UTF-16 60)가 저장된다")
    void 운전목표_이모지_30개() throws Exception {
        String goal = "😀".repeat(30);

        patchGoal(goal).andExpect(status().isOk());

        assertThat(reloadGoal()).isEqualTo(goal);
    }

    @Test
    @DisplayName("운전 목표 — 가족 이모지 30개(코드포인트 210)도 잘리지 않고 저장된다")
    void 운전목표_가족이모지_30개() throws Exception {
        String goal = FAMILY.repeat(30);
        assertThat(goal.codePointCount(0, goal.length())).isEqualTo(210); // 컬럼 폭 확장 전제

        patchGoal(goal).andExpect(status().isOk());

        assertThat(reloadGoal()).isEqualTo(goal);
    }

    @Test
    @DisplayName("운전 목표 — 31자는 400으로 거절한다(자르지 않는다)")
    void 운전목표_31자_거절() throws Exception {
        patchGoal("가".repeat(31))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_400"))
                .andExpect(jsonPath("$.data.drivingGoal").value("30자 이하로 입력해주세요."));
        patchGoal("😀".repeat(31)).andExpect(status().isBadRequest());

        assertThat(reloadGoal()).isNull(); // 아무것도 저장되지 않았다
    }

    @Test
    @DisplayName("운전 목표 — 한글 30자는 그대로 통과한다(기존 경계 유지)")
    void 운전목표_한글_30자() throws Exception {
        String goal = "가".repeat(30);

        patchGoal(goal).andExpect(status().isOk());

        assertThat(reloadGoal()).isEqualTo(goal);
    }

    @Test
    @DisplayName("온보딩의 운전 목표도 같은 기준으로 동작한다")
    void 온보딩_운전목표() throws Exception {
        String goal = FAMILY.repeat(30);

        mockMvc.perform(
                        post("/api/v1/members/me/onboarding")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(onboardingBody(goal)))
                .andExpect(status().isOk());

        assertThat(reloadGoal()).isEqualTo(goal);
    }

    @Test
    @DisplayName("후기 내용 — 이모지 150개는 저장되고 151개는 400이다")
    void 후기_내용_경계값() throws Exception {
        me.applyOnboarding(Level.SEED, null); // 후기는 레벨 있는 회원만 쓴다
        Long placeId = seedCourse().getId();
        String content = "😀".repeat(150);

        mockMvc.perform(
                        post("/api/v1/places/" + placeId + "/reviews")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(reviewBody(content)))
                .andExpect(status().isOk());

        mockMvc.perform(
                        post("/api/v1/places/" + placeId + "/reviews")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(reviewBody("😀".repeat(151))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.content").value("150자 이하로 입력해주세요."));

        entityManager.flush();
        entityManager.clear();
        List<Review> reviews = reviewRepository.findAll();
        assertThat(reviews)
                .singleElement()
                .satisfies(r -> assertThat(r.getContent()).isEqualTo(content));
    }

    @Test
    @DisplayName("후기 수정(PUT)도 같은 기준으로 동작한다")
    void 후기_수정_경계값() throws Exception {
        me.applyOnboarding(Level.SEED, null);
        Long placeId = seedCourse().getId();
        String created =
                mockMvc.perform(
                                post("/api/v1/places/" + placeId + "/reviews")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(reviewBody("처음 쓴 후기")))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        Long reviewId = JsonPath.parse(created).read("$.data.reviewId", Long.class);

        String edited = FAMILY.repeat(150);
        mockMvc.perform(
                        put("/api/v1/reviews/" + reviewId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(reviewBody(edited)))
                .andExpect(status().isOk());
        mockMvc.perform(
                        put("/api/v1/reviews/" + reviewId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(reviewBody("😀".repeat(151))))
                .andExpect(status().isBadRequest());

        entityManager.flush();
        entityManager.clear();
        assertThat(reviewRepository.findById(reviewId).orElseThrow().getContent())
                .isEqualTo(edited);
    }

    private org.springframework.test.web.servlet.ResultActions patchGoal(String goal)
            throws Exception {
        return mockMvc.perform(
                patch("/api/v1/members/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"drivingGoal\":\"" + goal + "\"}"));
    }

    /** 영속성 컨텍스트를 비우고 DB에서 다시 읽는다 — 컬럼 폭 문제를 잡으려면 실제 write/read가 일어나야 한다. */
    private String reloadGoal() {
        entityManager.flush();
        entityManager.clear();
        return memberRepository.findById(me.getId()).orElseThrow().getDrivingGoal();
    }

    private static String onboardingBody(String goal) {
        return "{\"drivingPeriod\":\"UNDER_1_MONTH\",\"level\":\"SEED\",\"drivingGoal\":\""
                + goal
                + "\"}";
    }

    private static String reviewBody(String content) {
        return "{\"isRecommended\":true,\"difficulty\":\"EASY\",\"congestion\":\"QUIET\","
                + "\"practiceMethod\":\"SOLO\",\"content\":\""
                + content
                + "\"}";
    }

    private Course seedCourse() {
        return courseRepository.save(Course.builder().name("이모지 코스").location(point()).build());
    }

    private static Point point() {
        return GEO.createPoint(new Coordinate(127.0, 37.5));
    }
}
