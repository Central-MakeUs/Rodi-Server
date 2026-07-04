package cmc.rodi.global.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cmc.rodi.domain.member.entity.Member;
import cmc.rodi.global.auth.entity.RefreshToken;
import cmc.rodi.global.auth.exception.AuthErrorCode;
import cmc.rodi.global.auth.jwt.JwtProperties;
import cmc.rodi.global.auth.repository.RefreshTokenRepository;
import cmc.rodi.global.auth.service.RefreshTokenService.Rotation;
import cmc.rodi.global.exception.BusinessException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    private static final Duration REFRESH_TTL = Duration.ofDays(14);

    @Mock RefreshTokenRepository repository;

    RefreshTokenService service;

    @BeforeEach
    void setUp() {
        JwtProperties properties =
                new JwtProperties("test-secret", Duration.ofMinutes(30), REFRESH_TTL);
        service = new RefreshTokenService(repository, properties);
    }

    @Test
    @DisplayName("발급: 원문을 반환하고 DB에는 해시만 저장한다")
    void issue_저장은_해시_반환은_원문() {
        Member member = Member.createBySocial("user@example.com");

        String raw = service.issue(member);

        assertThat(raw).isNotBlank();
        RefreshToken saved = captureSaved();
        assertThat(saved.getMember()).isSameAs(member);
        assertThat(saved.getTokenHash()).isEqualTo(sha256Hex(raw)); // 해시로 저장
        assertThat(saved.getTokenHash()).isNotEqualTo(raw); // 원문 그대로가 아님
        assertThat(saved.getExpiresAt()).isAfter(LocalDateTime.now());
    }

    @Nested
    @DisplayName("회전(rotate)")
    class Rotate {

        @Test
        @DisplayName("정상: 기존 토큰을 폐기·계보 연결하고 새 토큰을 발급한다")
        void 정상_회전() {
            Member member = Member.createBySocial("user@example.com");
            RefreshToken current = refreshToken(member, LocalDateTime.now().plusDays(1));
            when(repository.findByTokenHash(anyString())).thenReturn(Optional.of(current));

            RefreshToken persisted = mock(RefreshToken.class);
            when(persisted.getId()).thenReturn(2L);
            when(repository.save(any(RefreshToken.class))).thenReturn(persisted);

            Rotation rotation = service.rotate("old-raw");

            assertThat(rotation.member()).isSameAs(member);
            assertThat(rotation.rawRefreshToken()).isNotBlank().isNotEqualTo("old-raw");

            // 기존 토큰: 폐기 + 새 토큰 id로 계보 연결 + 사용시각 기록
            assertThat(current.isRevoked()).isTrue();
            assertThat(current.getReplacedByTokenId()).isEqualTo(2L);
            assertThat(current.getLastUsedAt()).isNotNull();

            // 새로 저장된 토큰의 해시 = 반환된 새 원문의 해시
            RefreshToken saved = captureSaved();
            assertThat(saved.getTokenHash()).isEqualTo(sha256Hex(rotation.rawRefreshToken()));
            verify(repository, never()).revokeAllByMember(any(), any());
        }

        @Test
        @DisplayName("재사용 탐지: 폐기된 토큰 재제출 시 회원 전체 세션을 폐기하고 예외")
        void 재사용_탐지() {
            Member member = Member.createBySocial("user@example.com");
            RefreshToken revoked = refreshToken(member, LocalDateTime.now().plusDays(1));
            revoked.revoke(LocalDateTime.now().minusMinutes(1)); // 이미 폐기됨
            when(repository.findByTokenHash(anyString())).thenReturn(Optional.of(revoked));

            assertThatThrownBy(() -> service.rotate("stolen-raw"))
                    .isInstanceOfSatisfying(
                            BusinessException.class,
                            e ->
                                    assertThat(e.getErrorCode())
                                            .isEqualTo(AuthErrorCode.REFRESH_TOKEN_REUSE_DETECTED));

            verify(repository).revokeAllByMember(eq(member), any(LocalDateTime.class));
            verify(repository, never()).save(any());
        }

        @Test
        @DisplayName("만료: 만료된 토큰이면 예외, 세션 폐기는 하지 않는다")
        void 만료() {
            Member member = Member.createBySocial("user@example.com");
            RefreshToken expired = refreshToken(member, LocalDateTime.now().minusDays(1));
            when(repository.findByTokenHash(anyString())).thenReturn(Optional.of(expired));

            assertThatThrownBy(() -> service.rotate("expired-raw"))
                    .isInstanceOfSatisfying(
                            BusinessException.class,
                            e ->
                                    assertThat(e.getErrorCode())
                                            .isEqualTo(AuthErrorCode.EXPIRED_REFRESH_TOKEN));

            verify(repository, never()).save(any());
            verify(repository, never()).revokeAllByMember(any(), any());
        }

        @Test
        @DisplayName("미존재: 없는 토큰이면 INVALID_REFRESH_TOKEN")
        void 미존재() {
            when(repository.findByTokenHash(anyString())).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.rotate("unknown-raw"))
                    .isInstanceOfSatisfying(
                            BusinessException.class,
                            e ->
                                    assertThat(e.getErrorCode())
                                            .isEqualTo(AuthErrorCode.INVALID_REFRESH_TOKEN));
        }
    }

    @Nested
    @DisplayName("폐기(revoke)")
    class Revoke {

        @Test
        @DisplayName("존재하는 토큰이면 폐기 마킹")
        void 존재하면_폐기() {
            Member member = Member.createBySocial("user@example.com");
            RefreshToken token = refreshToken(member, LocalDateTime.now().plusDays(1));
            when(repository.findByTokenHash(anyString())).thenReturn(Optional.of(token));

            service.revoke("raw");

            assertThat(token.isRevoked()).isTrue();
        }

        @Test
        @DisplayName("없는 토큰이면 무시(멱등)")
        void 없으면_무시() {
            when(repository.findByTokenHash(anyString())).thenReturn(Optional.empty());

            assertThatCode(() -> service.revoke("raw")).doesNotThrowAnyException();
        }
    }

    @Test
    @DisplayName("전체 폐기: 회원의 모든 세션을 폐기한다")
    void revokeAll_회원전체() {
        Member member = Member.createBySocial("user@example.com");

        service.revokeAll(member);

        verify(repository).revokeAllByMember(eq(member), any(LocalDateTime.class));
    }

    private RefreshToken refreshToken(Member member, LocalDateTime expiresAt) {
        return RefreshToken.builder()
                .member(member)
                .tokenHash("stored-hash")
                .expiresAt(expiresAt)
                .build();
    }

    private RefreshToken captureSaved() {
        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(repository).save(captor.capture());
        return captor.getValue();
    }

    private static String sha256Hex(String raw) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
