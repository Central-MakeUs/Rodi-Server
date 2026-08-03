package cmc.rodi.domain.place.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** 연관 검색어(스펙 009)의 장소명 후보. 검색 결과 목록(PlaceListItem)과 달리 자동완성용 최소 정보만 담는다. */
public record PlaceSuggestion(
        @Schema(description = "장소 id") Long placeId,
        @Schema(description = "장소명") String name,
        @Schema(description = "시군구 단위 주소", example = "서울특별시 강남구") String region) {}
