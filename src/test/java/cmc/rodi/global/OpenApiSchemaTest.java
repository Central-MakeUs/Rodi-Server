package cmc.rodi.global;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cmc.rodi.support.TestcontainersConfiguration;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
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
    @Autowired ObjectMapper objectMapper;

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

    @Test
    @DisplayName("@CurrentMember는 요청 파라미터로 노출되지 않는다")
    void 현재회원_비노출() throws Exception {
        JsonNode apiDocs = objectMapper.readTree(fetchApiDocs());

        // 토큰에서 꺼내는 값이라 클라이언트가 보낼 것이 아닌데, @Parameter(hidden=true)를 빠뜨리면
        // springdoc이 required 쿼리 파라미터로 그려 Swagger에서 호출 자체가 막힌다.
        // 경로 변수 memberId(차단 대상 등)는 진짜 요청값이라 대상이 아니다 — in=query만 본다.
        List<String> exposed = new ArrayList<>();
        apiDocs.path("paths")
                .properties()
                .forEach(
                        path ->
                                path.getValue()
                                        .properties()
                                        .forEach(
                                                operation -> {
                                                    for (JsonNode parameter :
                                                            operation
                                                                    .getValue()
                                                                    .path("parameters")) {
                                                        if ("memberId"
                                                                        .equals(
                                                                                parameter
                                                                                        .path(
                                                                                                "name")
                                                                                        .asText())
                                                                && "query"
                                                                        .equals(
                                                                                parameter
                                                                                        .path("in")
                                                                                        .asText())) {
                                                            exposed.add(
                                                                    operation.getKey().toUpperCase()
                                                                            + " "
                                                                            + path.getKey());
                                                        }
                                                    }
                                                }));

        assertThat(exposed).as("@Parameter(hidden = true)가 빠진 엔드포인트").isEmpty();
    }

    @Test
    @DisplayName("폼 엔드포인트는 각자의 응답 예시를 갖는다")
    void 폼_예시_분리() throws Exception {
        JsonNode apiDocs = objectMapper.readTree(fetchApiDocs());

        String skipReasonExample = responseExample(apiDocs, "/api/v1/practices/skip-reason-form");
        assertThat(skipReasonExample)
                .contains("WHY_NOT_PRACTICED")
                .contains("TOO_FAR")
                .doesNotContain("REVIEW_REPORT_REASON")
                .doesNotContain("SPAM");

        String reportExample = responseExample(apiDocs, "/api/v1/reviews/report-form");
        assertThat(reportExample)
                .contains("REVIEW_REPORT_REASON")
                .contains("SPAM")
                .doesNotContain("WHY_NOT_PRACTICED");

        // 근본 원인 차단 — 공용 스키마에 example이 붙으면 예시를 안 준 폼이 남의 도메인 값을 그린다
        assertThat(apiDocs.at("/components/schemas/FormResponse").toString())
                .as("공용 폼 스키마에 example이 붙어 있다")
                .doesNotContain("\"example\"");
        assertThat(apiDocs.at("/components/schemas/FormOption").toString())
                .as("공용 선택지 스키마에 example이 붙어 있다")
                .doesNotContain("\"example\"");
    }

    /**
     * 공용 {@code FormResponse} 스키마에 한쪽 도메인 값을 example로 달면 두 폼이 같은 예시를 그린다. 문서 전체를 문자열로 훑으면 두 값이 모두
     * 들어 있어 잡히지 않으므로, 엔드포인트별 응답 예시만 떼어내 확인한다.
     */
    private String responseExample(JsonNode apiDocs, String path) {
        JsonNode content =
                apiDocs.at(
                        "/paths/"
                                + path.replace("/", "~1")
                                + "/get/responses/200/content/application~1json");

        // @Content를 직접 주면 응답 스키마가 통째로 빠진다(예시만 남고 필드 설명이 사라진다).
        // useReturnTypeSchema로 스키마를 등록하고 ref로 다시 붙여둔 상태를 지킨다.
        String ref = content.at("/schema/$ref").asText();
        assertThat(ref).as("%s 의 200 응답에 스키마가 붙어 있지 않다", path).isNotEmpty();
        assertThat(apiDocs.at(ref.substring(1)).isMissingNode())
                .as("%s 가 가리키는 스키마(%s)가 문서에 없다 — 끊어진 ref다", path, ref)
                .isFalse();

        JsonNode examples = content.path("examples");
        assertThat(examples.isMissingNode() || examples.isEmpty())
                .as("%s 의 200 응답 예시가 없다", path)
                .isFalse();
        return examples.toString();
    }

    private String fetchApiDocs() throws Exception {
        return mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
    }
}
