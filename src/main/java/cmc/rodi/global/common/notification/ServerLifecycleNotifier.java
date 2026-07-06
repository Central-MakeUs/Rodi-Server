package cmc.rodi.global.common.notification;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 서버 생명주기를 Discord health 채널로 알린다. 기동 완료 시 🟢, 정상 종료(SIGTERM=재배포/재시작) 시 🟡. 하드 크래시(OOM·SIGKILL)에선
 * ContextClosedEvent가 안 뜨므로 외부 모니터링(UptimeRobot 등)으로 보완한다.
 */
@Component
public class ServerLifecycleNotifier {

    private final DiscordNotifier discordNotifier;

    public ServerLifecycleNotifier(DiscordNotifier discordNotifier) {
        this.discordNotifier = discordNotifier;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        discordNotifier.sendStartup();
    }

    @EventListener(ContextClosedEvent.class)
    public void onClose() {
        discordNotifier.sendShutdown();
    }
}
