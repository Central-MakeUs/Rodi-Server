package cmc.rodi.domain.place.dto;

import cmc.rodi.global.exception.BusinessException;
import cmc.rodi.global.exception.ErrorCode;

/**
 * 코스 검색(스펙 007) 요청 파라미터. 키워드(시군구 주소 일부) + 현위치(거리 정렬 기준) + 페이지 크기·커서. 공개 API라 잘못된 입력을 쿼리 전에 400으로
 * 막는다: 키워드는 트림 후 1~50자, 위도 [-90,90]·경도 [-180,180], size 1~100. 키워드는 트림해 보관한다.
 */
public record PlaceSearchRequest(String keyword, double lat, double lng, int size, String cursor) {

    private static final int MAX_KEYWORD_LENGTH = 50;
    private static final int MAX_SIZE = 100;

    public PlaceSearchRequest {
        keyword = keyword == null ? "" : keyword.trim();
        if (keyword.isEmpty() || keyword.length() > MAX_KEYWORD_LENGTH) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        if (size < 1 || size > MAX_SIZE) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        if (!(lat >= -90 && lat <= 90) || !(lng >= -180 && lng <= 180)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
    }

    /** LIKE 패턴( %kw% ). 키워드 안의 와일드카드(\ % _)는 이스케이프해 리터럴로 취급한다. */
    public String likePattern() {
        String escaped = keyword.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        return "%" + escaped + "%";
    }
}
