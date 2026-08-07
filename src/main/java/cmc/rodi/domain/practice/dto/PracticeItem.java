package cmc.rodi.domain.practice.dto;

import cmc.rodi.domain.place.dto.PlaceListItem;
import cmc.rodi.domain.practice.entity.MemberPractice;
import cmc.rodi.domain.practice.entity.PracticeStatus;
import cmc.rodi.domain.practice.entity.SkipReason;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

/**
 * 내 연습 목록 아이템. 장소 요약은 저장 목록·현위치 목록과 같은 {@link PlaceListItem}을 재사용한다(현위치를 받지 않으므로 {@code
 * distanceFromMe}는 null).
 */
public record PracticeItem(
        @Schema(description = "연습 항목 id") Long practiceId,
        @Schema(description = "상태") PracticeStatus status,
        @Schema(description = "다녀온 횟수") int visitCount,
        @Schema(description = "마지막 방문 시각(없으면 null)") LocalDateTime visitedAt,
        @Schema(description = "미방문 사유(NOT_VISITED일 때만)") SkipReason skipReason,
        @Schema(description = "미방문 직접 입력 사유(기타일 때만)") String skipDetail,
        @Schema(description = "담은 시각") LocalDateTime createdAt,
        @Schema(description = "장소 요약") PlaceListItem place) {

    public static PracticeItem of(MemberPractice practice, PlaceListItem place) {
        return new PracticeItem(
                practice.getId(),
                practice.getStatus(),
                practice.getVisitCount(),
                practice.getVisitedAt(),
                practice.getSkipReason(),
                practice.getSkipDetail(),
                practice.getCreatedAt(),
                place);
    }
}
