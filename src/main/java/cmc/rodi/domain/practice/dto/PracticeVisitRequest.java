package cmc.rodi.domain.practice.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * 방문 기록 요청(RV-01의 "다녀왔어요"). 상태는 서버가 정한다 — 이 요청이 오면 방문 처리이고, 인증 여부는 인정 주행거리로 판정한다. "안 했어요"는 미방문 사유
 * 제출 API(POST /practices/{id}/skip-reason)가 담당한다.
 */
public record PracticeVisitRequest(
        @Schema(
                        description =
                                "앱이 GPS로 측정한 인정 주행거리(m). 코스 경로선 150m 이내에서 이동한 거리만."
                                        + " 측정 없이 다녀왔어요만 누른 경우 생략(=0) — 이때는 인증되지 않는다.",
                        example = "2100")
                @PositiveOrZero
                Integer certifiedDistanceMeters) {

    /** 생략 시 0(측정 없음). */
    public int metersOrZero() {
        return certifiedDistanceMeters == null ? 0 : certifiedDistanceMeters;
    }
}
