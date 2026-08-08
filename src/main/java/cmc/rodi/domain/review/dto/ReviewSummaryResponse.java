package cmc.rodi.domain.review.dto;

import cmc.rodi.domain.member.entity.Level;
import cmc.rodi.domain.review.entity.Difficulty;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;

/**
 * 후기 요약(난이도 분포 화면). 모수가 둘이다 — <b>추천 수는 전체 레벨 합산</b>이고, 난이도 분포는 <b>선택한 레벨 기준</b>이다. 분포는 0건인 값도 키를 빼지
 * 않고 0으로 채워 막대가 항상 그려지게 한다. 막대 비율은 클라이언트가 최대값 기준으로 계산한다. 차단은 반영하지 않아 수치가 모두에게 동일하다.
 *
 * <p>혼잡도는 화면에 쓰지 않아 응답에서 뺐다(작성 시 저장은 계속 한다).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ReviewSummaryResponse(
        @Schema(description = "적용된 레벨 필터(전체면 ALL)", example = "ROOKIE") String level,
        @Schema(description = "선택 레벨 기준 후기 수(난이도 분포의 모수)") long levelReviewCount,
        @Schema(description = "전체 레벨 후기 수(추천 수의 모수)") long totalReviewCount,
        @Schema(description = "가장 많이 선택된 난이도(선택 레벨 기준). 후기가 없으면 키 자체가 생략된다")
                TopDifficulty topDifficulty,
        @Schema(description = "추천 수(전체 레벨 합산)") long recommendCount,
        @Schema(description = "비추천 수(전체 레벨 합산)") long notRecommendCount,
        @Schema(description = "난이도별 후기 수(선택 레벨 기준, 5개 항상 포함)")
                Map<Difficulty, Long> difficultyCounts,
        @Schema(description = "레벨별 후기 수(레벨 필터와 무관한 전체 분포)") Map<Level, Long> levelCounts) {

    /** 최다 선택 난이도. 동률이면 <b>더 어려운 쪽</b>을 고른다(쉽다고 오인해 무리하는 쪽이 더 위험하다). */
    public record TopDifficulty(
            @Schema(description = "난이도") Difficulty difficulty,
            @Schema(description = "그 난이도를 고른 후기 수") long count) {}
}
