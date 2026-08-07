package cmc.rodi.domain.review.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cmc.rodi.domain.review.dto.ReviewCreateResponse;
import cmc.rodi.domain.review.dto.ReviewListRequest;
import cmc.rodi.domain.review.service.ReviewQueryService;
import cmc.rodi.domain.review.service.ReviewService;
import cmc.rodi.global.auth.jwt.JwtAuthenticationFilter;
import cmc.rodi.global.auth.resolver.CurrentMemberArgumentResolver;
import cmc.rodi.global.common.form.FormOption;
import cmc.rodi.global.common.form.FormResponse;
import cmc.rodi.global.common.form.FormType;
import cmc.rodi.global.common.notification.DiscordNotifier;
import cmc.rodi.global.common.pagination.CursorPage;
import cmc.rodi.global.config.SecurityConfig;
import cmc.rodi.global.config.WebConfig;
import cmc.rodi.global.exception.GlobalExceptionHandler;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.ComponentScan.Filter;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

// @CurrentMember 주입을 위해 WebConfig·리졸버는 포함하고, 실제 보안설정·JWT 필터만 제외
@WebMvcTest(
        controllers = ReviewController.class,
        excludeFilters =
                @Filter(
                        type = FilterType.ASSIGNABLE_TYPE,
                        classes = {SecurityConfig.class, JwtAuthenticationFilter.class}))
@AutoConfigureMockMvc(addFilters = false)
@Import({
    GlobalExceptionHandler.class,
    DiscordNotifier.class,
    WebConfig.class,
    CurrentMemberArgumentResolver.class
})
class ReviewControllerTest {

    private static final String VALID_BODY =
            """
            {
              "isRecommended": true,
              "difficulty": "EASY",
              "congestion": "NORMAL",
              "practiceMethod": "ACCOMPANIED",
              "content": "차선이 넓어서 처음 도로 나갈 때 좋았어요.",
              "caution": "주말 오후엔 자전거가 많습니다."
            }
            """;

    @Autowired MockMvc mockMvc;

    @MockitoBean ReviewService reviewService;
    @MockitoBean ReviewQueryService reviewQueryService;

    private void authenticate(long memberId) {
        SecurityContextHolder.getContext()
                .setAuthentication(
                        new UsernamePasswordAuthenticationToken(memberId, null, List.of()));
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("후기 작성 — 생성된 reviewId를 반환하고 현재 회원 id로 서비스에 위임한다")
    void 작성() throws Exception {
        authenticate(7L);
        when(reviewService.create(eq(1L), eq(7L), any())).thenReturn(new ReviewCreateResponse(31L));

        mockMvc.perform(
                        post("/api/v1/places/1/reviews")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(VALID_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.data.reviewId").value(31));

        verify(reviewService).create(eq(1L), eq(7L), any());
    }

    @Test
    @DisplayName("필수 항목 누락·150자 초과 내용은 400")
    void 작성_검증() throws Exception {
        authenticate(7L);

        mockMvc.perform(
                        post("/api/v1/places/1/reviews")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"difficulty\": \"EASY\"}"))
                .andExpect(status().isBadRequest());

        String tooLong = "가".repeat(151);
        String body =
                """
                {
                  "isRecommended": true,
                  "difficulty": "EASY",
                  "congestion": "NORMAL",
                  "practiceMethod": "SOLO",
                  "content": "%s"
                }
                """
                        .formatted(tooLong);
        mockMvc.perform(
                        post("/api/v1/places/1/reviews")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("수정·삭제는 데이터 없이 200 + 현재 회원 id로 위임")
    void 수정과_삭제() throws Exception {
        authenticate(7L);

        mockMvc.perform(
                        put("/api/v1/reviews/31")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(VALID_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").doesNotExist());
        verify(reviewService).update(eq(31L), eq(7L), any());

        mockMvc.perform(delete("/api/v1/reviews/31")).andExpect(status().isOk());
        verify(reviewService).delete(31L, 7L);
    }

    @Test
    @DisplayName("목록 — level·size·cursor가 요청 객체로 전달된다(size 범위 밖은 400)")
    void 목록_파라미터() throws Exception {
        authenticate(7L);
        when(reviewQueryService.getReviews(eq(1L), eq(7L), any()))
                .thenReturn(CursorPage.first(List.of(), false, null, 0));

        mockMvc.perform(get("/api/v1/places/1/reviews").param("level", "ROOKIE").param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isArray());
        verify(reviewQueryService).getReviews(1L, 7L, new ReviewListRequest("ROOKIE", 5, null));

        mockMvc.perform(get("/api/v1/places/1/reviews").param("size", "0"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("신고는 데이터 없이 200 + 사유 누락·기타인데 직접입력 없으면 400")
    void 신고() throws Exception {
        authenticate(7L);

        mockMvc.perform(
                        post("/api/v1/reviews/31/report")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"reason\": \"ABUSE\", \"detail\": \"비방 표현\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").doesNotExist());
        verify(reviewService).report(eq(31L), eq(7L), any());

        mockMvc.perform(
                        post("/api/v1/reviews/31/report")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(
                        post("/api/v1/reviews/31/report")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"reason\": \"OTHER\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("신고 사유 폼 — 선택지가 order 순으로 내려가고 기타만 텍스트 입력 필드를 갖는다")
    void 신고사유_폼() throws Exception {
        authenticate(7L);
        when(reviewService.getReportForm())
                .thenReturn(
                        new FormResponse(
                                "REVIEW_REPORT_REASON",
                                FormType.SINGLE_SELECT,
                                "신고 사유",
                                null,
                                true,
                                List.of(
                                        FormOption.of("SPAM", "스팸/광고", 1),
                                        FormOption.withTextInput(
                                                "OTHER", "기타", 2, "이유를 작성해주세요", 100))));

        mockMvc.perform(get("/api/v1/reviews/report-form"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.questionId").value("REVIEW_REPORT_REASON"))
                .andExpect(jsonPath("$.data.type").value("SINGLE_SELECT"))
                .andExpect(jsonPath("$.data.title").value("신고 사유"))
                .andExpect(jsonPath("$.data.description").doesNotExist()) // 설명 없으면 키 자체를 생략
                .andExpect(jsonPath("$.data.options[0].code").value("SPAM"))
                .andExpect(jsonPath("$.data.options[0].requiresTextInput").value(false))
                .andExpect(jsonPath("$.data.options[0].textInputMaxLength").doesNotExist())
                .andExpect(jsonPath("$.data.options[1].requiresTextInput").value(true))
                .andExpect(jsonPath("$.data.options[1].textInputPlaceholder").value("이유를 작성해주세요"))
                .andExpect(jsonPath("$.data.options[1].textInputMaxLength").value(100));
    }
}
