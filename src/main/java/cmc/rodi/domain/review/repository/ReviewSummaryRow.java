package cmc.rodi.domain.review.repository;

/**
 * 후기 요약 집계 한 행. 집계 단위는 사람 수가 아니라 후기 건수다.
 *
 * <p>모수가 둘이다 — {@code total}·추천 수는 <b>전체 레벨</b>, {@code levelCount}·난이도별 수는 <b>선택한 레벨</b> 기준이다.
 */
public interface ReviewSummaryRow {
    long getTotalCount();

    long getRecommendCount();

    long getNotRecommendCount();

    long getLevelCount();

    long getVeryEasy();

    long getEasy();

    long getNormalDifficulty();

    long getHard();

    long getVeryHard();
}
