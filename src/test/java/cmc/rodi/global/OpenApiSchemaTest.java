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
    @DisplayName("회원 수정 요청 스키마엔 drivingGoal·nickname만 노출된다")
    void 회원_수정_요청_필드() throws Exception {
        JsonNode apiDocs = objectMapper.readTree(fetchApiDocs());

        // 생략/null 구분용 내부 플래그(drivingGoalSent 등)가 요청 필드로 그려지면 안 된다
        List<String> fields = new ArrayList<>();
        apiDocs.at("/components/schemas/MemberUpdateRequest/properties")
                .fieldNames()
                .forEachRemaining(fields::add);
        assertThat(fields).containsExactlyInAnyOrder("drivingGoal", "nickname");
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

    @Test
    @DisplayName("온보딩 폼과 후기 작성 폼의 단계·문항 스키마는 이름이 겹치지 않는다")
    void 폼_스키마_이름_충돌_없음() throws Exception {
        JsonNode apiDocs = objectMapper.readTree(fetchApiDocs());

        // springdoc은 클래스 단순 이름으로 스키마를 만든다. 두 폼이 둘 다 Step·Question이면 한쪽이 덮어써
        // 온보딩 문서에서 skippable·maxSelect·exclusive가 사라지고 후기 선택지(FormOption)가 대신 붙었다.
        JsonNode onboardingStep =
                schemaOf(
                        apiDocs,
                        propertyOf(apiDocs, "OnboardingFormResponse", "steps").path("items"));
        JsonNode onboardingQuestion =
                schemaOf(
                        apiDocs, onboardingStep.path("properties").path("questions").path("items"));
        JsonNode onboardingOption =
                schemaOf(
                        apiDocs,
                        onboardingQuestion.path("properties").path("options").path("items"));
        assertThat(onboardingStep.path("properties").has("skippable")).isTrue();
        assertThat(onboardingQuestion.path("properties").has("maxSelect")).isTrue();
        assertThat(onboardingOption.path("properties").has("exclusive")).isTrue();
        assertThat(onboardingOption.path("properties").has("requiresTextInput")).isFalse();

        JsonNode reviewStepRef = propertyOf(apiDocs, "ReviewFormResponse", "steps").path("items");
        assertThat(reviewStepRef.path("$ref").asText()).endsWith("/ReviewFormStep");
        JsonNode reviewQuestion =
                schemaOf(
                        apiDocs,
                        schemaOf(apiDocs, reviewStepRef)
                                .path("properties")
                                .path("questions")
                                .path("items"));
        assertThat(reviewQuestion.path("properties").has("maxSelect")).isFalse();

        // 조건부 노출은 두 폼이 같은 공용 ShowWhen을 쓴다(Prod와 같은 이름)
        assertThat(onboardingQuestion.path("properties").path("showWhen").path("$ref").asText())
                .isEqualTo("#/components/schemas/ShowWhen");
        assertThat(reviewQuestion.path("properties").path("showWhen").path("$ref").asText())
                .isEqualTo("#/components/schemas/ShowWhen");
    }

    private static JsonNode propertyOf(JsonNode apiDocs, String schema, String property) {
        return apiDocs.path("components")
                .path("schemas")
                .path(schema)
                .path("properties")
                .path(property);
    }

    /** {@code $ref}를 따라가 스키마 본문을 돌려준다. */
    private static JsonNode schemaOf(JsonNode apiDocs, JsonNode ref) {
        String name = ref.path("$ref").asText().replace("#/components/schemas/", "");
        return apiDocs.path("components").path("schemas").path(name);
    }

    private String fetchApiDocs() throws Exception {
        return mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
    }
}
