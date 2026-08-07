package cmc.rodi.domain.practice.dto;

import cmc.rodi.domain.practice.entity.MemberPractice;
import cmc.rodi.domain.practice.entity.PracticeStatus;
import io.swagger.v3.oas.annotations.media.Schema;

/** 연습 목록 담기 결과. 이미 담긴 장소였다면 기존 항목이 예정 상태로 되돌아온 것이다(연습 횟수는 유지). */
public record PracticeRegisterResponse(
        @Schema(description = "연습 항목 id") Long practiceId,
        @Schema(description = "상태") PracticeStatus status,
        @Schema(description = "지금까지 다녀온 횟수") int visitCount) {

    public static PracticeRegisterResponse from(MemberPractice practice) {
        return new PracticeRegisterResponse(
                practice.getId(), practice.getStatus(), practice.getVisitCount());
    }
}
