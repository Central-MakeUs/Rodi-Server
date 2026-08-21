package cmc.rodi.domain.member.dto;

import cmc.rodi.domain.member.entity.RecentSearch;
import cmc.rodi.domain.member.entity.RecentSearchType;
import io.swagger.v3.oas.annotations.media.Schema;

/** 최근 검색어 응답 아이템(스펙 008). type으로 지역/장소 구분, PLACE는 placeId로 상세 직행. */
public record RecentSearchResponse(
        @Schema(description = "검색어 id(개별 삭제용)") Long id,
        @Schema(description = "종류(REGION 지역명 / PLACE 장소명)") RecentSearchType type,
        @Schema(description = "표시명", example = "서울특별시 강남구") String keyword,
        @Schema(description = "장소 id(PLACE만, REGION은 null)") Long placeId) {

    public static RecentSearchResponse from(RecentSearch recentSearch) {
        return new RecentSearchResponse(
                recentSearch.getId(),
                recentSearch.getType(),
                recentSearch.getKeyword(),
                recentSearch.getPlaceId());
    }
}
