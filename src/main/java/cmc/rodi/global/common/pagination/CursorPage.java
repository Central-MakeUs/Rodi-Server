package cmc.rodi.global.common.pagination;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * 커서 페이지네이션 공통 응답(ADR 0010). 장소·북마크·리뷰 등 무한 스크롤 목록에서 재사용한다. totalCount는 매 페이지 count 쿼리를 피하려고 첫
 * 페이지에서만 채우고(cursor 없음), 이후 페이지는 null이다.
 */
public record CursorPage<T>(
        List<T> items,
        @Schema(description = "다음 페이지 존재 여부") boolean hasNext,
        @Schema(description = "다음 페이지 커서(hasNext=false면 null)") String nextCursor,
        @Schema(description = "전체 개수(첫 페이지에서만 채움, 이후 null)") Long totalCount) {

    /** 첫 페이지: totalCount 포함. */
    public static <T> CursorPage<T> first(
            List<T> items, boolean hasNext, String nextCursor, long totalCount) {
        return new CursorPage<>(items, hasNext, nextCursor, totalCount);
    }

    /** 다음 페이지: totalCount 생략(null). */
    public static <T> CursorPage<T> next(List<T> items, boolean hasNext, String nextCursor) {
        return new CursorPage<>(items, hasNext, nextCursor, null);
    }
}
