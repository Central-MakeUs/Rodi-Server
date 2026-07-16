package cmc.rodi.domain.place.dto;

import cmc.rodi.domain.member.entity.PracticeType;
import cmc.rodi.domain.place.entity.PlaceType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/** 현위치 목록 아이템. 공통 필드 + 코스 전용(tags·distanceMeters, 주차장은 null). */
public record PlaceListItem(
        @Schema(description = "장소 id") Long id,
        @Schema(description = "유형") PlaceType type,
        @Schema(description = "장소명") String name,
        @Schema(description = "설명") String description,
        @Schema(description = "위도") double lat,
        @Schema(description = "경도") double lng,
        @Schema(description = "현위치까지 거리(m)") long distanceFromMe,
        @Schema(description = "연습 태그(코스만, 주차장은 null)") List<PracticeType> tags,
        @Schema(description = "코스 주행거리(m)(코스만)") Integer distanceMeters) {}
