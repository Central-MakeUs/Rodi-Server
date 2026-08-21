package cmc.rodi.domain.place.dto;

import cmc.rodi.global.exception.BusinessException;
import cmc.rodi.global.exception.ErrorCode;

/**
 * 연관 검색어(스펙 009) 요청 파라미터. 키워드 + 장소 목록 페이지 크기·커서. 지역(regions)은 관련도순 상한 4개 고정이라 페이지네이션이 없다. 좌표는 지역
 * 대표좌표 확보 전까지 미사용이라 받지 않는다(추후 거리정렬 도입 시 옵셔널로 추가).
 */
public record RelatedSearchRequest(String keyword, int size, String cursor) {

    private static final int MAX_KEYWORD_LENGTH = 50;
    private static final int MAX_SIZE = 100;

    public RelatedSearchRequest {
        keyword = keyword == null ? "" : keyword.trim();
        if (keyword.isEmpty() || keyword.length() > MAX_KEYWORD_LENGTH) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        if (size < 1 || size > MAX_SIZE) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
    }

    /** LIKE 패턴( %kw% ). 키워드 안의 와일드카드(\ % _)는 이스케이프해 리터럴로 취급한다. */
    public String likePattern() {
        String escaped = keyword.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        return "%" + escaped + "%";
    }
}
