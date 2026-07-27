package cmc.rodi.domain.member.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cmc.rodi.domain.member.dto.RecentSearchResponse;
import cmc.rodi.domain.member.service.RecentSearchService;
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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

// @CurrentMember 주입을 위해 WebConfig·리졸버는 포함하고, 실제 보안설정·JWT 필터만 제외
@WebMvcTest(
        controllers = RecentSearchController.class,
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
class RecentSearchControllerTest {

    @Autowired MockMvc mockMvc;

    @MockitoBean RecentSearchService recentSearchService;

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private void authenticate(long memberId) {
        SecurityContextHolder.getContext()
                .setAuthentication(
                        new UsernamePasswordAuthenticationToken(memberId, null, List.of()));
    }

    @Test
    @DisplayName("조회: 200 + 최신순 목록 반환")
    void 조회() throws Exception {
        authenticate(7L);
        when(recentSearchService.getRecent(7L))
                .thenReturn(
                        List.of(
                                new RecentSearchResponse(2L, "판교"),
                                new RecentSearchResponse(1L, "강남")));

        mockMvc.perform(get("/api/v1/members/me/recent-searches"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].keyword").value("판교"))
                .andExpect(jsonPath("$.data[0].id").value(2))
                .andExpect(jsonPath("$.data[1].keyword").value("강남"));
    }

    @Test
    @DisplayName("개별 삭제: 200 + 회원 id·검색어 id로 위임")
    void 개별_삭제() throws Exception {
        authenticate(7L);

        mockMvc.perform(delete("/api/v1/members/me/recent-searches/5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true));

        verify(recentSearchService).delete(7L, 5L);
    }

    @Test
    @DisplayName("전체 삭제: 200 + 회원 id로 위임")
    void 전체_삭제() throws Exception {
        authenticate(7L);

        mockMvc.perform(delete("/api/v1/members/me/recent-searches"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true));

        verify(recentSearchService).deleteAll(7L);
    }
}
