package cmc.rodi.global.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cmc.rodi.global.auth.jwt.TokenProvider;
import cmc.rodi.global.auth.resolver.CurrentMember;
import cmc.rodi.global.common.response.ApiResponse;
import cmc.rodi.support.TestcontainersConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** 실제 보안 필터 체인을 통과하는 인증 통합 테스트. 토큰 없는 접근은 401, 유효 토큰은 회원 id 주입까지 확인한다. */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, AuthIntegrationTest.ProtectedController.class})
class AuthIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired TokenProvider tokenProvider;

    @Test
    @DisplayName("토큰 없이 보호 API 접근 → 401 공통 응답 형식")
    void 토큰_없음_401() throws Exception {
        mockMvc.perform(get("/api/v1/test/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("AUTH_401_6"));
    }

    @Test
    @DisplayName("잘못된 토큰으로 접근 → 401")
    void 잘못된_토큰_401() throws Exception {
        mockMvc.perform(
                        get("/api/v1/test/me")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.isSuccess").value(false));
    }

    @Test
    @DisplayName("유효 토큰으로 접근 → 200 + @CurrentMember로 회원 id 주입")
    void 유효_토큰_200() throws Exception {
        String accessToken = tokenProvider.createAccessToken(7L);

        mockMvc.perform(
                        get("/api/v1/test/me")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.data").value(7));
    }

    /** 인증 필요한 보호 엔드포인트(테스트 전용). */
    @RestController
    static class ProtectedController {

        @GetMapping("/api/v1/test/me")
        ApiResponse<Long> me(@CurrentMember Long memberId) {
            return ApiResponse.success(memberId);
        }
    }
}
