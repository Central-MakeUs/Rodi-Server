package cmc.rodi.domain.review.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cmc.rodi.domain.review.dto.ReviewCreateResponse;
import cmc.rodi.domain.review.service.ReviewService;
import cmc.rodi.global.auth.jwt.JwtAuthenticationFilter;
import cmc.rodi.global.auth.resolver.CurrentMemberArgumentResolver;
import cmc.rodi.global.common.notification.DiscordNotifier;
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
    @DisplayName("필수 항목 누락·1000자 초과 내용은 400")
    void 작성_검증() throws Exception {
        authenticate(7L);

        mockMvc.perform(
                        post("/api/v1/places/1/reviews")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"difficulty\": \"EASY\"}"))
                .andExpect(status().isBadRequest());

        String tooLong = "가".repeat(1001);
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
}
