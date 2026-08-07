package cmc.rodi.domain.practice.dto;

import cmc.rodi.domain.practice.entity.MemberPractice;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 방문 처리 결과. 앱이 인증 성공 여부와 기준을 바로 표시할 수 있게 돌려준다. 미방문(NOT_VISITED) 처리에서는 인증과 무관하므로 거리·인증 값이 모두
 * 0/false다.
 */
public record PracticeVisitResponse(
        @Schema(description = "지금까지 다녀온 횟수") int visitCount,
        @Schema(description = "이번에 반영된 인정 주행거리(m)") int addedCertifiedDistanceMeters,
        @Schema(description = "인증에 필요한 거리(m). 주차장 등 주행거리가 없는 장소는 0") int requiredDistanceMeters,
        @Schema(description = "이번 방문으로 인증되었는지") @JsonProperty("isCertifiedNow")
                boolean certifiedNow,
        @Schema(description = "이 항목이 한 번이라도 인증된 적 있는지") @JsonProperty("isVerified")
                boolean verified) {

    public static PracticeVisitResponse of(
            MemberPractice practice, int addedMeters, boolean certifiedNow) {
        return new PracticeVisitResponse(
                practice.getVisitCount(),
                addedMeters,
                practice.requiredCertificationMeters(),
                certifiedNow,
                practice.isVerified());
    }

    /** 미방문 처리 결과 — 거리·인증 없음. */
    public static PracticeVisitResponse notVisited(MemberPractice practice) {
        return new PracticeVisitResponse(
                practice.getVisitCount(),
                0,
                practice.requiredCertificationMeters(),
                false,
                practice.isVerified());
    }
}
