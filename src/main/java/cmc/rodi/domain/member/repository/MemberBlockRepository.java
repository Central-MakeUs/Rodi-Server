package cmc.rodi.domain.member.repository;

import cmc.rodi.domain.member.entity.MemberBlock;
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
}
