package cmc.rodi.domain.review.repository;

import cmc.rodi.domain.review.entity.ReviewReport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReviewReportRepository extends JpaRepository<ReviewReport, Long> {

    /**
     * 신고 접수(멱등). 같은 후기·같은 신고자는 unique 충돌로 무시되어 재신고해도 첫 신고가 유지된다. 네이티브라 auditing이 안 걸려 타임스탬프를 직접
     * 넣는다.
     *
     * @return 새로 접수했으면 1, 이미 신고한 상태면 0
     */
    @Modifying
    @Query(
            value =
                    """
                    INSERT INTO review_report
                        (review_id, reporter_id, reason, detail, created_at, updated_at)
                    VALUES (:reviewId, :reporterId, :reason, :detail, now(), now())
                    ON CONFLICT (review_id, reporter_id) DO NOTHING
                    """,
            nativeQuery = true)
    int saveIfAbsent(
            @Param("reviewId") Long reviewId,
            @Param("reporterId") Long reporterId,
            @Param("reason") String reason,
            @Param("detail") String detail);

    boolean existsByReviewIdAndReporterId(Long reviewId, Long reporterId);
}
