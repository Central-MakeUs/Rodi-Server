package cmc.rodi.domain.member.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cmc.rodi.domain.member.dto.CourseTutorialCompletionResponse;
import cmc.rodi.domain.member.dto.LevelProgressResponse;
import cmc.rodi.domain.member.dto.MemberUpdateRequest;
import cmc.rodi.domain.member.dto.MemberUpdateResponse;
import cmc.rodi.domain.member.dto.MyPageResponse;
import cmc.rodi.domain.member.dto.NicknameAvailabilityResponse;
import cmc.rodi.domain.member.dto.OnboardingFormResponse;
import cmc.rodi.domain.member.dto.OnboardingRequest;
import cmc.rodi.domain.member.entity.Level;
import cmc.rodi.domain.member.entity.PracticeType;
import cmc.rodi.domain.member.exception.MemberErrorCode;
import cmc.rodi.domain.member.service.MemberBlockService;
import cmc.rodi.domain.member.service.MemberCourseTutorialService;
import cmc.rodi.domain.member.service.MemberProfileService;
import cmc.rodi.domain.member.service.MemberSortService;
import cmc.rodi.domain.member.service.MemberWithdrawalService;
import cmc.rodi.domain.member.service.NicknameService;
import cmc.rodi.domain.member.service.OnboardingService;
import cmc.rodi.global.auth.jwt.JwtAuthenticationFilter;
import cmc.rodi.global.auth.resolver.CurrentMemberArgumentResolver;
import cmc.rodi.global.common.notification.DiscordNotifier;
import cmc.rodi.global.config.SecurityConfig;
import cmc.rodi.global.config.WebConfig;
import cmc.rodi.global.exception.GlobalExceptionHandler;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
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
        controllers = MemberController.class,
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
class MemberControllerTest {

    @Autowired MockMvc mockMvc;

    @MockitoBean MemberWithdrawalService memberWithdrawalService;
    @MockitoBean OnboardingService onboardingService;
    @MockitoBean MemberProfileService memberProfileService;
    @MockitoBean MemberSortService memberSortService;
    @MockitoBean MemberBlockService memberBlockService;
    @MockitoBean MemberCourseTutorialService memberCourseTutorialService;
    @MockitoBean NicknameService nicknameService;

