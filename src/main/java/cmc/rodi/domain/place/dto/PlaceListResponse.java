package cmc.rodi.domain.place.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/** 현위치 목록 응답. totalCount(bbox 총계, 클러스터 대조용) + 아이템 + 다음 커서(null=마지막). */
public record PlaceListResponse(
        @Schema(description = "뷰포트 내 총 장소 수") long totalCount,
        List<PlaceListItem> items,
        @Schema(description = "다음 페이지 커서(null=마지막)") String nextCursor) {}
