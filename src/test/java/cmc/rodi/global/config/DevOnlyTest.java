package cmc.rodi.global.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** {@link DevOnly} — local·dev에서만 빈이 만들어지고 prod에서는 없다(스펙 023). */
class DevOnlyTest {

    @DevOnly
    @RestController
    static class SampleDevOnlyController {
        @GetMapping("/sample/dev-only")
        String ping() {
            return "ok";
        }
    }

    private final ApplicationContextRunner runner =
            new ApplicationContextRunner().withUserConfiguration(SampleDevOnlyController.class);

    @Test
    @DisplayName("local 프로필에서는 빈이 만들어진다")
    void local_활성() {
        runner.withPropertyValues("spring.profiles.active=local")
                .run(context -> assertThat(context).hasSingleBean(SampleDevOnlyController.class));
    }

    @Test
    @DisplayName("dev 프로필(개발 서버)에서는 빈이 만들어진다")
    void dev_활성() {
        runner.withPropertyValues("spring.profiles.active=dev")
                .run(context -> assertThat(context).hasSingleBean(SampleDevOnlyController.class));
    }

    @Test
    @DisplayName("prod 프로필에서는 빈이 없다")
    void prod_비활성() {
        runner.withPropertyValues("spring.profiles.active=prod")
                .run(context -> assertThat(context).doesNotHaveBean(SampleDevOnlyController.class));
    }
}
