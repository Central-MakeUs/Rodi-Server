package cmc.rodi.domain.member.dto;

import cmc.rodi.domain.member.entity.Level;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;

/** 마이페이지 조회. 회원 프로필 + 레벨별 추천 태그 + 저장한 장소 수(place 도메인에서 조합) + 레벨 게이지. */
public record MyPageResponse(
        @Schema(description = "닉네임") String nickname,
        @Schema(
                        description = "다음 닉네임 변경 가능 시각(마지막 변경 + 30일). 지금 바꿀 수 있으면 null",
                        example = "2026-11-21T15:00:00")
                LocalDateTime nicknameChangeableAt,
        @Schema(description = "레벨") Level level,
        @Schema(
                        description = "레벨별 추천 태그(표시용 코드)",
                        example = "[\"U_TURN\",\"INTERSECTION\",\"PARKING\"]")
                List<String> recommendationTags,
        @Schema(description = "운전 목표(없으면 null)") String drivingGoal,
        @Schema(description = "저장한 장소 수") long savedPlaceCount,
        @Schema(description = "다음 레벨까지의 진행도(게이지)") LevelProgressResponse levelProgress) {}
