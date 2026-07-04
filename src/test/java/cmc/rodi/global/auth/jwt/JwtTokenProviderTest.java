package cmc.rodi.global.auth.jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cmc.rodi.global.auth.exception.AuthErrorCode;
import cmc.rodi.global.exception.BusinessException;
import java.time.Duration;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class JwtTokenProviderTest {

    private static final String SECRET = "test-secret-at-least-32-bytes-long-000000";
    private static final String OTHER_SECRET = "another-secret-at-least-32-bytes-long-11";

    private final JwtTokenProvider provider = provider(SECRET, Duration.ofMinutes(30));

    @Test
    @DisplayName("발급한 토큰을 파싱하면 회원 id가 그대로 복원된다")
    void 발급_파싱_라운드트립() {
        String token = provider.createAccessToken(42L);

        Long memberId = provider.getMemberId(token);

        assertThat(memberId).isEqualTo(42L);
    }

    @Test
    @DisplayName("변조된 토큰은 INVALID_ACCESS_TOKEN")
    void 변조_토큰() {
        String token = provider.createAccessToken(1L);
        String tampered = token.substring(0, token.length() - 2) + "xx";

        assertInvalidAccessToken(() -> provider.getMemberId(tampered));
    }

    @Test
    @DisplayName("다른 키로 서명된 토큰은 INVALID_ACCESS_TOKEN")
    void 잘못된_서명() {
        String foreignToken = provider(OTHER_SECRET, Duration.ofMinutes(30)).createAccessToken(1L);

        assertInvalidAccessToken(() -> provider.getMemberId(foreignToken));
    }

    @Test
    @DisplayName("만료된 토큰은 INVALID_ACCESS_TOKEN")
    void 만료_토큰() {
        // TTL을 음수로 줘 이미 만료된 토큰 생성
        String expired = provider(SECRET, Duration.ofSeconds(-10)).createAccessToken(1L);

        assertInvalidAccessToken(() -> provider.getMemberId(expired));
    }

    @Test
    @DisplayName("형식이 깨진 문자열은 INVALID_ACCESS_TOKEN")
    void 잘못된_형식() {
        assertInvalidAccessToken(() -> provider.getMemberId("not-a-jwt"));
    }

    private JwtTokenProvider provider(String secret, Duration accessTtl) {
        return new JwtTokenProvider(new JwtProperties(secret, accessTtl, Duration.ofDays(14)));
    }

    private void assertInvalidAccessToken(ThrowingCallable callable) {
        assertThatThrownBy(callable)
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        e ->
                                assertThat(e.getErrorCode())
                                        .isEqualTo(AuthErrorCode.INVALID_ACCESS_TOKEN));
    }
}
