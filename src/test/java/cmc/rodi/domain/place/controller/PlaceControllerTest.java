package cmc.rodi.domain.place.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cmc.rodi.domain.member.service.RecentSearchService;
import cmc.rodi.domain.place.dto.PlaceListItem;
import cmc.rodi.domain.place.dto.PlaceSearchRequest;
import cmc.rodi.domain.place.service.BookmarkQueryService;
import cmc.rodi.domain.place.service.BookmarkService;
import cmc.rodi.domain.place.service.PlaceQueryService;
import cmc.rodi.global.auth.jwt.JwtAuthenticationFilter;
import cmc.rodi.global.auth.resolver.CurrentMemberArgumentResolver;
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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** 검색 자동저장 훅 — 첫 페이지에서만 최근 검색어를 기록하고, 페이지네이션(cursor) 재조회는 기록하지 않는지 검증. */
@WebMvcTest(
        controllers = PlaceController.class,
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
class PlaceControllerTest {

    @Autowired MockMvc mockMvc;

    @MockitoBean PlaceQueryService placeQueryService;
    @MockitoBean BookmarkService bookmarkService;
    @MockitoBean BookmarkQueryService bookmarkQueryService;
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
    @DisplayName("검색 첫 페이지: 키워드를 최근 검색어로 기록")
    void 첫페이지_기록() throws Exception {
        authenticate(7L);
        when(placeQueryService.searchPlaces(any(PlaceSearchRequest.class), eq(7L)))
                .thenReturn(CursorPage.<PlaceListItem>first(List.of(), false, null, 0L));

        mockMvc.perform(
                        get("/api/v1/places/search")
                                .param("keyword", "강남")
                                .param("lat", "37.5")
                                .param("lng", "127.0"))
                .andExpect(status().isOk());

        verify(recentSearchService).record(7L, "강남");
    }

    @Test
    @DisplayName("검색 다음 페이지(cursor): 최근 검색어를 기록하지 않음")
    void 페이지네이션_미기록() throws Exception {
        authenticate(7L);
        when(placeQueryService.searchPlaces(any(PlaceSearchRequest.class), eq(7L)))
                .thenReturn(CursorPage.<PlaceListItem>next(List.of(), false, null));

        mockMvc.perform(
                        get("/api/v1/places/search")
                                .param("keyword", "강남")
                                .param("lat", "37.5")
                                .param("lng", "127.0")
                                .param("cursor", "eyJkIjoxfQ=="))
                .andExpect(status().isOk());

        verify(recentSearchService, never()).record(any(), any());
    }
}
