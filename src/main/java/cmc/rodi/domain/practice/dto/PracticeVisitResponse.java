package cmc.rodi.domain.practice.dto;

import cmc.rodi.domain.member.entity.Level;
import cmc.rodi.domain.member.entity.Member;
import cmc.rodi.domain.practice.entity.MemberPractice;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 방문 처리 결과. 앱이 인증 성공 여부와 기준을 바로 표시하고, 레벨이 올랐으면 팝업을 띄울 수 있게 돌려준다.
 *
 * <p>거리 필드가 둘인 이유 — {@code addedCertifiedDistanceMeters}는 이 항목의 인증 판정에 쓰인 값이고, 레벨에 누적되는 값은 코스 전체
 * 거리로 잘린 뒤라 더 작을 수 있다(스펙 012).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PracticeVisitResponse(
        @Schema(description = "지금까지 다녀온 횟수") int visitCount,
        @Schema(description = "이번에 반영된 인정 주행거리(m)") int addedCertifiedDistanceMeters,
        @Schema(description = "인증에 필요한 거리(m). 주차장 등 주행거리가 없는 장소는 0") int requiredDistanceMeters,
        @Schema(description = "이번 방문으로 인증되었는지") @JsonProperty("isCertifiedNow")
                boolean certifiedNow,
        @Schema(description = "이 항목이 한 번이라도 인증된 적 있는지") @JsonProperty("isVerified")
                boolean verified,
        @Schema(description = "레벨 게이지 기준 누적 주행거리(km)") double totalDistanceKm,
        @Schema(description = "이번 방문으로 레벨이 올랐는지") boolean levelUp,
        @Schema(description = "오른 뒤의 레벨(두 단계 이상이면 최종 레벨). 승급하지 않았으면 생략") Level newLevel) {

    public static PracticeVisitResponse of(
            MemberPractice practice,
            int addedMeters,
            boolean certifiedNow,
            Member member,
            boolean levelUp) {
        return new PracticeVisitResponse(
                practice.getVisitCount(),
                addedMeters,
                practice.requiredCertificationMeters(),
                certifiedNow,
                practice.isVerified(),
                toKm(member.getTotalDistanceMeters()),
                levelUp,
                levelUp ? member.getLevel() : null);
    }

    /**
     * 쿨다운에 걸려 <b>아무것도 바꾸지 않은</b> 결과. 재시도한 앱이 실패로 보지 않도록 현재 상태를 그대로 돌려준다 — 이번 회차로 반영된 게 없으니 거리는 0이고
     * {@code isCertifiedNow}·{@code levelUp}은 false이며, 인증 배지({@code isVerified})는 사실 그대로다.
     */
    public static PracticeVisitResponse unchanged(MemberPractice practice, Member member) {
        return new PracticeVisitResponse(
                practice.getVisitCount(),
                0,
                practice.requiredCertificationMeters(),
                false,
                practice.isVerified(),
                toKm(member.getTotalDistanceMeters()),
                false,
                null);
    }

    /** 소수 첫째 자리에서 <b>내림</b> — 진행률과 같은 방향으로, 실제보다 앞서 보이지 않게 한다. */
    private static double toKm(long meters) {
        return Math.floor(meters / 100.0) / 10.0;
    }
}
