package cmc.rodi.dev;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/** 로컬 전용 {@code /dev/**}(튜닝 페이지)를 인증 없이 연다. 운영 보안 설정({@code SecurityConfig})은 건드리지 않는다. */
@Profile("local")
@Configuration
public class DevSecurityConfig {

    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public SecurityFilterChain devSecurityFilterChain(HttpSecurity http) throws Exception {
        return http.securityMatcher("/dev/**")
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .build();
    }
}
