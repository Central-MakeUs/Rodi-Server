package cmc.rodi.global.common.form;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * 조건부 노출. {@code question} 문항에서 {@code anyOf} 중 하나라도 골랐을 때만 문항이 보인다. 온보딩 폼·후기 작성 폼이 함께 쓴다.
 *
 * <p>스키마 이름은 클래스 이름 그대로 {@code ShowWhen}이다 — 각 폼에 따로 있던 시절의 이름과 같아 문서가 바뀌지 않는다(스펙 025).
 */
public record ShowWhen(
        @Schema(description = "조건 문항 코드") String question,
        @Schema(description = "이 중 하나라도 고르면 노출") List<String> anyOf) {}
