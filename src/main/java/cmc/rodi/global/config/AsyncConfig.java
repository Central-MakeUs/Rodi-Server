package cmc.rodi.global.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/** {@code @Async} 활성화. Discord 알림 등 부가 작업을 요청 스레드와 분리한다. */
@Configuration
@EnableAsync
public class AsyncConfig {}
