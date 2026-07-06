package cmc.rodi.global.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** {@code @Scheduled} 활성화. 탈퇴 익명화/해제 등 주기 작업을 구동한다. */
@Configuration
@EnableScheduling
public class SchedulingConfig {}
