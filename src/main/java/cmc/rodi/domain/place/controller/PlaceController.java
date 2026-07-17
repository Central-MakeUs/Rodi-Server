package cmc.rodi.domain.place.controller;

import cmc.rodi.domain.place.dto.PlaceCoordinateResponse;
import cmc.rodi.domain.place.dto.PlaceDetailResponse;
import cmc.rodi.domain.place.dto.PlaceListItem;
import cmc.rodi.domain.place.dto.PlaceListRequest;
import cmc.rodi.domain.place.service.BookmarkService;
import cmc.rodi.domain.place.service.PlaceQueryService;
import cmc.rodi.global.auth.resolver.CurrentMember;
import cmc.rodi.global.common.pagination.CursorPage;
import cmc.rodi.global.common.response.ApiResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
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
public class PlaceController implements PlaceControllerDocs {

    private final PlaceQueryService placeQueryService;
    private final BookmarkService bookmarkService;

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
            @RequestParam(required = false) String cursor) {
        return ApiResponse.success(
                placeQueryService.getPlaces(
                        new PlaceListRequest(swLat, swLng, neLat, neLng, lat, lng, size, cursor)));
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
