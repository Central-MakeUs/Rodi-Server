package cmc.rodi.domain.place.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cmc.rodi.domain.place.dto.CourseApprovalRequest;
import cmc.rodi.domain.place.dto.CourseApprovalResponse;
import cmc.rodi.domain.place.entity.ApprovalStatus;
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

/**
 * 관리자 코스 승인 컨트롤러(스펙 016).
 *
 * <p>현재는 권한 체계가 없어 로그인 사용자 전체가 호출 가능하다. 이 테스트도 인증 컨텍스트만 세팅하고 관리자 권한은 세팅하지 않는다.
 */
@WebMvcTest(
        controllers = AdminCourseController.class,
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
class AdminCourseControllerTest {

    @Autowired MockMvc mockMvc;

    @MockitoBean CourseService courseService;

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
    @DisplayName("승인 상태 변경: 로그인 사용자면 권한 검사 없이 서비스로 위임한다(임시 정책)")
    void 승인_상태_변경() throws Exception {
        authenticate(77L);
        when(courseService.changeApprovalStatus(
                        eq(101L), eq(new CourseApprovalRequest(ApprovalStatus.APPROVED))))
                .thenReturn(new CourseApprovalResponse(101L, ApprovalStatus.APPROVED, null));

        mockMvc.perform(
                        patch("/api/v1/admin/courses/{courseId}/approval", 101L)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"approvalStatus\":\"APPROVED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.courseId").value(101))
                .andExpect(jsonPath("$.data.approvalStatus").value("APPROVED"));

        verify(courseService)
                .changeApprovalStatus(
                        eq(101L), eq(new CourseApprovalRequest(ApprovalStatus.APPROVED)));
    }

    @Test
    @DisplayName("승인 상태 변경: approvalStatus 누락은 400")
    void 상태_누락_400() throws Exception {
        authenticate(77L);

        mockMvc.perform(
                        patch("/api/v1/admin/courses/{courseId}/approval", 101L)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("승인 상태 변경: 정의되지 않은 상태 값은 400")
    void 정의되지_않은_상태_400() throws Exception {
        authenticate(77L);

        mockMvc.perform(
                        patch("/api/v1/admin/courses/{courseId}/approval", 101L)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"approvalStatus\":\"UNKNOWN\"}"))
                .andExpect(status().isBadRequest());
    }
}
