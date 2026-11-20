package cmc.rodi.domain.place.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cmc.rodi.domain.member.entity.PracticeType;
import cmc.rodi.domain.place.dto.PlaceListRequest;
import cmc.rodi.domain.place.dto.PlaceSearchRequest;
import cmc.rodi.domain.place.service.BookmarkQueryService;
import cmc.rodi.domain.place.service.BookmarkService;
import cmc.rodi.domain.place.service.PlaceQueryService;
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
import org.mockito.ArgumentCaptor;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** 유형 필터 파라미터(스펙 019) — practiceTypes 바인딩 형식(반복·콤마·빈 값)과 무효 값 400. */
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

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private void authenticate(long memberId) {
        SecurityContextHolder.getContext()
                .setAuthentication(
                        new UsernamePasswordAuthenticationToken(memberId, null, List.of()));
    }

    private static MockHttpServletRequestBuilder places() {
        return get("/api/v1/places")
                .param("swLat", "37.40")
                .param("swLng", "126.60")
                .param("neLat", "37.55")
                .param("neLng", "126.80")
                .param("lat", "37.45")
                .param("lng", "126.70");
    }

    private List<PracticeType> listFilterOf(MockHttpServletRequestBuilder request)
            throws Exception {
        mockMvc.perform(request).andExpect(status().isOk());
        ArgumentCaptor<PlaceListRequest> captor = ArgumentCaptor.forClass(PlaceListRequest.class);
        verify(placeQueryService).getPlaces(captor.capture(), any());
        return captor.getValue().practiceTypes();
    }

    @Test
    @DisplayName("목록: 반복 형식 practiceTypes가 그대로 필터로 전달된다(비로그인도 200)")
    void 목록_반복_형식() throws Exception {
        assertThat(
                        listFilterOf(
                                places().param("practiceTypes", "U_TURN")
                                        .param("practiceTypes", "PARKING")))
                .containsExactly(PracticeType.U_TURN, PracticeType.PARKING);
    }

    @Test
    @DisplayName("목록: 콤마 형식도 반복 형식과 같고, 중복 값은 하나로 본다")
    void 목록_콤마_형식() throws Exception {
        assertThat(listFilterOf(places().param("practiceTypes", "U_TURN,PARKING,U_TURN")))
                .containsExactly(PracticeType.U_TURN, PracticeType.PARKING);
    }

    @Test
    @DisplayName("목록: 생략·빈 값은 필터 없음(빈 목록)")
    void 목록_생략_빈값() throws Exception {
        assertThat(listFilterOf(places().param("practiceTypes", ""))).isEmpty();
    }

    @Test
    @DisplayName("목록: 파라미터 생략도 필터 없음")
    void 목록_생략() throws Exception {
        assertThat(listFilterOf(places())).isEmpty();
    }

    @Test
    @DisplayName("목록·검색·마커: 없는 연습유형은 400이고 조회하지 않는다")
    void 무효_값_400() throws Exception {
        authenticate(7L);

        mockMvc.perform(places().param("practiceTypes", "NOT_A_TYPE"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.isSuccess").value(false));
        mockMvc.perform(
                        get("/api/v1/places/search")
                                .param("keyword", "강남")
                                .param("lat", "37.45")
                                .param("lng", "126.70")
                                .param("practiceTypes", "NOT_A_TYPE"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/places/coordinates").param("practiceTypes", "NOT_A_TYPE"))
                .andExpect(status().isBadRequest());

        verify(placeQueryService, never()).getPlaces(any(), any());
        verify(placeQueryService, never()).searchPlaces(any(), any());
        verify(placeQueryService, never()).getAllCoordinates(any());
    }

    @Test
    @DisplayName("검색: practiceTypes가 필터로 전달되고 회원 id도 함께 넘어간다")
    void 검색_필터_전달() throws Exception {
        authenticate(7L);

        mockMvc.perform(
                        get("/api/v1/places/search")
                                .param("keyword", "강남")
                                .param("lat", "37.45")
                                .param("lng", "126.70")
                                .param("practiceTypes", "INTERSECTION"))
                .andExpect(status().isOk());

        ArgumentCaptor<PlaceSearchRequest> captor =
                ArgumentCaptor.forClass(PlaceSearchRequest.class);
        verify(placeQueryService).searchPlaces(captor.capture(), eq(7L));
        assertThat(captor.getValue().practiceTypes()).containsExactly(PracticeType.INTERSECTION);
    }

    @Test
    @DisplayName("마커: practiceTypes가 있으면 그 목록, 없으면 빈 목록으로 조회한다")
    void 마커_필터_전달() throws Exception {
        mockMvc.perform(get("/api/v1/places/coordinates").param("practiceTypes", "PARKING,U_TURN"))
                .andExpect(status().isOk());
        verify(placeQueryService)
                .getAllCoordinates(List.of(PracticeType.PARKING, PracticeType.U_TURN));

        mockMvc.perform(get("/api/v1/places/coordinates")).andExpect(status().isOk());
        verify(placeQueryService).getAllCoordinates(List.of());
    }
}
