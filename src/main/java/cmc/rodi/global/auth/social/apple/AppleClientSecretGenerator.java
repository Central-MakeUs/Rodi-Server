package cmc.rodi.global.auth.social.apple;

import io.jsonwebtoken.Jwts;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import org.springframework.stereotype.Component;

/**
 * 애플 토큰 요청에 쓰는 client_secret(JWT)을 .p8 개인키로 ES256 서명해 생성한다. 애플 규격: {@code iss}=Team ID, {@code
 * sub}=client_id(Bundle ID), {@code aud}=애플 issuer, header {@code kid}=Key ID, 만료 ≤6개월. 개인키 파싱은 최초
 * 사용 시점(lazy)에 해 미설정 환경에서도 앱 기동을 막지 않는다.
 */
@Component
public class AppleClientSecretGenerator {

    private static final Duration TTL = Duration.ofMinutes(5);

    private final AppleProperties properties;
    private volatile PrivateKey privateKey;

    public AppleClientSecretGenerator(AppleProperties properties) {
        this.properties = properties;
    }

    public String generate() {
        Instant now = Instant.now();
        return Jwts.builder()
                .header()
                .keyId(properties.keyId())
                .and()
                .issuer(properties.teamId())
                .subject(properties.clientId())
                .audience()
                .add(properties.issuer())
                .and()
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(TTL)))
                .signWith(privateKey(), Jwts.SIG.ES256)
                .compact();
    }

    private PrivateKey privateKey() {
        if (privateKey == null) {
            synchronized (this) {
                if (privateKey == null) {
                    privateKey = parse(properties.privateKey());
                }
            }
        }
        return privateKey;
    }

    /** .p8(PKCS#8) 개인키를 파싱. PEM 헤더/개행이 있어도 처리한다. */
    private PrivateKey parse(String pem) {
        if (pem == null || pem.isBlank()) {
            throw new IllegalStateException("APPLE_PRIVATE_KEY가 설정되지 않았습니다.");
        }
        String base64 =
                pem.replace("-----BEGIN PRIVATE KEY-----", "")
                        .replace("-----END PRIVATE KEY-----", "")
                        .replaceAll("\\s", "");
        try {
            byte[] der = Base64.getDecoder().decode(base64);
            return KeyFactory.getInstance("EC").generatePrivate(new PKCS8EncodedKeySpec(der));
        } catch (Exception e) {
            throw new IllegalStateException("애플 개인키(.p8) 파싱 실패", e);
        }
    }
}
