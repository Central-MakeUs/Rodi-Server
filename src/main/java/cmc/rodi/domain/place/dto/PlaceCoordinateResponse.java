package cmc.rodi.domain.place.dto;

import cmc.rodi.domain.place.entity.Place;
import cmc.rodi.domain.place.entity.PlaceType;
import io.swagger.v3.oas.annotations.media.Schema;
import org.locationtech.jts.geom.Point;

/** 지도 마커용 간단 좌표. 전체 place 목록(API#1)의 아이템. */
public record PlaceCoordinateResponse(
        @Schema(description = "장소 id") Long id,
        @Schema(description = "유형") PlaceType type,
        @Schema(description = "장소명") String name,
        @Schema(description = "시군구 단위 주소", example = "서울특별시 강남구") String address,
        @Schema(description = "위도") double lat,
        @Schema(description = "경도") double lng) {

    public static PlaceCoordinateResponse from(Place place) {
        Point location = place.getLocation();
        // PostGIS는 x=경도, y=위도 → lat=getY(), lng=getX()
        return new PlaceCoordinateResponse(
                place.getId(),
                place.getPlaceType(),
                place.getName(),
                place.getAddress(),
                location.getY(),
                location.getX());
    }
}
