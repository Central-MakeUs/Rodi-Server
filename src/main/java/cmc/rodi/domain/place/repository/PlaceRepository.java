package cmc.rodi.domain.place.repository;

import cmc.rodi.domain.place.entity.Place;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PlaceRepository extends JpaRepository<Place, Long> {

    /**
     * 뷰포트(bbox) 안의 place를 현위치 거리순으로 커서 페이징한다(ADR 0010). 정렬 (거리 ASC, id ASC), 커서 keyset은
     * (cursorDistance, cursorId) 초과분. 한 건 더 조회(limit=size+1)해 다음 페이지 존재를 판별한다.
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
                                   ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)::geography) AS distance
                        FROM place p
                        WHERE p.location && ST_MakeEnvelope(:swLng, :swLat, :neLng, :neLat, 4326)
                    ) t
                    WHERE (:cursorDistance IS NULL
                           OR t.distance > :cursorDistance
                           OR (t.distance = :cursorDistance AND t.id > :cursorId))
                    ORDER BY t.distance ASC, t.id ASC
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
            @Param("cursorDistance") Double cursorDistance,
            @Param("cursorId") Long cursorId,
            @Param("limit") int limit);

    /**
     * 키워드 검색(스펙 007). 주소(시군구) 또는 장소명 부분 일치. 전국 대상(bbox 없음), 현위치 거리순 커서 페이징 — 정렬·커서 규칙은 {@link
     * #findInViewport}와 동일. 패턴은 이스케이프된 %kw% (ESCAPE '\'), 해당 컬럼이 null인 place는 그 컬럼으로 매칭되지 않는다.
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
                                   ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)::geography) AS distance
                        FROM place p
                        WHERE p.address ILIKE :pattern ESCAPE '\\'
                           OR p.name ILIKE :pattern ESCAPE '\\'
                    ) t
                    WHERE (:cursorDistance IS NULL
                           OR t.distance > :cursorDistance
                           OR (t.distance = :cursorDistance AND t.id > :cursorId))
                    ORDER BY t.distance ASC, t.id ASC
                    LIMIT :limit
                    """,
            nativeQuery = true)
    List<PlaceListRow> searchByKeyword(
            @Param("pattern") String pattern,
            @Param("lat") double lat,
            @Param("lng") double lng,
            @Param("cursorDistance") Double cursorDistance,
            @Param("cursorId") Long cursorId,
            @Param("limit") int limit);

    /** 키워드 검색 총 건수(totalCount, 첫 페이지 전용). 주소 또는 장소명 부분 일치. */
    @Query(
            value =
                    """
                    SELECT COUNT(*) FROM place p
                    WHERE p.address ILIKE :pattern ESCAPE '\\'
                       OR p.name ILIKE :pattern ESCAPE '\\'
                    """,
            nativeQuery = true)
    long countByKeyword(@Param("pattern") String pattern);

    /**
     * 뷰포트(bbox) + 필터 매칭 우선 정렬(스펙 007). 정렬 (matched DESC, distance ASC, id ASC) — 매칭 place가 먼저, 비매칭도
     * 후순위로 전부 노출(숨김 없음). 커서 keyset은 (cursorMatched, cursorDistance, cursorId) 초과분. 코스는
     * course_practice_type 태그와 :practiceTypes 교집합, 주차장은 :parkingFlag(연습유형에 PARKING 포함 시 1)로 매칭.
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
                               CASE
                                   WHEN p.place_type = 'PARKING' THEN :parkingFlag
                                   WHEN EXISTS (
                                       SELECT 1 FROM course_practice_type cpt
                                       WHERE cpt.course_id = p.id
                                         AND cpt.practice_type IN (:practiceTypes)) THEN 1
                                   ELSE 0
                               END AS matched
                        FROM place p
                        WHERE p.location && ST_MakeEnvelope(:swLng, :swLat, :neLng, :neLat, 4326)
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
    List<PlaceListRow> findInViewportFiltered(
            @Param("swLat") double swLat,
            @Param("swLng") double swLng,
            @Param("neLat") double neLat,
            @Param("neLng") double neLng,
            @Param("lat") double lat,
            @Param("lng") double lng,
            @Param("practiceTypes") List<String> practiceTypes,
            @Param("parkingFlag") int parkingFlag,
            @Param("cursorMatched") Integer cursorMatched,
            @Param("cursorDistance") Double cursorDistance,
            @Param("cursorId") Long cursorId,
            @Param("limit") int limit);

    /**
     * 키워드(주소·장소명) + 필터 매칭 우선 정렬(스펙 007). 매칭·정렬·커서 규칙은 {@link #findInViewportFiltered}와 동일, 범위만 bbox
     * 대신 address/name ILIKE.
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
                               CASE
                                   WHEN p.place_type = 'PARKING' THEN :parkingFlag
                                   WHEN EXISTS (
                                       SELECT 1 FROM course_practice_type cpt
                                       WHERE cpt.course_id = p.id
                                         AND cpt.practice_type IN (:practiceTypes)) THEN 1
                                   ELSE 0
                               END AS matched
                        FROM place p
                        WHERE p.address ILIKE :pattern ESCAPE '\\'
                           OR p.name ILIKE :pattern ESCAPE '\\'
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
    List<PlaceListRow> searchByKeywordFiltered(
            @Param("pattern") String pattern,
            @Param("lat") double lat,
            @Param("lng") double lng,
            @Param("practiceTypes") List<String> practiceTypes,
            @Param("parkingFlag") int parkingFlag,
            @Param("cursorMatched") Integer cursorMatched,
            @Param("cursorDistance") Double cursorDistance,
            @Param("cursorId") Long cursorId,
            @Param("limit") int limit);

    /** 뷰포트 안 총 장소 수(totalCount). */
    @Query(
            value =
                    """
                    SELECT COUNT(*) FROM place p
                    WHERE p.location && ST_MakeEnvelope(:swLng, :swLat, :neLng, :neLat, 4326)
                    """,
            nativeQuery = true)
    long countInViewport(
            @Param("swLat") double swLat,
            @Param("swLng") double swLng,
            @Param("neLat") double neLat,
            @Param("neLng") double neLng);

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
                    """,
            nativeQuery = true)
    long countByNameRelevance(@Param("pattern") String pattern);
}
