package cmc.rodi.domain.place.controller;

import cmc.rodi.domain.member.service.RecentSearchService;
import cmc.rodi.domain.place.dto.PlaceCoordinateResponse;
import cmc.rodi.domain.place.dto.PlaceDetailResponse;
import cmc.rodi.domain.place.dto.PlaceListItem;
import cmc.rodi.domain.place.dto.PlaceListRequest;
import cmc.rodi.domain.place.dto.PlaceSearchRequest;
import cmc.rodi.domain.place.service.BookmarkQueryService;
import cmc.rodi.domain.place.service.BookmarkService;
import cmc.rodi.domain.place.service.PlaceQueryService;
import cmc.rodi.global.auth.resolver.CurrentMember;
import cmc.rodi.global.common.pagination.CursorPage;
import cmc.rodi.global.common.response.ApiResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 장소 API. 문서 스펙은 {@link PlaceControllerDocs}. */
@RestController
@RequestMapping("/api/v1/places")
@RequiredArgsConstructor
@Slf4j
public class PlaceController implements PlaceControllerDocs {

    private final PlaceQueryService placeQueryService;
    private final BookmarkService bookmarkService;
    private final BookmarkQueryService bookmarkQueryService;
    private final RecentSearchService recentSearchService;

    @Override
    @GetMapping("/coordinates")
    public ApiResponse<List<PlaceCoordinateResponse>> getCoordinates() {
        return ApiResponse.success(placeQueryService.getAllCoordinates());
    }

    @Override
    @GetMapping
    public ApiResponse<CursorPage<PlaceListItem>> getPlaces(
            @RequestParam double swLat,
            @RequestParam double swLng,
            @RequestParam double neLat,
            @RequestParam double neLng,
            @RequestParam double lat,
            @RequestParam double lng,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String cursor,
            @CurrentMember(required = false) Long memberId) {
        return ApiResponse.success(
                placeQueryService.getPlaces(
                        new PlaceListRequest(swLat, swLng, neLat, neLng, lat, lng, size, cursor),
                        memberId));
    }

    @Override
    @GetMapping("/search")
    public ApiResponse<CursorPage<PlaceListItem>> searchPlaces(
            @RequestParam String keyword,
            @RequestParam double lat,
            @RequestParam double lng,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String cursor,
            @CurrentMember Long memberId) {
        PlaceSearchRequest request = new PlaceSearchRequest(keyword, lat, lng, size, cursor);
        CursorPage<PlaceListItem> result = placeQueryService.searchPlaces(request, memberId);
        if (cursor == null) { // 첫 페이지에서만 최근 검색어 기록(페이지네이션 재조회로 중복 기록 방지)
            try {
                recentSearchService.record(memberId, request.keyword());
            } catch (Exception e) {
                // 최근 검색어 기록은 부수효과 — 실패해도 검색 결과 응답은 그대로 반환한다.
                log.warn("최근 검색어 기록 실패(검색 응답엔 영향 없음): {}", e.getClass().getSimpleName());
            }
        }
        return ApiResponse.success(result);
    }

    @Override
    @GetMapping("/bookmarks")
    public ApiResponse<CursorPage<PlaceListItem>> getSavedPlaces(
            @CurrentMember Long memberId,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String cursor) {
        return ApiResponse.success(bookmarkQueryService.getSavedPlaces(memberId, size, cursor));
    }

    @Override
    @GetMapping("/{placeId}")
    public ApiResponse<PlaceDetailResponse> getPlaceDetail(
            @PathVariable Long placeId, @CurrentMember Long memberId) {
        return ApiResponse.success(placeQueryService.getPlaceDetail(placeId, memberId));
    }

    @Override
    @PostMapping("/{placeId}/bookmark")
    public ApiResponse<Void> bookmark(@PathVariable Long placeId, @CurrentMember Long memberId) {
        bookmarkService.bookmark(placeId, memberId);
        return ApiResponse.success(null);
    }

    @Override
    @DeleteMapping("/{placeId}/bookmark")
    public ApiResponse<Void> unbookmark(@PathVariable Long placeId, @CurrentMember Long memberId) {
        bookmarkService.unbookmark(placeId, memberId);
        return ApiResponse.success(null);
    }
}
