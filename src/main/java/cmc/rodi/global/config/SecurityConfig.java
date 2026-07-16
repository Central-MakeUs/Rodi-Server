package cmc.rodi.global.config;

import cmc.rodi.global.auth.jwt.JwtAuthenticationEntryPoint;
import cmc.rodi.global.auth.jwt.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * 보안 설정. REST/모바일 클라이언트 대상이라 CSRF·폼로그인·세션을 쓰지 않는다(stateless). health·swagger·인증 API만 공개하고 나머지는 JWT
 * 인증이 필요하다. 인증 실패는 EntryPoint가 공통 응답 형식(401)으로 반환한다.
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private static final String[] PUBLIC_ENDPOINTS = {
        "/actuator/health",
        "/swagger-ui/**",
        "/swagger-ui.html",
        "/v3/api-docs", // OpenAPI JSON 루트(Apidog 등에서 스펙 import). PathPattern이 아래 /** 로 안 잡는 정확 경로
        "/v3/api-docs/**", // /v3/api-docs/swagger-config 등 하위
        "/error",
        "/api/v1/auth/**", // 소셜 로그인·토큰 재발급·로그아웃(토큰 없이 접근)
        "/api/v1/places/coordinates" // 지도 마커 좌표(공개). 상세·북마크는 JWT
    };

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http.csrf(csrf -> csrf.disable())
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .sessionManagement(
                        session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(
                        auth ->
                                auth.requestMatchers(PUBLIC_ENDPOINTS)
                                        .permitAll()
                                        .anyRequest()
                                        .authenticated())
                .exceptionHandling(
                        handling -> handling.authenticationEntryPoint(jwtAuthenticationEntryPoint))
                .addFilterBefore(
                        jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }
}
