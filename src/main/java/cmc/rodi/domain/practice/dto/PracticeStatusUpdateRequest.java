package cmc.rodi.domain.practice.dto;

import cmc.rodi.domain.practice.entity.PracticeStatus;
import cmc.rodi.domain.practice.entity.SkipReason;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * 방문 여부 상태 변경 요청. {@code VISITED}는 클라이언트가 이동 추적으로 자동 판정해 호출하고, {@code NOT_VISITED}는 사유가 필수다. 사유가
 * {@code OTHER}면 직접 입력도 필수이며, 다른 사유의 직접 입력은 저장하지 않는다.
 */
public record PracticeStatusUpdateRequest(
        @Schema(description = "바꿀 상태(VISITED / NOT_VISITED)") @NotNull PracticeStatus status,
        @Schema(
                        description =
                                "앱이 GPS로 측정한 인정 주행거리(m). 코스 경로선 150m 이내에서 이동한 거리만."
                                        + " 측정 없이 다녀왔어요만 누른 경우 생략(=0). 인증 여부는 서버가 판정한다.",
                        example = "2100")
                @PositiveOrZero
                Integer certifiedDistanceMeters,
        @Schema(description = "미방문 사유(NOT_VISITED 필수)") SkipReason skipReason,
        @Schema(description = "직접 입력 사유(기타 선택 시 필수, 최대 100자)", example = "차가 정비 중이었어요")
                @Size(max = SkipReason.TEXT_INPUT_MAX_LENGTH)
                String skipDetail) {

    public PracticeStatusUpdateRequest {
        // 직접 입력은 OTHER에서만 의미가 있다(신고 사유 요청과 같은 규칙).
        boolean acceptsDetail = skipReason != null && skipReason.requiresTextInput();
        skipDetail =
                (!acceptsDetail || skipDetail == null || skipDetail.isBlank())
                        ? null
                        : skipDetail.trim();
    }

    /** PLANNED로 되돌리는 건 [연습하기] 재등록이 하므로 상태 변경에서는 받지 않는다. */
    @AssertTrue(message = "VISITED 또는 NOT_VISITED만 지정할 수 있습니다")
    public boolean isTransitionAllowed() {
        return status == null || status != PracticeStatus.PLANNED;
    }

    /** 기타는 직접 입력이 반드시 있어야 한다. */
    @AssertTrue(message = "기타 사유는 직접 입력이 필요합니다")
    public boolean isSkipDetailConsistent() {
        return skipReason == null || !skipReason.requiresTextInput() || skipDetail != null;
    }
}
