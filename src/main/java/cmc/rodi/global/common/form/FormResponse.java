package cmc.rodi.global.common.form;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * 서버가 내려주는 선택 폼 정의(문항 + 선택지). 문구·순서를 서버가 쥐고 있어 앱 배포 없이 바꿀 수 있다.
 *
 * <p>선택지 코드는 그대로 제출 값이 된다(예: 신고 사유 {@code SPAM}). 화면에 설명이 없는 폼은 {@code description}을
 * 생략한다(NON_NULL).
 *
 * <p><b>필드에 example을 달지 않는다</b> — 여러 폼이 이 스키마를 공유하는데 springdoc은 스키마 단위로 example을 렌더링해, 한쪽 도메인 값을
 * 적어두면 다른 폼의 문서에도 그 값이 나온다. 예시는 각 엔드포인트에서 {@code @ExampleObject}로 준다.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record FormResponse(
        @Schema(description = "문항 식별자") String questionId,
        @Schema(description = "문항 유형") FormType type,
        @Schema(description = "제목") String title,
        @Schema(description = "부연 설명(없으면 생략)") String description,
        @Schema(description = "필수 응답 여부") boolean required,
        @Schema(description = "선택지(order 오름차순)") List<FormOption> options) {}
