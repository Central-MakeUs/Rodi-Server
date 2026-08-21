package cmc.rodi.domain.member.repository;

import cmc.rodi.domain.member.entity.MemberBlock;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MemberBlockRepository extends JpaRepository<MemberBlock, Long> {

    /** 차단(멱등). unique(blocker_id, blocked_id) 충돌 시 무시한다. 네이티브라 타임스탬프를 직접 넣는다. */
    @Modifying
    @Query(
            value =
                    """
                    INSERT INTO member_block (blocker_id, blocked_id, created_at, updated_at)
                    VALUES (:blockerId, :blockedId, now(), now())
                    ON CONFLICT (blocker_id, blocked_id) DO NOTHING
                    """,
            nativeQuery = true)
    int saveIfAbsent(@Param("blockerId") Long blockerId, @Param("blockedId") Long blockedId);

    /** 차단 해제(멱등). 없으면 아무 일도 없다. */
    void deleteByBlockerIdAndBlockedId(Long blockerId, Long blockedId);

    boolean existsByBlockerIdAndBlockedId(Long blockerId, Long blockedId);

    /**
     * 내가 차단한 회원 한 페이지(차단한 시각 최신순 keyset). 차단 대상을 함께 읽어 닉네임 조회로 인한 N+1을 막는다.
     *
     * <p>커서를 null로 두면 Postgres가 바인드 타입을 못 정하므로(could not determine data type) 첫 페이지는 미래 시각
     * sentinel로 표현한다. 다음 페이지 존재 판별을 위해 호출부가 size+1로 요청한다.
     */
    @Query(
            """
            SELECT b FROM MemberBlock b
            JOIN FETCH b.blocked
            WHERE b.blocker.id = :blockerId
              AND (b.createdAt < :cursorTime
                   OR (b.createdAt = :cursorTime AND b.id < :cursorId))
            ORDER BY b.createdAt DESC, b.id DESC
            """)
    List<MemberBlock> findPage(
            @Param("blockerId") Long blockerId,
            @Param("cursorTime") LocalDateTime cursorTime,
            @Param("cursorId") Long cursorId,
            Pageable pageable);

    /** 내가 차단한 회원 총계(첫 페이지 전용). */
    long countByBlockerId(Long blockerId);
}
