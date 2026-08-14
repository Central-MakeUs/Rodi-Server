package cmc.rodi.domain.member;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import cmc.rodi.domain.member.controller.MemberHardDeleteController;
import cmc.rodi.domain.member.service.MemberHardDeleteService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * 즉시 탈퇴가 <b>운영에 노출되지 않는지</b>. 되돌릴 수 없는 삭제라 프로파일 조건이 이 기능의 유일한 방어선이다 — 조건이 지워지거나 오타가 나도 다른 테스트는 전부
 * 통과하므로 여기서 못 박는다.
 */
class MemberHardDeleteProfileTest {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner()
                    .withBean(
                            MemberHardDeleteService.class,
                            () -> mock(MemberHardDeleteService.class))
                    .withUserConfiguration(MemberHardDeleteController.class);

    @Test
    @DisplayName("운영 프로파일에서는 즉시 탈퇴 컨트롤러가 등록되지 않는다")
    void 운영_제외() {
        contextRunner
                .withPropertyValues("spring.profiles.active=prod")
                .run(
                        context ->
                                assertThat(context)
                                        .doesNotHaveBean(MemberHardDeleteController.class));
    }

    @Test
    @DisplayName("그 외 프로파일에서는 등록된다")
    void 비운영_등록() {
        contextRunner
                .withPropertyValues("spring.profiles.active=local")
                .run(
                        context ->
                                assertThat(context)
                                        .hasSingleBean(MemberHardDeleteController.class));
    }
}
