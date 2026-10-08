package cmc.rodi.domain.place.dto;

import cmc.rodi.global.exception.BusinessException;
import cmc.rodi.global.exception.ErrorCode;

/**
 * 마커 클러스터 검색 범위(스펙 021) — 검색 시점 화면의 남서·북동 모서리. 위도는 Web Mercator 유효 범위(±85)로 막고, 남서는 북동보다 작아야 한다(넓이
 * 0인 범위는 단계 계산이 의미 없다). 날짜변경선을 넘는 범위는 지원하지 않는다.
 */
public record MarkerClusterRequest(double swLat, double swLng, double neLat, double neLng) {

    private static final double MAX_LAT = 85;

    public MarkerClusterRequest {
        checkLat(swLat);
        checkLat(neLat);
        checkLng(swLng);
        checkLng(neLng);
        if (!(swLat < neLat && swLng < neLng)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
    }

    private static void checkLat(double v) {
        if (!(v >= -MAX_LAT && v <= MAX_LAT)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
    }

    private static void checkLng(double v) {
        if (!(v >= -180 && v <= 180)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
    }
}
