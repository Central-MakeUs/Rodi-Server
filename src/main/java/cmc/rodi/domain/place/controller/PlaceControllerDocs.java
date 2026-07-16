package cmc.rodi.domain.place.controller;

import cmc.rodi.domain.place.dto.PlaceCoordinateResponse;
import cmc.rodi.global.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;

/** 장소 API의 Swagger 문서 스펙. 매핑·구현은 {@link PlaceController}. */
@Tag(name = "Place", description = "장소·코스·주차장")
public interface PlaceControllerDocs {

    @Operation(
            summary = "장소 좌표 목록(마커)",
            description = "전체 장소의 간단 좌표를 반환한다. 지도 마커·클라이언트 클러스터링용. 공개.")
    ApiResponse<List<PlaceCoordinateResponse>> getCoordinates();
}
