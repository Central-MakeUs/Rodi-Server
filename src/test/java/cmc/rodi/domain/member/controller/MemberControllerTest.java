package cmc.rodi.domain.member.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cmc.rodi.domain.member.dto.MemberUpdateRequest;
import cmc.rodi.domain.member.dto.MyPageResponse;
import cmc.rodi.domain.member.dto.OnboardingRequest;
import cmc.rodi.domain.member.entity.Level;
import cmc.rodi.domain.member.service.MemberProfileService;
import cmc.rodi.domain.member.service.MemberWithdrawalService;
import cmc.rodi.domain.member.service.OnboardingService;
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

    private static final String ONBOARDING_BODY =
            """
            {
              "drivingPeriod": "YEARS_2_10",
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
    @DisplayName("마이페이지 조회: 200 + 프로필 요약 반환")
    void 마이페이지_조회() throws Exception {
        authenticate(7L);
        when(memberProfileService.getMyPage(7L))
                .thenReturn(
                        new MyPageResponse(
                                "성난 초보",
                                Level.ROOKIE,
                                List.of("U_TURN", "INTERSECTION", "PARKING"),
                                "골목길에 익숙해지기",
                                3));

        mockMvc.perform(get("/api/v1/members/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nickname").value("성난 초보"))
                .andExpect(jsonPath("$.data.level").value("ROOKIE"))
                .andExpect(jsonPath("$.data.recommendationTags[0]").value("U_TURN"))
                .andExpect(jsonPath("$.data.drivingGoal").value("골목길에 익숙해지기"))
                .andExpect(jsonPath("$.data.savedPlaceCount").value(3));
    }

    @Test
    @DisplayName("회원 수정: 200 + @CurrentMember의 회원 id로 서비스 위임")
    void 회원_수정() throws Exception {
        authenticate(7L);

        mockMvc.perform(
                        patch("/api/v1/members/me")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"drivingGoal\":\"골목길에 익숙해지기\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true));

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
}
