package cmc.rodi.domain.place.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cmc.rodi.domain.place.dto.CourseRegisterRequest;
import cmc.rodi.domain.place.dto.CourseRegisterResponse;
import cmc.rodi.domain.place.entity.ApprovalStatus;
import cmc.rodi.domain.place.service.CourseQueryService;
import cmc.rodi.domain.place.service.CourseService;
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

/** 코스 등록 요청 검증(스펙 014) — 경로 구성·연습유형 개수·글자수 제약이 400으로 걸리는지. */
@WebMvcTest(
        controllers = CourseController.class,
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
class CourseControllerTest {

    @Autowired MockMvc mockMvc;

    @MockitoBean CourseService courseService;
    @MockitoBean CourseQueryService courseQueryService;

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private void authenticate(long memberId) {
        SecurityContextHolder.getContext()
                .setAuthentication(
                        new UsernamePasswordAuthenticationToken(memberId, null, List.of()));
    }

    /** 경로점만 갈아끼우며 검증을 확인하기 위한 본문 조립. */
    private String body(String waypointsJson) {
        return body(waypointsJson, "[\"STRAIGHT\"]", "차선이 넓고, 직선 구간이 길어요.");
    }

    private String body(String waypointsJson, String practiceTypesJson, String description) {
        return """
               {
                 "address": "서울특별시 강남구",
                 "distanceMeters": 8200,
                 "waypoints": %s,
                 "practiceTypes": %s,
                 "description": "%s"
               }
               """
                .formatted(waypointsJson, practiceTypesJson, description);
    }

    private static final String START =
            """
            { "type": "START", "lat": 37.5273, "lng": 127.0403, "name": "압구정로데오역" }
            """;
    private static final String VIA =
            """
            { "type": "VIA", "lat": 37.5227, "lng": 127.0521, "name": "청담사거리" }
            """;
    private static final String DESTINATION =
            """
            { "type": "DESTINATION", "lat": 37.5133, "lng": 127.0533, "name": "삼성중앙역" }
            """;

    private void expectBadRequest(String body) throws Exception {
        authenticate(7L);

        mockMvc.perform(
                        post("/api/v1/courses")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.isSuccess").value(false));

        verify(courseService, never()).register(any(), any());
    }

    @Test
    @DisplayName("등록: 200 + 요청과 회원 id로 위임")
    void 등록() throws Exception {
        authenticate(7L);
        when(courseService.register(any(), eq(7L)))
                .thenReturn(new CourseRegisterResponse(101L, ApprovalStatus.PENDING));

        mockMvc.perform(
                        post("/api/v1/courses")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body("[%s,%s,%s]".formatted(START, VIA, DESTINATION))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.courseId").value(101))
                .andExpect(jsonPath("$.data.approvalStatus").value("PENDING"));

        verify(courseService).register(any(CourseRegisterRequest.class), eq(7L));
    }

    @Test
    @DisplayName("등록: 경유지 없이 출발·도착 2개만으로도 된다")
    void 경유지_없음() throws Exception {
        authenticate(7L);
        when(courseService.register(any(), eq(7L)))
                .thenReturn(new CourseRegisterResponse(101L, ApprovalStatus.PENDING));

        mockMvc.perform(
                        post("/api/v1/courses")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body("[%s,%s]".formatted(START, DESTINATION))))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("등록: 첫 항목이 출발지가 아니면 400")
    void 출발지_아님() throws Exception {
        expectBadRequest(body("[%s,%s]".formatted(VIA, DESTINATION)));
    }

    @Test
    @DisplayName("등록: 마지막이 도착지가 아니면 400")
    void 도착지_아님() throws Exception {
        expectBadRequest(body("[%s,%s]".formatted(START, VIA)));
    }

    @Test
    @DisplayName("등록: 도착지가 가운데 있으면 400")
    void 도착지_중간() throws Exception {
        expectBadRequest(body("[%s,%s,%s]".formatted(START, DESTINATION, DESTINATION)));
    }

    @Test
    @DisplayName("등록: 경유지가 4개(총 6개)면 400")
    void 경유지_초과() throws Exception {
        expectBadRequest(
                body("[%s,%s,%s,%s,%s,%s]".formatted(START, VIA, VIA, VIA, VIA, DESTINATION)));
    }

    @Test
    @DisplayName("등록: 출발지 지점명이 없으면 400 — 코스명의 기본값이라 필수")
    void 출발지_지점명_없음() throws Exception {
        String namelessStart =
                """
                { "type": "START", "lat": 37.5273, "lng": 127.0403 }
                """;
        expectBadRequest(body("[%s,%s]".formatted(namelessStart, DESTINATION)));
    }

    @Test
    @DisplayName("등록: 연습유형이 없거나 4개면 400")
    void 연습유형_개수() throws Exception {
        String route = "[%s,%s]".formatted(START, DESTINATION);
        expectBadRequest(body(route, "[]", "차선이 넓고, 직선 구간이 길어요."));
        expectBadRequest(
                body(
                        route,
                        "[\"STRAIGHT\",\"LANE_CHANGE\",\"INTERSECTION\",\"U_TURN\"]",
                        "차선이 넓고, 직선 구간이 길어요."));
    }

    @Test
    @DisplayName("등록: 연습유형이 중복되면 400")
    void 연습유형_중복() throws Exception {
        expectBadRequest(
                body(
                        "[%s,%s]".formatted(START, DESTINATION),
                        "[\"STRAIGHT\",\"STRAIGHT\"]",
                        "차선이 넓고, 직선 구간이 길어요."));
    }

    @Test
    @DisplayName("등록: 한줄소개가 10자 미만·30자 초과면 400")
    void 한줄소개_길이() throws Exception {
        String route = "[%s,%s]".formatted(START, DESTINATION);
        expectBadRequest(body(route, "[\"STRAIGHT\"]", "짧은설명"));
        expectBadRequest(body(route, "[\"STRAIGHT\"]", "가".repeat(31)));
    }

    @Test
    @DisplayName("등록: 주행거리가 없거나 0 이하면 400")
    void 주행거리() throws Exception {
        authenticate(7L);
        String withoutDistance =
                """
                {
                  "address": "서울특별시 강남구",
                  "waypoints": [%s,%s],
                  "practiceTypes": ["STRAIGHT"],
                  "description": "차선이 넓고, 직선 구간이 길어요."
                }
                """
                        .formatted(START, DESTINATION);

        mockMvc.perform(
                        post("/api/v1/courses")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(withoutDistance))
                .andExpect(status().isBadRequest());

        expectBadRequest(
                """
                {
                  "address": "서울특별시 강남구",
                  "distanceMeters": 0,
                  "waypoints": [%s,%s],
                  "practiceTypes": ["STRAIGHT"],
                  "description": "차선이 넓고, 직선 구간이 길어요."
                }
                """
                        .formatted(START, DESTINATION));
    }

    @Test
    @DisplayName("등록 폼: 카테고리 5개를 order 순으로 반환하고 복합 상황만 전체 버튼이 없다")
    void 등록_폼() throws Exception {
        authenticate(7L);
        when(courseService.getRegistrationForm())
                .thenReturn(new CourseService(null, null, null).getRegistrationForm());

        mockMvc.perform(get("/api/v1/courses/registration-form"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.maxWaypoints").value(3))
                .andExpect(jsonPath("$.data.practiceType.maxSelect").value(3))
                .andExpect(
                        jsonPath("$.data.practiceType.maxSelectExceededMessage")
                                .value("연습유형은 최대 3개까지 선택할 수 있어요."))
                .andExpect(jsonPath("$.data.practiceType.categories.length()").value(5))
                .andExpect(
                        jsonPath("$.data.practiceType.categories[0].code").value("BASIC_DRIVING"))
                .andExpect(jsonPath("$.data.practiceType.categories[0].label").value("기초 주행"))
                .andExpect(
                        jsonPath("$.data.practiceType.categories[0].selectAllEnabled").value(true))
                .andExpect(
                        jsonPath("$.data.practiceType.categories[0].practiceTypes[0].label")
                                .value("직선주행"))
                .andExpect(jsonPath("$.data.practiceType.categories[4].code").value("COMPLEX"))
                .andExpect(
                        jsonPath("$.data.practiceType.categories[4].selectAllEnabled").value(false))
                .andExpect(jsonPath("$.data.inputs.description.minLength").value(10))
                .andExpect(jsonPath("$.data.inputs.description.maxLength").value(30))
                .andExpect(jsonPath("$.data.inputs.caution.maxLength").value(100))
                .andExpect(jsonPath("$.data.inputs.caution.minLength").doesNotExist());
    }
}
