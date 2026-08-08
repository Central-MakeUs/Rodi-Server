package cmc.rodi.domain.practice.repository;

import cmc.rodi.domain.practice.entity.MemberPractice;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MemberPracticeRepository extends JpaRepository<MemberPractice, Long> {

    /** 이미 담은 장소인지(재등록은 행을 늘리지 않고 이 항목을 되살린다). */
    Optional<MemberPractice> findByMemberIdAndPlaceId(Long memberId, Long placeId);

    long countByMemberId(Long memberId);

    /** 이 회원이 그 장소에서 GPS 방문 인증에 성공한 이력이 있는지(후기 "인증된 후기" 배지 판정). */
    boolean existsByMemberIdAndPlaceIdAndVerifiedTrue(Long memberId, Long placeId);

    /**
     * 내 연습 목록 한 페이지(최근 방문순 keyset). 방문 이력이 없는 항목은 담은 시각을 정렬값으로 써서 같은 축에 섞는다.
     *
     * <p>커서를 null로 두면 Postgres가 바인드 타입을 못 정하므로(could not determine data type) 첫 페이지는 미래 시각
     * sentinel로 표현한다. 다음 페이지 존재 판별을 위해 호출부가 size+1로 요청한다.
     */
    @Query(
            """
            SELECT p FROM MemberPractice p
            JOIN FETCH p.place
            WHERE p.member.id = :memberId
              AND (COALESCE(p.visitedAt, p.createdAt) < :cursorTime
                   OR (COALESCE(p.visitedAt, p.createdAt) = :cursorTime AND p.id < :cursorId))
            ORDER BY COALESCE(p.visitedAt, p.createdAt) DESC, p.id DESC
            """)
    List<MemberPractice> findPage(
            @Param("memberId") Long memberId,
            @Param("cursorTime") LocalDateTime cursorTime,
            @Param("cursorId") Long cursorId,
            Pageable pageable);
}
