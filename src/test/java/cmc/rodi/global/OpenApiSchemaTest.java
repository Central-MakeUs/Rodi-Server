package cmc.rodi.global;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cmc.rodi.support.TestcontainersConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 요청 스키마 위생 검사. {@code @AssertTrue} 검증 메서드는 getter처럼 생겨서 그냥 두면 요청 본문 필드로 문서에 노출되고, 클라이언트가 보내야 할 값으로
 * 오해한다(실제로 Swagger 예시에 transitionAllowed 등이 찍혔다).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class OpenApiSchemaTest {

    @Autowired MockMvc mockMvc;

    @Test
    @DisplayName("검증 메서드는 OpenAPI 스키마에 필드로 노출되지 않는다")
    void 검증_메서드_비노출() throws Exception {
        // 문서 생성이 실패해 빈 본문이 와도 doesNotContain은 통과해버리므로 상태부터 확인한다
        String apiDocs =
                mockMvc.perform(get("/v3/api-docs"))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();

        assertThat(apiDocs)
                .doesNotContain("transitionAllowed")
                .doesNotContain("skipDetailConsistent")
                .doesNotContain("detailConsistent")
                .doesNotContain("placeIdConsistent");
    }
}
