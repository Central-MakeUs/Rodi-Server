package cmc.rodi.domain.place.dto;

import cmc.rodi.global.common.pagination.CursorPage;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/** 연관 검색어(스펙 009) 응답. 지역(regions)과 장소명(places)을 구분해 내려준다. */
public record RelatedSearchResponse(
        @Schema(description = "지역 후보(관련도순 최대 4개, 첫 페이지에서만 채움)") List<String> regions,
        @Schema(description = "장소명 후보(관련도순 커서 페이지)") CursorPage<PlaceSuggestion> places) {}
