package cmc.rodi.domain.member.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

/** 코스 등록 튜토리얼 완료 저장 결과(스펙 017). */
public record CourseTutorialCompletionResponse(
        @Schema(description = "코스 등록 튜토리얼 최초 완료 시각") LocalDateTime courseTutorialCompletedAt) {}
