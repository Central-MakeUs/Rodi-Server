package cmc.rodi.global.common.form;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

/** 선택지 하나. 텍스트 입력이 없는 항목은 {@code textInput*} 필드를 아예 내려보내지 않는다(NON_NULL). */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record FormOption(
        @Schema(description = "선택지 코드(제출 시 이 값을 보낸다)", example = "SPAM") String code,
        @Schema(description = "화면 표시 문구", example = "스팸/광고") String label,
        @Schema(description = "노출 순서(1부터)") int order,
        @Schema(description = "선택 시 텍스트 입력이 필요한지") @JsonProperty("requiresTextInput")
                boolean requiresTextInput,
        @Schema(description = "텍스트 입력 placeholder(필요할 때만)") String textInputPlaceholder,
        @Schema(description = "텍스트 입력 최대 길이(필요할 때만)") Integer textInputMaxLength) {

    /** 텍스트 입력이 없는 일반 선택지. */
    public static FormOption of(String code, String label, int order) {
        return new FormOption(code, label, order, false, null, null);
    }

    /** "기타"처럼 직접 입력을 받는 선택지. */
    public static FormOption withTextInput(
            String code, String label, int order, String placeholder, int maxLength) {
        return new FormOption(code, label, order, true, placeholder, maxLength);
    }
}
