package cmc.rodi.global.auth.jwt;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** JWT 서명 시크릿과 토큰 수명. 시크릿은 환경변수로 주입(prod), 커밋 금지. */
@ConfigurationProperties(prefix = "jwt")
public record JwtProperties(String secret, Duration accessTokenTtl, Duration refreshTokenTtl) {}
