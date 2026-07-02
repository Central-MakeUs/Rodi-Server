package cmc.rodi.global.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/** JPA Auditing 활성화. {@link cmc.rodi.global.common.entity.BaseEntity}의 created/updated 자동 기록. */
@Configuration
@EnableJpaAuditing
public class JpaConfig {}
