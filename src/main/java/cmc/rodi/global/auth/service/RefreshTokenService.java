package cmc.rodi.global.auth.service;

import cmc.rodi.domain.member.entity.Member;
import cmc.rodi.global.auth.entity.RefreshToken;
import cmc.rodi.global.auth.exception.AuthErrorCode;
import cmc.rodi.global.auth.jwt.JwtProperties;
import cmc.rodi.global.auth.repository.RefreshTokenRepository;
import cmc.rodi.global.exception.BusinessException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * refresh token 수명 관리. 원문은 반환만 하고 DB에는 SHA-256 해시만 저장한다. 재발급 시 회전하며, 폐기된 토큰이 다시 제출되면 탈취로 간주해 회원의 모든
 * 세션을 폐기한다(ADR 0009).
 */
@Service
public class RefreshTokenService {

    private static final int TOKEN_BYTES = 32;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final RefreshTokenRepository repository;
    private final Duration refreshTtl;

    public RefreshTokenService(RefreshTokenRepository repository, JwtProperties properties) {
        this.repository = repository;
        this.refreshTtl = properties.refreshTokenTtl();
    }

    /** 새 refresh 발급 → 원문 반환(해시만 저장). */
    @Transactional
    public String issue(Member member) {
        String raw = generateRawToken();
        repository.save(
                RefreshToken.builder()
                        .member(member)
                        .tokenHash(hash(raw))
                        .expiresAt(LocalDateTime.now().plus(refreshTtl))
                        .build());
        return raw;
    }

    /** refresh 회전: 검증 + 재사용 탐지 후 새 refresh 발급. */
    @Transactional
    public Rotation rotate(String rawRefreshToken) {
        RefreshToken token =
                repository
                        .findByTokenHash(hash(rawRefreshToken))
                        .orElseThrow(
                                () -> new BusinessException(AuthErrorCode.INVALID_REFRESH_TOKEN));

        LocalDateTime now = LocalDateTime.now();
        if (token.isRevoked()) {
            // 폐기된 토큰 재제출 = 탈취 의심 → 회원 전체 세션 폐기
            repository.revokeAllByMember(token.getMember(), now);
            throw new BusinessException(AuthErrorCode.REFRESH_TOKEN_REUSE_DETECTED);
        }
        if (token.isExpired(now)) {
            throw new BusinessException(AuthErrorCode.EXPIRED_REFRESH_TOKEN);
        }

        String raw = generateRawToken();
        RefreshToken next =
                repository.save(
                        RefreshToken.builder()
                                .member(token.getMember())
                                .tokenHash(hash(raw))
                                .expiresAt(now.plus(refreshTtl))
                                .build());
        token.rotate(next.getId(), now);
        return new Rotation(token.getMember(), raw);
    }

    /** 단순 폐기(로그아웃). 토큰이 없으면 무시(멱등). */
    @Transactional
    public void revoke(String rawRefreshToken) {
        repository
                .findByTokenHash(hash(rawRefreshToken))
                .ifPresent(token -> token.revoke(LocalDateTime.now()));
    }

    /** 회원의 모든 세션 폐기(탈퇴 등). */
    @Transactional
    public void revokeAll(Member member) {
        repository.revokeAllByMember(member, LocalDateTime.now());
    }

    private String generateRawToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hash(String raw) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    /** 회전 결과: 회원과 새 refresh 원문. */
    public record Rotation(Member member, String rawRefreshToken) {}
}
