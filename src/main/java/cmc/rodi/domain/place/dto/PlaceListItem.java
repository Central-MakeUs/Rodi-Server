package cmc.rodi.domain.place.dto;

import cmc.rodi.domain.member.entity.PracticeType;
import cmc.rodi.domain.place.entity.PlaceType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/** 현위치 목록 아이템. 공통 필드 + 코스 전용(description·distanceMeters) + 주차장 전용(capacity·openTime). */
public record PlaceListItem(
        @Schema(description = "장소 id") Long id,
        @Schema(description = "유형") PlaceType type,
        @Schema(description = "장소명") String name,
        @Schema(description = "시군구 단위 주소", example = "서울특별시 강남구") String address,
        @Schema(description = "위도") double lat,
        @Schema(description = "경도") double lng,
        @Schema(description = "현위치까지 거리(m)") long distanceFromMe,
        @Schema(description = "연습 유형(코스=태그들, 주차장=[PARKING])") List<PracticeType> practiceTypes,
        @Schema(description = "설명(코스만)") String description,
        @Schema(description = "코스 주행거리(m)(코스만)") Integer distanceMeters,
        @Schema(description = "총 주차면수(주차장만)") Integer capacity,
        @Schema(description = "영업시작 시각(주차장만)", example = "00:00") String openTime) {}
