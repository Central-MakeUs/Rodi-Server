package cmc.rodi.domain.place.repository;

import cmc.rodi.domain.place.dto.MyCourseItem;
import cmc.rodi.domain.place.entity.ApprovalStatus;
import cmc.rodi.domain.place.entity.Course;
import java.util.List;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CourseRepository extends JpaRepository<Course, Long> {

    /**
     * 내가 등록한 코스 목록(스펙 015). 최신 등록순 커서 페이징(ADR 0010).
     *
     * <p>{@code place.id}가 IDENTITY라 등록 시각과 함께 단조 증가한다 → <b>id 내림차순이 곧 최신 등록순</b>이고 커서 keyset도 id
     * 하나로 충분하다(저장 목록과 같은 판단).
     *
     * <p>{@code status}가 null이면 상태 필터 없이 전부. 삭제한 코스는 등록자 본인 목록에서도 빠진다.
     */
    @Query(
            """
            SELECT new cmc.rodi.domain.place.dto.MyCourseItem(c.id, c.name, c.approvalStatus, c.createdAt)
            FROM Course c
            WHERE c.createdBy.id = :memberId
              AND c.deletedAt IS NULL
              AND (:status IS NULL OR c.approvalStatus = :status)
              AND (:cursorId IS NULL OR c.id < :cursorId)
            ORDER BY c.id DESC
            """)
    List<MyCourseItem> findMyCourses(
            @Param("memberId") Long memberId,
            @Param("status") ApprovalStatus status,
            @Param("cursorId") Long cursorId,
            Limit limit);

    /** 내 코스 총 개수(첫 페이지 전용). 상태 필터가 적용된 개수다. */
    @Query(
            """
            SELECT COUNT(c) FROM Course c
            WHERE c.createdBy.id = :memberId
              AND c.deletedAt IS NULL
              AND (:status IS NULL OR c.approvalStatus = :status)
            """)
    long countMyCourses(@Param("memberId") Long memberId, @Param("status") ApprovalStatus status);
}
