package cmc.rodi.domain.member.controller;

import cmc.rodi.domain.member.dto.RecentSearchResponse;
import cmc.rodi.domain.member.service.RecentSearchService;
import cmc.rodi.global.auth.resolver.CurrentMember;
import cmc.rodi.global.common.response.ApiResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 최근 검색어 API. 저장은 검색 API가 자동 처리하고, 여기선 조회·삭제만 제공한다. 문서 스펙은 {@link RecentSearchControllerDocs}. */
@RestController
@RequestMapping("/api/v1/members/me/recent-searches")
@RequiredArgsConstructor
public class RecentSearchController implements RecentSearchControllerDocs {

    private final RecentSearchService recentSearchService;

    @Override
    @GetMapping
    public ApiResponse<List<RecentSearchResponse>> getRecentSearches(@CurrentMember Long memberId) {
        return ApiResponse.success(recentSearchService.getRecent(memberId));
    }

    @Override
    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteRecentSearch(
            @PathVariable Long id, @CurrentMember Long memberId) {
        recentSearchService.delete(memberId, id);
        return ApiResponse.success(null);
    }

    @Override
    @DeleteMapping
    public ApiResponse<Void> deleteAllRecentSearches(@CurrentMember Long memberId) {
        recentSearchService.deleteAll(memberId);
        return ApiResponse.success(null);
    }
}
