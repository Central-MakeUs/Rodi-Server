package cmc.rodi.domain.place.dto;

import cmc.rodi.global.exception.BusinessException;
import cmc.rodi.global.exception.ErrorCode;

/**
 * 현위치 목록(#2) 요청 파라미터. 뷰포트(SW/NE) + 현위치(lat/lng) + 페이지 크기·커서. 공개 API라 잘못된 입력이 페이지네이션·공간쿼리에 닿기 전에
 * 400으로 막는다: size 1~100, 위도 [-90,90]·경도 [-180,180], SW는 NE 이하(위경도 범위 밖은 geography 캐스트에서 500이 남).
 */
public record PlaceListRequest(
        double swLat,
        double swLng,
        double neLat,
        double neLng,
        double lat,
        double lng,
        int size,
        String cursor) {

    private static final int MAX_SIZE = 100;

    public PlaceListRequest {
        if (size < 1 || size > MAX_SIZE) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        checkLat(swLat);
        checkLat(neLat);
        checkLat(lat);
        checkLng(swLng);
        checkLng(neLng);
        checkLng(lng);
        if (swLat > neLat || swLng > neLng) { // 남서 모서리는 북동 모서리 이하여야 함
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
    }

    private static void checkLat(double v) {
        if (!(v >= -90 && v <= 90)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
    }

    private static void checkLng(double v) {
        if (!(v >= -180 && v <= 180)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
    }
}
