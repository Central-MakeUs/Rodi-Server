package cmc.rodi.domain.place.dto;

/** 현위치 목록(#2) 요청 파라미터. 뷰포트(SW/NE) + 현위치(lat/lng) + 페이지 크기·커서. */
public record PlaceListRequest(
        double swLat,
        double swLng,
        double neLat,
        double neLng,
        double lat,
        double lng,
        int size,
        String cursor) {}
