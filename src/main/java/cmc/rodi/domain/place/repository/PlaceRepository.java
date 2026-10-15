package cmc.rodi.domain.place.repository;

import cmc.rodi.domain.member.entity.PracticeType;
import cmc.rodi.domain.place.entity.Place;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PlaceRepository extends JpaRepository<Place, Long> {

    /**
     * 전체 조회에 노출할 place 조건(스펙 014). 주차장은 항상 보이고, <b>코스는 승인됐고 삭제되지 않은 것만</b> 보인다.
     *
     * <p>목록·검색·연관검색어·마커가 각자 조건을 적으면 한 곳만 빠뜨려도 미승인 코스가 새어 나간다. 상수로 묶어 같은 술어를 공유한다. place 별칭은 {@code
     * p}로 고정.
     */
    String VISIBLE =
            """
            (p.place_type <> 'COURSE'
             OR EXISTS (SELECT 1 FROM course c
                        WHERE c.place_id = p.id
                          AND c.approval_status = 'APPROVED'
                          AND c.deleted_at IS NULL))
            """;

    /**
     * 필터({@code practiceTypes} 파라미터)에 맞는 place 조건(스펙 019). 코스는 태그 중 하나라도 기준에 있으면, 주차장은 기준에 PARKING이
     * 있으면({@code :filterParking = 1}) 맞는다. place 별칭은 {@code p}, {@code :filterTypes}는 비어 있지 않은 enum
     * 이름 목록.
     *
     * <p>{@link #MATCHES_SORT}와 <b>술어가 같고 파라미터 이름만 다르다</b>. 한 쿼리에 필터·정렬 기준이 함께 들어가 이름이 겹치면 안 되는데,
     * {@code @Query}에는 컴파일 타임 상수만 들어가 이름을 바꿔 끼우는 메서드를 쓸 수 없다. 한쪽을 고치면 다른 쪽도 고친다.
     */
    String MATCHES_FILTER =
            """
            ((p.place_type = 'PARKING' AND :filterParking = 1)
             OR EXISTS (SELECT 1 FROM course_practice_type cpt
                        WHERE cpt.course_id = p.id
                          AND cpt.practice_type IN (:filterTypes)))
            """;

    /**
     * 정렬 기준(회원 sort_tags)에 맞는 place 조건(스펙 007·019). 규칙은 {@link #MATCHES_FILTER}와 같고 파라미터 이름만 다르다.
     */
    String MATCHES_SORT =
            """
            ((p.place_type = 'PARKING' AND :sortParking = 1)
             OR EXISTS (SELECT 1 FROM course_practice_type cpt
                        WHERE cpt.course_id = p.id
                          AND cpt.practice_type IN (:sortTypes)))
            """;

    /**
     * 마커용 전체 좌표. 승인·미삭제 코스와 주차장만 반환한다.
     *
     * <p>네이티브가 아닌 JPQL인 이유: JOINED 상속이라 엔티티를 그대로 돌려주려면 하위 테이블 컬럼까지 필요하다.
     */
    @Query(
            """
            SELECT p FROM Place p
            WHERE TYPE(p) <> Course
               OR p.id IN (SELECT c.id FROM Course c
                            WHERE c.approvalStatus = cmc.rodi.domain.place.entity.ApprovalStatus.APPROVED
                              AND c.deletedAt IS NULL)
            """)
    List<Place> findAllVisible();

    /**
     * 필터를 건 마커용 좌표(스펙 019). {@link #findAllVisible} 중 {@code practiceTypes} 파라미터에 맞는 것만 — 코스는 태그가
     * 필터와 겹칠 때, 주차장은 필터에 PARKING이 있을 때({@code includeParking}). 매칭 규칙은 {@link #MATCHES_FILTER}와
     * 같다(JPQL이라 상수 대신 엔티티 관계로 적는다).
     */
    @Query(
            """
            SELECT p FROM Place p
            WHERE (TYPE(p) = Parking AND :includeParking = true)
               OR p.id IN (SELECT c.id FROM Course c JOIN c.tags t
                            WHERE c.approvalStatus = cmc.rodi.domain.place.entity.ApprovalStatus.APPROVED
                              AND c.deletedAt IS NULL
                              AND t IN :practiceTypes)
            """)
    List<Place> findAllVisibleFiltered(
            @Param("practiceTypes") List<PracticeType> practiceTypes,
            @Param("includeParking") boolean includeParking);

    /**
     * 뷰포트(bbox) 안의 place를 커서 페이징한다(ADR 0010, 스펙 019). 필터와 정렬을 한 쿼리에서 함께 처리한다.
     *
     * <ul>
     *   <li>필터 — {@code useFilter}면 {@link #MATCHES_FILTER}에 맞는 place만. 아니면 전부.
     *   <li>정렬 — {@code useSort}면 {@link #MATCHES_SORT}에 맞는 place가 먼저(matched=1). 아니면 모두
     *       matched=0이라 거리순과 같다.
     * </ul>
     *
     * 정렬 (matched DESC, distance ASC, id ASC), 커서 keyset은 (cursorMatched, cursorDistance, cursorId)
     * 초과분. 한 건 더 조회(limit=size+1)해 다음 페이지 존재를 판별한다. 쓰지 않는 기준의 목록은 빈 {@code IN ()}이 SQL 오류라 자리표시 값을
     * 넘긴다.
     */
    @Query(
            value =
                    """
                    SELECT * FROM (
                        SELECT p.id AS id,
                               p.place_type AS "placeType",
                               p.name AS name,
                               p.address AS address,
                               ST_Y(p.location) AS lat,
                               ST_X(p.location) AS lng,
                               ST_Distance(
                                   p.location::geography,
                                   ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)::geography) AS distance,
                               CASE WHEN :useSort AND """
                            + MATCHES_SORT
                            + """
                               THEN 1 ELSE 0 END AS matched
                        FROM place p
                        WHERE p.location && ST_MakeEnvelope(:swLng, :swLat, :neLng, :neLat, 4326)
                          AND """
                            + VISIBLE
                            + """
                          AND (:useFilter = false OR """
                            + MATCHES_FILTER
                            + """
                          )
                    ) t
                    WHERE (:cursorMatched IS NULL
                           OR t.matched < :cursorMatched
                           OR (t.matched = :cursorMatched AND t.distance > :cursorDistance)
                           OR (t.matched = :cursorMatched AND t.distance = :cursorDistance
                               AND t.id > :cursorId))
                    ORDER BY t.matched DESC, t.distance ASC, t.id ASC
                    LIMIT :limit
                    """,
            nativeQuery = true)
    List<PlaceListRow> findInViewport(
            @Param("swLat") double swLat,
            @Param("swLng") double swLng,
            @Param("neLat") double neLat,
            @Param("neLng") double neLng,
            @Param("lat") double lat,
            @Param("lng") double lng,
            @Param("useFilter") boolean useFilter,
            @Param("filterTypes") List<String> filterTypes,
            @Param("filterParking") int filterParking,
            @Param("useSort") boolean useSort,
            @Param("sortTypes") List<String> sortTypes,
            @Param("sortParking") int sortParking,
            @Param("cursorMatched") Integer cursorMatched,
            @Param("cursorDistance") Double cursorDistance,
            @Param("cursorId") Long cursorId,
            @Param("limit") int limit);

    /** 뷰포트 안 장소 수(totalCount, 첫 페이지 전용). 필터만 반영한다(정렬은 개수와 무관). */
    @Query(
            value =
                    """
                    SELECT COUNT(*) FROM place p
                    WHERE p.location && ST_MakeEnvelope(:swLng, :swLat, :neLng, :neLat, 4326)
                      AND """
                            + VISIBLE
                            + """
                      AND (:useFilter = false OR """
                            + MATCHES_FILTER
                            + """
                      )
                    """,
            nativeQuery = true)
    long countInViewport(
            @Param("swLat") double swLat,
            @Param("swLng") double swLng,
            @Param("neLat") double neLat,
            @Param("neLng") double neLng,
            @Param("useFilter") boolean useFilter,
            @Param("filterTypes") List<String> filterTypes,
            @Param("filterParking") int filterParking);

    /**
     * 키워드 검색(스펙 007). 주소(시군구) 또는 장소명 부분 일치. 전국 대상(bbox 없음) — 필터·정렬·커서 규칙은 {@link #findInViewport}와
     * 동일. 패턴은 이스케이프된 %kw% (ESCAPE '\'), 해당 컬럼이 null인 place는 그 컬럼으로 매칭되지 않는다.
     */
    @Query(
            value =
                    """
                    SELECT * FROM (
                        SELECT p.id AS id,
                               p.place_type AS "placeType",
                               p.name AS name,
                               p.address AS address,
                               ST_Y(p.location) AS lat,
                               ST_X(p.location) AS lng,
                               ST_Distance(
                                   p.location::geography,
                                   ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)::geography) AS distance,
                               CASE WHEN :useSort AND """
                            + MATCHES_SORT
                            + """
                               THEN 1 ELSE 0 END AS matched
                        FROM place p
                        WHERE (p.address ILIKE :pattern ESCAPE '\\'
                            OR p.name ILIKE :pattern ESCAPE '\\')
                          AND """
                            + VISIBLE
                            + """
                          AND (:useFilter = false OR """
                            + MATCHES_FILTER
                            + """
                          )
                    ) t
                    WHERE (:cursorMatched IS NULL
                           OR t.matched < :cursorMatched
                           OR (t.matched = :cursorMatched AND t.distance > :cursorDistance)
                           OR (t.matched = :cursorMatched AND t.distance = :cursorDistance
                               AND t.id > :cursorId))
                    ORDER BY t.matched DESC, t.distance ASC, t.id ASC
                    LIMIT :limit
                    """,
            nativeQuery = true)
    List<PlaceListRow> searchByKeyword(
            @Param("pattern") String pattern,
            @Param("lat") double lat,
            @Param("lng") double lng,
            @Param("useFilter") boolean useFilter,
            @Param("filterTypes") List<String> filterTypes,
            @Param("filterParking") int filterParking,
            @Param("useSort") boolean useSort,
            @Param("sortTypes") List<String> sortTypes,
            @Param("sortParking") int sortParking,
            @Param("cursorMatched") Integer cursorMatched,
            @Param("cursorDistance") Double cursorDistance,
            @Param("cursorId") Long cursorId,
            @Param("limit") int limit);

    /** 키워드 검색 총 건수(totalCount, 첫 페이지 전용). 필터만 반영한다. */
    @Query(
            value =
                    """
                    SELECT COUNT(*) FROM place p
                    WHERE (p.address ILIKE :pattern ESCAPE '\\'
                        OR p.name ILIKE :pattern ESCAPE '\\')
                      AND """
                            + VISIBLE
                            + """
                      AND (:useFilter = false OR """
                            + MATCHES_FILTER
                            + """
                      )
                    """,
            nativeQuery = true)
    long countByKeyword(
            @Param("pattern") String pattern,
            @Param("useFilter") boolean useFilter,
            @Param("filterTypes") List<String> filterTypes,
            @Param("filterParking") int filterParking);

    /**
     * 장소명 관련도 검색(스펙 009, 연관 검색어). place(코스+주차장) 중 name이 키워드를 포함하는 것을, 키워드가 이름에서 처음 나오는 위치(POSITION,
     * 1-based) 오름차순으로 — 앞에서 매칭될수록 관련도 높음 — 정렬한다. 커서 keyset은 (cursorMatchPos, cursorId) 초과분. 한 건 더
     * (limit=size+1) 조회해 다음 페이지 존재를 판별한다.
     */
    @Query(
            value =
                    """
                    SELECT * FROM (
                        SELECT p.id AS id,
                               p.name AS name,
                               p.address AS address,
                               POSITION(LOWER(:keyword) IN LOWER(p.name)) AS "matchPos"
                        FROM place p
                        WHERE p.name ILIKE :pattern ESCAPE '\\'
                          AND """
                            + VISIBLE
                            + """
                    ) t
                    WHERE (:cursorMatchPos IS NULL
                           OR t."matchPos" > :cursorMatchPos
                           OR (t."matchPos" = :cursorMatchPos AND t.id > :cursorId))
                    ORDER BY t."matchPos" ASC, t.id ASC
                    LIMIT :limit
                    """,
            nativeQuery = true)
    List<PlaceNameMatchRow> searchByNameRelevance(
            @Param("pattern") String pattern,
            @Param("keyword") String keyword,
            @Param("cursorMatchPos") Integer cursorMatchPos,
            @Param("cursorId") Long cursorId,
            @Param("limit") int limit);

    /** 장소명 관련도 검색 총 건수(totalCount, 첫 페이지 전용). */
    @Query(
            value =
                    """
                    SELECT COUNT(*) FROM place p
                    WHERE p.name ILIKE :pattern ESCAPE '\\'
                      AND """
                            + VISIBLE,
            nativeQuery = true)
    long countByNameRelevance(@Param("pattern") String pattern);
}