    private static final String ONBOARDING_BODY =
            """
            {
              "drivingPeriod": "YEARS_3_9",
              "recentFrequency": "MONTHLY_1_2",
              "roadExperiences": ["SOLO"],
              "soloDrivingRange": "HIGHWAY_LONG",
              "soloParkingLevel": "MOSTLY_POSSIBLE",
              "level": "NAVIGATOR",
              "practiceTypes": ["LANE_CHANGE", "ROUNDABOUT", "NARROW_ROAD"],
              "carType": "MIDSIZE",
              "drivingGoal": "강남 운전 자신있게"
            }
            """;

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
    @DisplayName("탈퇴 요청: 200 + @CurrentMember의 회원 id로 서비스 위임")
    void 탈퇴_요청() throws Exception {
        authenticate(7L);

        mockMvc.perform(delete("/api/v1/members/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true));

        verify(memberWithdrawalService).withdraw(7L);
    }

    @Test
    @DisplayName("닉네임 확인: 사용 가능하면 200 + available=true, 사유는 null")
    void 닉네임_확인_가능() throws Exception {
        authenticate(7L);
        when(nicknameService.checkAvailability(7L, "초보_탈출"))
                .thenReturn(NicknameAvailabilityResponse.available("초보_탈출"));

        mockMvc.perform(get("/api/v1/members/nickname/availability").param("nickname", "초보_탈출"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nickname").value("초보_탈출"))
                .andExpect(jsonPath("$.data.available").value(true))
                .andExpect(jsonPath("$.data.reason").doesNotExist())
                .andExpect(jsonPath("$.data.message").doesNotExist());
    }

    @Test
    @DisplayName("닉네임 확인: 사용 불가도 200이고 사유 코드·문구를 준다")
    void 닉네임_확인_불가() throws Exception {
        authenticate(7L);
        when(nicknameService.checkAvailability(7L, "차근차근_토끼"))
                .thenReturn(
                        NicknameAvailabilityResponse.unavailable(
                                "차근차근_토끼", MemberErrorCode.NICKNAME_DUPLICATED));

        mockMvc.perform(get("/api/v1/members/nickname/availability").param("nickname", "차근차근_토끼"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.data.available").value(false))
                .andExpect(jsonPath("$.data.reason").value("MEMBER_409_3"))
                .andExpect(jsonPath("$.data.message").value("이미 사용 중인 닉네임이에요."));
    }

    @Test
    @DisplayName("닉네임 확인: nickname 파라미터가 없으면 400 COMMON_400")
    void 닉네임_확인_파라미터_누락() throws Exception {
        authenticate(7L);

        mockMvc.perform(get("/api/v1/members/nickname/availability"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_400"));
    }

    @Test
    @DisplayName("마이페이지 조회: 200 + 프로필 요약 반환")
    void 마이페이지_조회() throws Exception {
        authenticate(7L);
        when(memberProfileService.getMyPage(7L))
                .thenReturn(
                        new MyPageResponse(
                                "성난_초보",
                                LocalDateTime.of(2026, 11, 21, 15, 0),
                                Level.ROOKIE,
                                List.of("U_TURN", "INTERSECTION", "PARKING"),
                                "골목길에 익숙해지기",
                                3,
                                new LevelProgressResponse(100.0, 50.0, 150.0, 50)));

        mockMvc.perform(get("/api/v1/members/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nickname").value("성난_초보"))
                .andExpect(jsonPath("$.data.level").value("ROOKIE"))
                .andExpect(jsonPath("$.data.recommendationTags[0]").value("U_TURN"))
                .andExpect(jsonPath("$.data.drivingGoal").value("골목길에 익숙해지기"))
                .andExpect(jsonPath("$.data.savedPlaceCount").value(3))
                .andExpect(jsonPath("$.data.levelProgress.totalDistanceKm").value(100.0))
                .andExpect(jsonPath("$.data.levelProgress.currentLevelStartKm").value(50.0))
                .andExpect(jsonPath("$.data.levelProgress.nextLevelKm").value(150.0))
                .andExpect(jsonPath("$.data.levelProgress.progressPercent").value(50));
    }

    @Test
    @DisplayName("마이페이지 조회: Navigator는 nextLevelKm 키가 빠지고 진행률 100")
    void 마이페이지_최상위_레벨() throws Exception {
        authenticate(7L);
        when(memberProfileService.getMyPage(7L))
                .thenReturn(
                        new MyPageResponse(
                                "노련한 여우",
                                null,
                                Level.NAVIGATOR,
                                List.of("U_TURN"),
                                null,
                                0,
                                new LevelProgressResponse(812.4, 600.0, null, 100)));

        mockMvc.perform(get("/api/v1/members/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.levelProgress.totalDistanceKm").value(812.4))
                .andExpect(jsonPath("$.data.levelProgress.currentLevelStartKm").value(600.0))
                .andExpect(jsonPath("$.data.levelProgress.nextLevelKm").doesNotExist()) // 목표 없음
                .andExpect(jsonPath("$.data.levelProgress.progressPercent").value(100));
    }

    @Test
    @DisplayName("회원 수정: 200 + @CurrentMember의 회원 id로 서비스 위임")
    void 회원_수정() throws Exception {
        authenticate(7L);
        when(memberProfileService.update(eq(7L), any(MemberUpdateRequest.class)))
                .thenReturn(
                        new MemberUpdateResponse("성난_초보", LocalDateTime.of(2026, 11, 21, 15, 0)));

        mockMvc.perform(
                        patch("/api/v1/members/me")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"drivingGoal\":\"골목길에 익숙해지기\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.data.nickname").value("성난_초보"))
                .andExpect(jsonPath("$.data.nicknameChangeableAt").value("2026-11-21T15:00:00"));

        verify(memberProfileService).update(eq(7L), any(MemberUpdateRequest.class));
    }

    @Test
    @DisplayName("회원 수정: 운전목표 30자 초과 시 400")
    void 회원_수정_길이초과_400() throws Exception {
        authenticate(7L);
        String tooLong = "가".repeat(31);

        mockMvc.perform(
                        patch("/api/v1/members/me")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"drivingGoal\":\"" + tooLong + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.isSuccess").value(false));
    }

    @Test
    @DisplayName("온보딩 폼: 200 + 2단계(2단계만 건너뛰기), 흐름 표시는 true일 때만 내려간다")
    void 온보딩_폼() throws Exception {
        authenticate(7L);
        when(onboardingService.getForm()).thenReturn(OnboardingFormResponse.of());

        mockMvc.perform(get("/api/v1/members/me/onboarding-form"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.steps.length()").value(2))
                .andExpect(jsonPath("$.data.steps[0].skippable").value(false))
                .andExpect(jsonPath("$.data.steps[1].skippable").value(true))
                .andExpect(jsonPath("$.data.steps[0].questions[0].code").value("drivingPeriod"))
                .andExpect(jsonPath("$.data.steps[0].questions[0].type").value("SINGLE_SELECT"))
                .andExpect(jsonPath("$.data.steps[0].questions[0].required").value(true))
                .andExpect(jsonPath("$.data.steps[0].questions[0].options[5].label").value("3~9년"))
                .andExpect(
                        jsonPath("$.data.steps[0].questions[0].options[5].skipsFollowUps")
                                .value(true))
                .andExpect(
                        jsonPath("$.data.steps[0].questions[0].options[0].skipsFollowUps")
                                .doesNotExist())
                .andExpect(
                        jsonPath("$.data.steps[0].questions[2].options[0].exclusive").value(true))
                .andExpect(
                        jsonPath("$.data.steps[0].questions[2].options[3].exclusive")
                                .doesNotExist())
                .andExpect(jsonPath("$.data.steps[0].questions[3].showWhen.anyOf[0]").value("SOLO"))
                .andExpect(jsonPath("$.data.steps[1].questions[0].maxSelect").value(3))
                .andExpect(jsonPath("$.data.steps[1].questions[2].type").value("TEXT"))
                .andExpect(jsonPath("$.data.steps[1].questions[2].maxLength").value(30))
                .andExpect(jsonPath("$.data.steps[1].questions[2].options").doesNotExist());
    }

    @Test
    @DisplayName("온보딩 제출: 200 + @CurrentMember의 회원 id로 서비스 위임")
    void 온보딩_제출() throws Exception {
        authenticate(7L);

        mockMvc.perform(
                        post("/api/v1/members/me/onboarding")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(ONBOARDING_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true));

        verify(onboardingService).submit(eq(7L), any(OnboardingRequest.class));
    }

    @Test
    @DisplayName("온보딩 필수 항목 누락: 400 검증 실패")
    void 온보딩_필수_누락_400() throws Exception {
        authenticate(7L);

        mockMvc.perform(
                        post("/api/v1/members/me/onboarding")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.isSuccess").value(false));
    }

    private static String onboardingBodyWithRoadExperiences(String roadExperiences) {
        return """
                {"drivingPeriod": "MONTHS_1_2", "level": "SEED", "roadExperiences": %s}
                """
                .formatted(roadExperiences);
    }

    @Test
    @DisplayName("온보딩 '없음' 배타: NONE과 다른 경험을 함께 보내면 400, 저장 위임 없음")
    void 온보딩_없음_복수선택_400() throws Exception {
        authenticate(7L);

        mockMvc.perform(
                        post("/api/v1/members/me/onboarding")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(onboardingBodyWithRoadExperiences("[\"NONE\", \"SOLO\"]")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.isSuccess").value(false));

        verify(onboardingService, never()).submit(any(), any());
    }

    @Test
    @DisplayName("온보딩 도로 경험 중복: 같은 값을 두 번 보내면 400")
    void 온보딩_도로경험_중복_400() throws Exception {
        authenticate(7L);

        mockMvc.perform(
                        post("/api/v1/members/me/onboarding")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(onboardingBodyWithRoadExperiences("[\"SOLO\", \"SOLO\"]")))
                .andExpect(status().isBadRequest());

        verify(onboardingService, never()).submit(any(), any());
    }

    @Test
    @DisplayName("온보딩 도로 경험 null 원소: 400")
    void 온보딩_도로경험_null원소_400() throws Exception {
        authenticate(7L);

        mockMvc.perform(
                        post("/api/v1/members/me/onboarding")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(onboardingBodyWithRoadExperiences("[null]")))
                .andExpect(status().isBadRequest());

        verify(onboardingService, never()).submit(any(), any());
    }

    @Test
    @DisplayName("온보딩 도로 경험: NONE 단독·복수 경험·빈 배열은 200")
    void 온보딩_도로경험_정상_200() throws Exception {
        authenticate(7L);

        for (String roadExperiences :
                List.of("[\"NONE\"]", "[\"ACCOMPANIED\", \"SOLO\"]", "[]", "null")) {
            mockMvc.perform(
                            post("/api/v1/members/me/onboarding")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(onboardingBodyWithRoadExperiences(roadExperiences)))
                    .andExpect(status().isOk());
        }

        verify(onboardingService, times(4)).submit(eq(7L), any(OnboardingRequest.class));
    }

    @Test
    @DisplayName("정렬 기준 저장: 200 + @CurrentMember의 회원 id로 연습유형 리스트 위임")
    void 정렬_기준_저장() throws Exception {
        authenticate(7L);

        mockMvc.perform(
                        put("/api/v1/members/me/sort-tags")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"sortTags\":[\"U_TURN\",\"INTERSECTION\",\"PARKING\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true));

        verify(memberSortService)
                .updateSortTags(
                        7L,
                        List.of(
                                PracticeType.U_TURN,
                                PracticeType.INTERSECTION,
                                PracticeType.PARKING));
    }

    @Test
    @DisplayName("정렬 기준 저장: 빈 배열도 200(해제)")
    void 정렬_기준_저장_빈배열() throws Exception {
        authenticate(7L);

        mockMvc.perform(
                        put("/api/v1/members/me/sort-tags")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"sortTags\":[]}"))
                .andExpect(status().isOk());

        verify(memberSortService).updateSortTags(7L, List.of());
    }

    @Test
    @DisplayName("정렬 기준 저장: 무효 enum 값·sortTags 누락은 400이고 저장하지 않는다")
    void 정렬_기준_저장_검증_400() throws Exception {
        authenticate(7L);

        for (String body :
                List.of("{\"sortTags\":[\"NOT_A_TYPE\"]}", "{}", "{\"sortTags\":[null]}")) {
            mockMvc.perform(
                            put("/api/v1/members/me/sort-tags")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.isSuccess").value(false));
        }

        verify(memberSortService, never()).updateSortTags(any(), any());
    }

    @Test
    @DisplayName("옛 경로 filter-tags: 호출마다 deprecated 로그를 남긴다")
    @ExtendWith(OutputCaptureExtension.class)
    void 옛_경로_로그(CapturedOutput output) throws Exception {
        authenticate(7L);

        mockMvc.perform(
                        put("/api/v1/members/me/filter-tags")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"filterTags\":[\"U_TURN\"]}"))
                .andExpect(status().isOk());

        assertThat(output).contains("deprecated PUT /members/me/filter-tags 호출 memberId=7");
    }

    @Test
    @DisplayName("옛 경로 filter-tags: 200 + sort_tags 저장으로 위임")
    void 필터_저장() throws Exception {
        authenticate(7L);

        mockMvc.perform(
                        put("/api/v1/members/me/filter-tags")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"filterTags\":[\"U_TURN\",\"INTERSECTION\",\"PARKING\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true));

        verify(memberSortService)
                .updateSortTags(
                        7L,
                        List.of(
                                PracticeType.U_TURN,
                                PracticeType.INTERSECTION,
                                PracticeType.PARKING));
    }

    @Test
    @DisplayName("옛 경로 filter-tags: 빈 배열도 200(해제)")
    void 필터_저장_빈배열() throws Exception {
        authenticate(7L);

        mockMvc.perform(
                        put("/api/v1/members/me/filter-tags")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"filterTags\":[]}"))
                .andExpect(status().isOk());

        verify(memberSortService).updateSortTags(7L, List.of());
    }

    @Test
    @DisplayName("옛 경로 filter-tags: 무효 enum 값은 400")
    void 필터_저장_무효값_400() throws Exception {
        authenticate(7L);

        mockMvc.perform(
                        put("/api/v1/members/me/filter-tags")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"filterTags\":[\"NOT_A_TYPE\"]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.isSuccess").value(false));
    }

    @Test
    @DisplayName("옛 경로 filter-tags: filterTags 누락(null)은 400")
    void 필터_저장_누락_400() throws Exception {
        authenticate(7L);

        mockMvc.perform(
                        put("/api/v1/members/me/filter-tags")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.isSuccess").value(false));
    }

    @Test
    @DisplayName("옛 경로 filter-tags: 리스트에 null 요소가 있으면 400(500 아님)")
    void 필터_저장_null요소_400() throws Exception {
        authenticate(7L);

        mockMvc.perform(
                        put("/api/v1/members/me/filter-tags")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"filterTags\":[null]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.isSuccess").value(false));
    }

    @Test
    @DisplayName("코스 등록 튜토리얼 완료: 200 + @CurrentMember의 회원 id로 서비스 위임")
    void 코스등록_튜토리얼_완료() throws Exception {
        authenticate(7L);
        LocalDateTime completedAt = LocalDateTime.of(2026, 8, 14, 18, 40);
        when(memberCourseTutorialService.complete(7L))
                .thenReturn(new CourseTutorialCompletionResponse(completedAt));

        mockMvc.perform(patch("/api/v1/members/me/course-tutorial"))
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.data.courseTutorialCompletedAt").value("2026-08-14T18:40:00"));

        verify(memberCourseTutorialService).complete(7L);
    }

    @Test
    @DisplayName("차단·해제: 200 + (현재 회원 → 경로의 회원) 순서로 서비스에 위임")
    void 차단_해제() throws Exception {
        authenticate(7L);

        mockMvc.perform(post("/api/v1/members/9/block"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").doesNotExist());
        verify(memberBlockService).block(7L, 9L);

        mockMvc.perform(delete("/api/v1/members/9/block")).andExpect(status().isOk());
        verify(memberBlockService).unblock(7L, 9L);
    }
}
