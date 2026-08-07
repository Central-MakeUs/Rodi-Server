package cmc.rodi.global.common.form;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * 서버가 내려주는 선택 폼 정의(문항 + 선택지). 문구·순서를 서버가 쥐고 있어 앱 배포 없이 바꿀 수 있다.
 *
 * <p>선택지 코드는 그대로 제출 값이 된다(예: 신고 사유 {@code SPAM}). 화면에 설명이 없는 폼은 {@code description}을
 * 생략한다(NON_NULL).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record FormResponse(
        @Schema(description = "문항 식별자", example = "REVIEW_REPORT_REASON") String questionId,
        @Schema(description = "문항 유형") FormType type,
        @Schema(description = "제목", example = "신고 사유") String title,
        @Schema(description = "부연 설명(없으면 생략)") String description,
        @Schema(description = "필수 응답 여부") boolean required,
        @Schema(description = "선택지(order 오름차순)") List<FormOption> options) {}
