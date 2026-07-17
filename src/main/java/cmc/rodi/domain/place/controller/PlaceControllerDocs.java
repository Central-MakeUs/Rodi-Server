package cmc.rodi.domain.place.controller;

import cmc.rodi.domain.place.dto.CourseDetailResponse;
import cmc.rodi.domain.place.dto.PlaceCoordinateResponse;
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
            summary = "코스 상세",
            description =
                    "코스의 설명·주의사항·연습유형·경로점(순서)·주행거리와 북마크 수·현재 회원의 북마크 여부를 반환한다. "
                            + "placeId가 코스가 아니거나 없으면 404. JWT 필요.")
    ApiResponse<CourseDetailResponse> getCourseDetail(
            @Parameter(description = "코스 place id") Long placeId,
            @Parameter(hidden = true) Long memberId);
}
