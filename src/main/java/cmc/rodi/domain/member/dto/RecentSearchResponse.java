package cmc.rodi.domain.member.dto;

import cmc.rodi.domain.member.entity.RecentSearch;
import io.swagger.v3.oas.annotations.media.Schema;

/** 최근 검색어 응답 아이템(스펙 008). 개별 삭제에 쓰도록 id를 함께 준다. */
public record RecentSearchResponse(
        @Schema(description = "검색어 id(개별 삭제용)") Long id,
        @Schema(description = "검색 키워드", example = "강남") String keyword) {

    public static RecentSearchResponse from(RecentSearch recentSearch) {
        return new RecentSearchResponse(recentSearch.getId(), recentSearch.getKeyword());
    }
}
