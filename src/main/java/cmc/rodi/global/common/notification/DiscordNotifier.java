package cmc.rodi.global.common.notification;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Discord 웹훅 알림. 서버 오류(5xx)는 error 채널, 생명주기(기동/종료)는 health 채널로 보낸다. 웹훅 URL 미설정(로컬 등)이면 전송하지 않고, 전송
 * 실패가 요청·종료 처리에 영향을 주지 않는다. 알림엔 환경 라벨(prod/local)을 붙이고, 기동 알림엔 CD가 주입한 배포 메타데이터(브랜치·업데이트 커밋·PR 링크)를
 * 함께 싣는다.
 */
@Component
public class DiscordNotifier {

    private static final Logger log = LoggerFactory.getLogger(DiscordNotifier.class);
    private static final Pattern PR_NUMBER = Pattern.compile("#(\\d+)");

    private final String errorWebhookUrl;
    private final String healthWebhookUrl;
    private final String environment;
    private final String deployBranch;
    private final String deployCommit;
    private final String deployMessage;
    private final String deployRepo;
    private final RestClient restClient = RestClient.create();

    public DiscordNotifier(
            @Value("${notification.discord.error-webhook-url:}") String errorWebhookUrl,
            @Value("${notification.discord.health-webhook-url:}") String healthWebhookUrl,
            @Value("${spring.profiles.active:local}") String environment,
            @Value("${deploy.branch:}") String deployBranch,
            @Value("${deploy.commit:}") String deployCommit,
            @Value("${deploy.message:}") String deployMessage,
            @Value("${deploy.repo:}") String deployRepo) {
        this.errorWebhookUrl = errorWebhookUrl;
        this.healthWebhookUrl = healthWebhookUrl;
        this.environment = environment;
        this.deployBranch = deployBranch;
        this.deployCommit = deployCommit;
        this.deployMessage = deployMessage;
        this.deployRepo = deployRepo;
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

    /** 서버 기동 완료 알림(비동기). 배포 메타데이터(브랜치·업데이트·커밋)가 있으면 함께 싣는다. */
    @Async
    public void sendStartup() {
        StringBuilder content =
                new StringBuilder("🟢 **[Rodi][%s] 서버 정상 가동**".formatted(environment));
        if (hasText(deployBranch)) {
            content.append("\n• 브랜치: `").append(deployBranch).append('`');
        }
        if (hasText(deployMessage)) {
            content.append("\n• 업데이트: ").append(subject());
            String prLink = prLink();
            if (prLink != null) {
                content.append("\n• PR: ").append(prLink);
            }
        }
        if (hasText(deployCommit)) {
            content.append("\n• 커밋: `").append(shortCommit()).append('`');
        }
        send(healthWebhookUrl, content.toString());
    }

    /** 서버 종료(재배포/재시작) 알림 — 종료 직전이라 동기 전송해야 완료된다. */
    public void sendShutdown() {
        send(healthWebhookUrl, "🟡 **[Rodi][%s] 서버 종료 중 (재배포/재시작)**".formatted(environment));
    }

    /** 배포 커밋 메시지의 첫 줄(제목). 스쿼시 머지면 끝에 (#PR번호)가 붙는다. */
    private String subject() {
        return deployMessage.lines().findFirst().orElse(deployMessage).strip();
    }

    private String shortCommit() {
        return deployCommit.length() > 7 ? deployCommit.substring(0, 7) : deployCommit;
    }

    /** 커밋 제목의 (#번호)와 레포로 PR 링크를 만든다. 둘 중 하나라도 없으면 null. */
    private String prLink() {
        if (!hasText(deployRepo)) {
            return null;
        }
        Matcher matcher = PR_NUMBER.matcher(subject());
        return matcher.find()
                ? "https://github.com/%s/pull/%s".formatted(deployRepo, matcher.group(1))
                : null;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
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
