package cmc.rodi.global.common.notification;

import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Discord 웹훅 알림. 서버 오류(5xx)는 error 채널, 생명주기(기동/종료)는 health 채널로 보낸다. 웹훅 URL 미설정(로컬 등)이면 전송하지 않고, 전송
 * 실패가 요청·종료 처리에 영향을 주지 않는다. 알림엔 환경 라벨(prod/local)을 붙인다.
 */
@Component
public class DiscordNotifier {

    private static final Logger log = LoggerFactory.getLogger(DiscordNotifier.class);

    private final String errorWebhookUrl;
    private final String healthWebhookUrl;
    private final String environment;
    private final RestClient restClient = RestClient.create();

    public DiscordNotifier(
            @Value("${notification.discord.error-webhook-url:}") String errorWebhookUrl,
            @Value("${notification.discord.health-webhook-url:}") String healthWebhookUrl,
            @Value("${spring.profiles.active:local}") String environment) {
        this.errorWebhookUrl = errorWebhookUrl;
        this.healthWebhookUrl = healthWebhookUrl;
        this.environment = environment;
    }

    /** 5xx 서버 오류 알림(비동기 — 요청 스레드와 분리). */
    @Async
    public void sendServerError(String traceId, Throwable e, String method, String uri) {
        String content =
                """
                🚨 **[Rodi][%s] 서버 오류(5xx)**
                • traceId: `%s`
                • 요청: `%s %s`
                • 예외: `%s: %s`"""
                        .formatted(
                                environment,
                                traceId,
                                method,
                                uri,
                                e.getClass().getName(),
                                e.getMessage());
        send(errorWebhookUrl, content);
    }

    /** 서버 기동 완료 알림(비동기). */
    @Async
    public void sendStartup() {
        send(healthWebhookUrl, "🟢 **[Rodi][%s] 서버 정상 가동**".formatted(environment));
    }

    /** 서버 종료(재배포/재시작) 알림 — 종료 직전이라 동기 전송해야 완료된다. */
    public void sendShutdown() {
        send(healthWebhookUrl, "🟡 **[Rodi][%s] 서버 종료 중 (재배포/재시작)**".formatted(environment));
    }

    private void send(String webhookUrl, String content) {
        if (webhookUrl == null || webhookUrl.isBlank()) {
            return; // 미설정 환경(로컬 등)에서는 전송하지 않음
        }
        try {
            restClient
                    .post()
                    .uri(webhookUrl)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("content", content))
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception ex) {
            log.warn("Discord 알림 전송 실패", ex);
        }
    }
}
