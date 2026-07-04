package cmc.rodi.global.auth.jwt;

import cmc.rodi.global.auth.exception.AuthErrorCode;
import cmc.rodi.global.exception.BusinessException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Component;

/** HS256 대칭키 기반 access token 발급·검증. */
@Component
public class JwtTokenProvider implements TokenProvider {

    private static final String ISSUER = "rodi";

    private final SecretKey key;
    private final long accessTtlSeconds;

    public JwtTokenProvider(JwtProperties properties) {
        this.key = Keys.hmacShaKeyFor(properties.secret().getBytes(StandardCharsets.UTF_8));
        this.accessTtlSeconds = properties.accessTokenTtl().getSeconds();
    }

    @Override
    public String createAccessToken(Long memberId) {
        Instant now = Instant.now();
        return Jwts.builder()
                .issuer(ISSUER)
                .subject(String.valueOf(memberId))
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(accessTtlSeconds)))
                .signWith(key)
                .compact();
    }

    @Override
    public Long getMemberId(String accessToken) {
        try {
            Claims claims =
                    Jwts.parser()
                            .verifyWith(key)
                            .build()
                            .parseSignedClaims(accessToken)
                            .getPayload();
            return Long.valueOf(claims.getSubject());
        } catch (JwtException | IllegalArgumentException e) {
            throw new BusinessException(AuthErrorCode.INVALID_ACCESS_TOKEN);
        }
    }
}
