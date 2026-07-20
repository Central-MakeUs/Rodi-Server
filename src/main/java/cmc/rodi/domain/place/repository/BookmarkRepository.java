package cmc.rodi.domain.place.repository;

import cmc.rodi.domain.place.entity.Bookmark;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BookmarkRepository extends JpaRepository<Bookmark, Long> {

    /** 장소의 북마크 수(상세 응답용). */
    long countByPlaceId(Long placeId);

    /** 회원이 저장한 장소 수(마이페이지·저장 목록 totalCount용). */
    long countByMemberId(Long memberId);

    /**
     * 저장 목록 커서 조회. 최신 저장순(bookmark.id DESC) + keyset(:cursorId 미만). id가 저장 시각과 단조 증가라 id 하나로 정렬·커서가
     * 성립한다. 한 건 더(limit=size+1) 조회해 다음 페이지 존재를 판별한다.
     */
    @Query(
            value =
                    """
                    SELECT b.place_id AS placeId, b.id AS bookmarkId
                    FROM bookmark b
                    WHERE b.member_id = :memberId
                      AND (:cursorId IS NULL OR b.id < :cursorId)
                    ORDER BY b.id DESC
                    LIMIT :limit
                    """,
            nativeQuery = true)
    List<SavedBookmarkRow> findSavedRefs(
            @Param("memberId") Long memberId,
            @Param("cursorId") Long cursorId,
            @Param("limit") int limit);

    /** 현재 회원이 이 장소를 북마크했는지. */
    boolean existsByMemberIdAndPlaceId(Long memberId, Long placeId);

    /**
     * 북마크 저장(멱등). unique(member_id, place_id) 충돌 시 무시하므로 동시 요청(더블탭)에도 안전하다. 네이티브라 auditing이 안 걸려
     * 타임스탬프를 직접 넣는다.
     *
     * @return 새로 저장했으면 1, 이미 있었으면 0
     */
    @Modifying
    @Query(
            value =
                    """
                    INSERT INTO bookmark (member_id, place_id, created_at, updated_at)
                    VALUES (:memberId, :placeId, now(), now())
                    ON CONFLICT (member_id, place_id) DO NOTHING
                    """,
            nativeQuery = true)
    int saveIfAbsent(@Param("memberId") Long memberId, @Param("placeId") Long placeId);

    /** 북마크 해제(멱등). 없으면 아무 일도 없다. */
    void deleteByMemberIdAndPlaceId(Long memberId, Long placeId);
}
