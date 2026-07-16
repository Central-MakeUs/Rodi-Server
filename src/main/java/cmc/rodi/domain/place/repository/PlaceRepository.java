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
                               p.description AS description,
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
}
