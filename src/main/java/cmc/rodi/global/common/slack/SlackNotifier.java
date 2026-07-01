package cmc.rodi.global.common.slack;

import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * 서버 오류(5xx)를 Slack Incoming Webhook으로 알린다.
 * - {@code slack.webhook-url} 미설정(로컬 등)이면 전송하지 않는다.
 * - 비동기(@Async) + 실패 안전: Slack 전송 실패가 요청 처리에 영향을 주지 않는다.
 */
@Component
public class SlackNotifier {

    private static final Logger log = LoggerFactory.getLogger(SlackNotifier.class);

    private final String webhookUrl;
    private final RestClient restClient = RestClient.create();

    public SlackNotifier(@Value("${slack.webhook-url:}") String webhookUrl) {
        this.webhookUrl = webhookUrl;
    }

    @Async
    public void sendServerError(String traceId, Throwable e, String method, String uri) {
        if (webhookUrl == null || webhookUrl.isBlank()) {
            return; // 미설정 환경(로컬 등)에서는 전송하지 않음
        }
        try {
            String text = """
                    :rotating_light: *[Rodi] 서버 오류*
                    • traceId: `%s`
                    • 요청: %s %s
                    • 예외: %s: %s""".formatted(
                    traceId, method, uri, e.getClass().getName(), e.getMessage());

            restClient.post()
                    .uri(webhookUrl)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("text", text))
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception ex) {
            log.warn("Slack 알림 전송 실패", ex);
        }
    }
}
