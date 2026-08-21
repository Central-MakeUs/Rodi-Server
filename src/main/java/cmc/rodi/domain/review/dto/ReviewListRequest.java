package cmc.rodi.domain.review.dto;

import cmc.rodi.domain.member.entity.Level;
import cmc.rodi.global.exception.BusinessException;
import cmc.rodi.global.exception.ErrorCode;

/**
 * 후기 목록·요약 요청 파라미터. {@code level}은 생략하면 조회자 본인 레벨, {@code ALL}이면 전체, 그 외에는 해당 레벨로 필터한다. 잘못된 레벨
 * 문자열·size 범위 밖은 400으로 막는다.
 */
public record ReviewListRequest(String level, int size, String cursor) {

    private static final int MAX_SIZE = 100;
    public static final String ALL_LEVELS = "ALL";

    public ReviewListRequest {
        if (size < 1 || size > MAX_SIZE) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
    }

    /** 요약은 페이지네이션이 없어 level만 검증하면 된다. */
    public static ReviewListRequest ofLevel(String level) {
        return new ReviewListRequest(level, 1, null);
    }

    /** 전체 레벨 요청인지(level=ALL). */
    public boolean allLevels() {
        return ALL_LEVELS.equalsIgnoreCase(level);
    }

    /** 지정된 레벨. 생략(null)이면 null을 돌려주며, 이때 필터 기준은 호출부가 조회자 레벨로 채운다. 유효하지 않은 레벨 문자열은 400. */
    public Level explicitLevel() {
        if (level == null || level.isBlank() || allLevels()) {
            return null;
        }
        try {
            return Level.valueOf(level.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, e);
        }
    }
}
