package cmc.rodi.domain.place.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * 마커 클러스터 검색 결과(스펙 021). 검색 범위의 장소와, 확대 단계별 묶음을 한 번에 준다. 앱은 확대·축소할 때 서버에 묻지 않고 지금 화면의 경도 폭({@code
 * 동쪽 경도 − 서쪽 경도})이 {@code minLngSpan} 이상 {@code maxLngSpan} 미만인 단계를 골라 그린다.
 *
 * <p>칸 번호·칸 크기 같은 그리드 전용 값은 넣지 않는다(ADR 0015) — 서버의 묶는 방식이 바뀌어도 앱은 그대로다.
 */
public record MarkerClusterResponse(
        @Schema(description = "검색 범위의 장소 전체(필터 적용), id 오름차순. 단계에서는 id로 참조한다")
                List<PlaceCoordinateResponse> places,
        @Schema(description = "확대 단계. 넓은 화면용 → 좁은 화면용. 경도 폭 범위가 빈틈없이 이어진다") List<Level> levels) {

    public record Level(
            @Schema(description = "이 단계를 쓰는 화면 경도 폭의 하한(이상)", example = "0.2602") double minLngSpan,
            @Schema(
                            description = "이 단계를 쓰는 화면 경도 폭의 상한(미만). null이면 상한 없음(가장 넓은 단계)",
                            example = "0.5204")
                    Double maxLngSpan,
            @Schema(description = "클러스터. 위도 내림차순 → 경도 오름차순") List<Cluster> clusters,
            @Schema(description = "이 단계에서 개별로 그릴 장소 id(places에서 찾는다), 오름차순") List<Long> placeIds) {}

    public record Cluster(
            @Schema(description = "그릴 위치 — 묶인 장소들의 중심점(칸 가장자리 쪽이면 안쪽으로 당김)", example = "37.552")
                    double lat,
            @Schema(description = "그릴 위치 경도", example = "126.921") double lng,
            @Schema(description = "묶인 장소 수(2 이상)", example = "12") int count,
            @Schema(description = "탭하면 카메라를 맞출 범위(묶인 장소들을 감싸는 영역)") Bounds bounds) {}

    public record Bounds(
            @Schema(example = "37.511") double swLat,
            @Schema(example = "126.884") double swLng,
            @Schema(example = "37.583") double neLat,
            @Schema(example = "126.968") double neLng) {}
}
