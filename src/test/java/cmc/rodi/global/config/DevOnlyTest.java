package cmc.rodi.global.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

/** {@link DevOnly} — local·dev에서는 엔드포인트가 응답하고 prod에서는 없다(404)(스펙 023). */
class DevOnlyTest {

    private static final String PATH = "/sample/dev-only";

    @DevOnly
    @RestController
    static class SampleDevOnlyController {
        @GetMapping(PATH)
        String ping() {
            return "ok";
        }
    }

    @Configuration
    @EnableWebMvc
    static class MvcConfig {}

    // 보안·DB 없이 MVC 디스패치만 띄워, 프로필에 따라 핸들러가 등록되는지를 요청으로 확인한다
    private final WebApplicationContextRunner runner =
            new WebApplicationContextRunner()
                    .withUserConfiguration(MvcConfig.class, SampleDevOnlyController.class);

    private void assertResponds(String profile) {
        runner.withPropertyValues("spring.profiles.active=" + profile)
                .run(
                        context -> {
                            MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
                            mockMvc.perform(get(PATH))
                                    .andExpect(status().isOk())
                                    .andExpect(content().string("ok"));
                        });
    }

    @Test
    @DisplayName("local 프로필에서는 엔드포인트가 응답한다")
    void local_응답() {
        assertResponds("local");
    }

    @Test
    @DisplayName("dev 프로필(개발 서버)에서는 엔드포인트가 응답한다")
    void dev_응답() {
        assertResponds("dev");
    }

    @Test
    @DisplayName("prod 프로필에서는 엔드포인트가 없어 404")
    void prod_404() {
        runner.withPropertyValues("spring.profiles.active=prod")
                .run(
                        context -> {
                            MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
                            mockMvc.perform(get(PATH)).andExpect(status().isNotFound());
                        });
    }
}
