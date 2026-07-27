package cmc.rodi.domain.member.controller;

import cmc.rodi.domain.member.dto.RecentSearchResponse;
import cmc.rodi.global.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;

/** 최근 검색어 API의 Swagger 문서 스펙. 매핑·구현은 {@link RecentSearchController}. */
@Tag(name = "RecentSearch", description = "최근 검색어")
public interface RecentSearchControllerDocs {

    @Operation(
            summary = "최근 검색어 조회",
            description =
                    "현재 회원의 최근 검색어를 최신순으로 반환한다(상한 15개 이내). 저장은 검색 API가 자동으로 하므로 저장 엔드포인트는 없다. JWT 필요.")
    ApiResponse<List<RecentSearchResponse>> getRecentSearches(
            @Parameter(hidden = true) Long memberId);

    @Operation(
            summary = "최근 검색어 개별 삭제",
            description = "검색어 하나를 삭제한다. 본인 것이 아니거나 없는 id면 삭제 없이 멱등 200. JWT 필요.")
    ApiResponse<Void> deleteRecentSearch(
            @Parameter(description = "검색어 id") Long id, @Parameter(hidden = true) Long memberId);

    @Operation(
            summary = "최근 검색어 전체 삭제",
            description = "현재 회원의 최근 검색어를 모두 삭제한다(없어도 멱등 200). JWT 필요.")
    ApiResponse<Void> deleteAllRecentSearches(@Parameter(hidden = true) Long memberId);
}
