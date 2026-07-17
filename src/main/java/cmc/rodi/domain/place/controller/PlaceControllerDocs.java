package cmc.rodi.domain.place.controller;

import cmc.rodi.domain.place.dto.PlaceCoordinateResponse;
import cmc.rodi.domain.place.dto.PlaceDetailResponse;
import cmc.rodi.domain.place.dto.PlaceListItem;
import cmc.rodi.global.common.pagination.CursorPage;
import cmc.rodi.global.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;

/** 장소 API의 Swagger 문서 스펙. 매핑·구현은 {@link PlaceController}. */
@Tag(name = "Place", description = "장소·코스·주차장")
public interface PlaceControllerDocs {

    @Operation(
            summary = "장소 좌표 목록(마커)",
            description = "전체 장소의 간단 좌표를 반환한다. 지도 마커·클라이언트 클러스터링용. 공개.")
    ApiResponse<List<PlaceCoordinateResponse>> getCoordinates();

    @Operation(
            summary = "현위치 장소 목록",
            description =
                    "지도 뷰포트(남서/북동) 안의 place(코스+주차장)를 현위치 거리순으로 커서 페이징한다. "
                            + "코스는 태그·주행거리 포함, 주차장은 공통 필드만. totalCount는 뷰포트 총계. 공개.")
    ApiResponse<CursorPage<PlaceListItem>> getPlaces(
            @Parameter(description = "남서 위도") double swLat,
            @Parameter(description = "남서 경도") double swLng,
            @Parameter(description = "북동 위도") double neLat,
            @Parameter(description = "북동 경도") double neLng,
            @Parameter(description = "현위치 위도") double lat,
            @Parameter(description = "현위치 경도") double lng,
            @Parameter(description = "페이지 크기(기본 20)") int size,
            @Parameter(description = "다음 페이지 커서(없으면 첫 페이지)") String cursor);

    @Operation(
            summary = "장소 상세(코스·주차장 통합)",
            description =
                    "placeId가 타입을 결정하므로 엔드포인트를 나누지 않는다. 공통 필드(이름·주소·좌표·북마크 수/여부)에 더해 "
                            + "type=COURSE면 course 블록(설명·주의사항·연습유형·주행거리·경로점), "
                            + "type=PARKING이면 parking 블록(주소·주차면수·요금·영업시간)을 채워 반환한다. "
                            + "해당 없는 블록은 null. 없는 id면 404. JWT 필요.")
    ApiResponse<PlaceDetailResponse> getPlaceDetail(
            @Parameter(description = "장소 id(코스·주차장 공통)") Long placeId,
            @Parameter(hidden = true) Long memberId);

    @Operation(
            summary = "북마크 저장",
            description = "장소를 북마크한다(코스·주차장 공통). 멱등 — 이미 저장돼 있어도 200. 없는 장소면 404. JWT 필요.")
    ApiResponse<Void> bookmark(
            @Parameter(description = "장소 id") Long placeId,
            @Parameter(hidden = true) Long memberId);

    @Operation(summary = "북마크 해제", description = "장소 북마크를 해제한다. 멱등 — 북마크가 없어도 200. JWT 필요.")
    ApiResponse<Void> unbookmark(
            @Parameter(description = "장소 id") Long placeId,
            @Parameter(hidden = true) Long memberId);
}
