package cmc.rodi.global.auth.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cmc.rodi.global.auth.dto.TokenResponse;
import cmc.rodi.global.auth.entity.SocialProvider;
import cmc.rodi.global.auth.jwt.JwtAuthenticationFilter;
import cmc.rodi.global.auth.service.AuthService;
import cmc.rodi.global.common.notification.DiscordNotifier;
import cmc.rodi.global.config.SecurityConfig;
import cmc.rodi.global.config.WebConfig;
import cmc.rodi.global.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.ComponentScan.Filter;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

// 컨트롤러 계층만 검증 → 실제 보안·웹 설정은 제외하고 AuthService는 목으로 대체
@WebMvcTest(
        controllers = AuthController.class,
        excludeFilters =
                @Filter(
                        type = FilterType.ASSIGNABLE_TYPE,
                        classes = {
                            SecurityConfig.class,
                            WebConfig.class,
                            JwtAuthenticationFilter.class
                        }))
@AutoConfigureMockMvc(addFilters = false)
@Import({GlobalExceptionHandler.class, DiscordNotifier.class})
class AuthControllerTest {

    @Autowired MockMvc mockMvc;

    @MockitoBean AuthService authService;

    @Test
    @DisplayName("소셜 로그인 성공: 200 + 공통 응답 형식으로 토큰 반환")
    void 소셜_로그인_성공() throws Exception {
        when(authService.login(eq(SocialProvider.KAKAO), eq("kakao-token")))
                .thenReturn(new TokenResponse("access-jwt", "refresh-raw", true));

        mockMvc.perform(
                        post("/api/v1/auth/oauth/kakao")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"credential\":\"kakao-token\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.code").value("COMMON_200"))
                .andExpect(jsonPath("$.data.accessToken").value("access-jwt"))
                .andExpect(jsonPath("$.data.refreshToken").value("refresh-raw"))
                .andExpect(jsonPath("$.data.isNewMember").value(true));
    }

    @Test
    @DisplayName("credential이 비면 400(검증 실패) + 필드 에러")
    void credential_빈값_400() throws Exception {
        mockMvc.perform(
                        post("/api/v1/auth/oauth/kakao")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"credential\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.data.credential").exists());
    }

    @Test
    @DisplayName("미지원 provider면 400(UNSUPPORTED_PROVIDER)")
    void 미지원_provider_400() throws Exception {
        mockMvc.perform(
                        post("/api/v1/auth/oauth/naver")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"credential\":\"whatever\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("AUTH_400_1"));
    }

    @Test
    @DisplayName("토큰 재발급 성공: 200 + 새 토큰 반환")
    void 재발급_성공() throws Exception {
        when(authService.reissue("refresh-raw"))
                .thenReturn(new TokenResponse("new-access", "new-refresh", false));

        mockMvc.perform(
                        post("/api/v1/auth/token/refresh")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"refreshToken\":\"refresh-raw\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").value("new-access"))
                .andExpect(jsonPath("$.data.isNewMember").value(false));
    }

    @Test
    @DisplayName("로그아웃 성공: 200 + AuthService.logout 위임")
    void 로그아웃_성공() throws Exception {
        mockMvc.perform(
                        post("/api/v1/auth/logout")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"refreshToken\":\"refresh-raw\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true));

        verify(authService).logout("refresh-raw");
    }
}
