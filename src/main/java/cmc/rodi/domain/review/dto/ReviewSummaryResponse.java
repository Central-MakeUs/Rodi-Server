package cmc.rodi.domain.review.dto;

import cmc.rodi.domain.member.entity.Level;
import cmc.rodi.domain.review.entity.Congestion;
import cmc.rodi.domain.review.entity.Difficulty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;

/**
 * 후기 요약(난이도 분포 화면). 분포는 <b>선택한 레벨 기준 후기 건수</b>이며, 0건인 값도 키를 빼지 않고 0으로 채워 막대가 항상 그려지게 한다. 막대 비율은
 * 클라이언트가 최대값 기준으로 계산한다. 차단은 반영하지 않아 수치가 모두에게 동일하다.
 */
public record ReviewSummaryResponse(
        @Schema(description = "적용된 레벨 필터(전체면 ALL)", example = "ROOKIE") String level,
        @Schema(description = "선택 레벨 기준 총 후기 수") long totalCount,
        @Schema(description = "추천 수") long recommendCount,
        @Schema(description = "비추천 수") long notRecommendCount,
        @Schema(description = "난이도별 후기 수(5개 항상 포함)") Map<Difficulty, Long> difficultyCounts,
        @Schema(description = "혼잡도별 후기 수(3개 항상 포함)") Map<Congestion, Long> congestionCounts,
        @Schema(description = "레벨별 후기 수(레벨 필터와 무관한 전체 분포)") Map<Level, Long> levelCounts) {}
