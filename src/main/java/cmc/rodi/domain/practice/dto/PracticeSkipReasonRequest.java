package cmc.rodi.domain.practice.dto;

import cmc.rodi.domain.practice.entity.SkipReason;
import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 미방문 사유 제출. 미방문 이유 폼(GET /practices/skip-reason-form)의 선택지 코드를 그대로 보낸다. {@code OTHER}(기타)면 직접 입력이
 * 필수이고, 다른 사유의 직접 입력은 저장하지 않는다.
 */
public record PracticeSkipReasonRequest(
        @Schema(description = "미방문 사유 코드") @NotNull SkipReason reason,
        @Schema(description = "직접 입력 사유(기타 선택 시 필수, 최대 100자)", example = "차가 정비 중이었어요")
                @Size(max = SkipReason.TEXT_INPUT_MAX_LENGTH)
                String detail) {

    public PracticeSkipReasonRequest {
        boolean acceptsDetail = reason != null && reason.requiresTextInput();
        detail = (!acceptsDetail || detail == null || detail.isBlank()) ? null : detail.trim();
    }

    /** 기타는 직접 입력이 반드시 있어야 한다. */
    @JsonIgnore
    @Schema(hidden = true) // 검증 메서드일 뿐이라 요청 스키마에 노출하지 않는다
    @AssertTrue(message = "기타 사유는 직접 입력이 필요합니다")
    public boolean isDetailConsistent() {
        return reason == null || !reason.requiresTextInput() || detail != null;
    }
}
