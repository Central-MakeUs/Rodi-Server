package cmc.rodi.domain.review.repository;

/** 후기 요약 집계 한 행(추천·난이도·혼잡도 카운트). 집계 단위는 사람 수가 아니라 후기 건수다. */
public interface ReviewSummaryRow {
    long getTotal();

    long getRecommendCount();

    long getNotRecommendCount();

    long getVeryEasy();

    long getEasy();

    long getNormalDifficulty();

    long getHard();

    long getVeryHard();

    long getQuiet();

    long getNormalCongestion();

    long getCrowded();
}
