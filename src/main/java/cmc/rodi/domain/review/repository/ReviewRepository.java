package cmc.rodi.domain.review.repository;

import cmc.rodi.domain.member.entity.Level;
import cmc.rodi.domain.review.entity.Review;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReviewRepository extends JpaRepository<Review, Long> {

    /**
     * 장소 후기 한 페이지(최신순 keyset). {@code levels}에 전체 레벨을 넘기면 필터가 없는 것과 같고, 조회 회원이 차단한 작성자의 후기는 제외한다.
     * 다음 페이지 존재 판별을 위해 호출부가 size+1로 요청한다.
     *
     * <p>레벨·커서를 null로 두면 Postgres가 바인드 파라미터 타입을 못 정하므로(could not determine data type), 필터 없음은 전체 레벨
     * 목록으로, 첫 페이지는 미래 시각 sentinel 커서로 표현한다.
     */
    @Query(
            """
            SELECT r FROM Review r
            JOIN FETCH r.member
            WHERE r.place.id = :placeId
              AND r.memberLevel IN :levels
              AND NOT EXISTS (
                    SELECT 1 FROM MemberBlock b
                    WHERE b.blocker.id = :memberId AND b.blocked.id = r.member.id)
              AND (r.createdAt < :cursorTime
                   OR (r.createdAt = :cursorTime AND r.id < :cursorId))
            ORDER BY r.createdAt DESC, r.id DESC
            """)
    List<Review> findPage(
            @Param("placeId") Long placeId,
            @Param("levels") Collection<Level> levels,
            @Param("memberId") Long memberId,
            @Param("cursorTime") LocalDateTime cursorTime,
            @Param("cursorId") Long cursorId,
            Pageable pageable);

    /** 목록 totalCount(첫 페이지 전용). 레벨 필터·차단 제외 조건은 목록과 동일하다. */
    @Query(
            """
            SELECT COUNT(r) FROM Review r
            WHERE r.place.id = :placeId
              AND r.memberLevel IN :levels
              AND NOT EXISTS (
                    SELECT 1 FROM MemberBlock b
                    WHERE b.blocker.id = :memberId AND b.blocked.id = r.member.id)
            """)
    long countVisible(
            @Param("placeId") Long placeId,
            @Param("levels") Collection<Level> levels,
            @Param("memberId") Long memberId);

    /**
     * 요약 집계(추천·난이도·혼잡도)를 한 쿼리로. {@code :level}이 null이면 COALESCE가 컬럼 자기 비교가 되어 전체 레벨이 집계된다. 차단은 반영하지
     * 않는다(요약 수치는 모두에게 동일).
     */
    @Query(
            value =
                    """
                    SELECT COUNT(*)                                                  AS total,
                           COUNT(*) FILTER (WHERE r.is_recommended)                   AS recommendCount,
                           COUNT(*) FILTER (WHERE NOT r.is_recommended)               AS notRecommendCount,
                           COUNT(*) FILTER (WHERE r.difficulty = 'VERY_EASY')         AS veryEasy,
                           COUNT(*) FILTER (WHERE r.difficulty = 'EASY')              AS easy,
                           COUNT(*) FILTER (WHERE r.difficulty = 'NORMAL')            AS normalDifficulty,
                           COUNT(*) FILTER (WHERE r.difficulty = 'HARD')              AS hard,
                           COUNT(*) FILTER (WHERE r.difficulty = 'VERY_HARD')         AS veryHard,
                           COUNT(*) FILTER (WHERE r.congestion = 'QUIET')             AS quiet,
                           COUNT(*) FILTER (WHERE r.congestion = 'NORMAL')            AS normalCongestion,
                           COUNT(*) FILTER (WHERE r.congestion = 'CROWDED')           AS crowded
                    FROM review r
                    WHERE r.place_id = :placeId
                      AND r.member_level = COALESCE(CAST(:level AS varchar), r.member_level)
                    """,
            nativeQuery = true)
    ReviewSummaryRow summarize(@Param("placeId") Long placeId, @Param("level") String level);

    /** 레벨별 후기 수(드롭다운용). 레벨 필터와 무관한 전체 분포. */
    @Query(
            """
            SELECT r.memberLevel AS level, COUNT(r) AS cnt FROM Review r
            WHERE r.place.id = :placeId
            GROUP BY r.memberLevel
            """)
    List<LevelCountRow> countByLevel(@Param("placeId") Long placeId);
}
