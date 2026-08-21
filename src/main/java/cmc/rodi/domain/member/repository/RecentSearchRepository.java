package cmc.rodi.domain.member.repository;

import cmc.rodi.domain.member.entity.RecentSearch;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RecentSearchRepository extends JpaRepository<RecentSearch, Long> {

    /**
     * 지역(REGION) 등록(upsert). 부분 유니크 uq_recent_search_region(member_id, keyword) 충돌 시 새 행 없이
     * searched_at만 갱신해 맨 앞으로. 재검색이 맨 앞으로 오도록 트랜잭션 고정값 now() 대신 벽시계 clock_timestamp()를 쓴다. 네이티브라
     * auditing 없이 시각을 직접 넣는다.
     */
    @Modifying
    @Query(
            value =
                    """
                    INSERT INTO member_recent_search (member_id, type, keyword, searched_at)
                    VALUES (:memberId, 'REGION', :keyword, clock_timestamp())
                    ON CONFLICT (member_id, keyword) WHERE type = 'REGION'
                    DO UPDATE SET searched_at = clock_timestamp()
                    """,
            nativeQuery = true)
    void upsertRegion(@Param("memberId") Long memberId, @Param("keyword") String keyword);

    /**
     * 장소(PLACE) 등록(upsert). 부분 유니크 uq_recent_search_place(member_id, place_id) 충돌 시
     * searched_at·keyword를 갱신(이름 변경 대비)해 맨 앞으로. 이름이 같아도 place_id가 다르면 별개로 저장된다.
     */
    @Modifying
    @Query(
            value =
                    """
                    INSERT INTO member_recent_search (member_id, type, keyword, place_id, searched_at)
                    VALUES (:memberId, 'PLACE', :keyword, :placeId, clock_timestamp())
                    ON CONFLICT (member_id, place_id) WHERE type = 'PLACE'
                    DO UPDATE SET searched_at = clock_timestamp(), keyword = EXCLUDED.keyword
                    """,
            nativeQuery = true)
    void upsertPlace(
            @Param("memberId") Long memberId,
            @Param("keyword") String keyword,
            @Param("placeId") Long placeId);

    /** 회원의 최근 검색어를 최신순으로 조회(상한은 Pageable로 제한). searched_at 동률은 id로 결정적 정렬. */
    List<RecentSearch> findByMemberIdOrderBySearchedAtDescIdDesc(Long memberId, Pageable pageable);

    /** 상한(keep) 초과분 제거 — 최신 keep개만 남기고 오래된 것부터 삭제. */
    @Modifying
    @Query(
            value =
                    """
                    DELETE FROM member_recent_search
                    WHERE member_id = :memberId
                      AND id NOT IN (
                          SELECT id FROM member_recent_search
                          WHERE member_id = :memberId
                          ORDER BY searched_at DESC, id DESC
                          LIMIT :keep
                      )
                    """,
            nativeQuery = true)
    void deleteBeyondCap(@Param("memberId") Long memberId, @Param("keep") int keep);

    /** 개별 삭제(본인 소유만). 없거나 타인 것이면 0건 삭제(멱등). */
    long deleteByIdAndMemberId(Long id, Long memberId);

    /** 회원의 전체 최근 검색어 삭제. */
    void deleteByMemberId(Long memberId);
}
