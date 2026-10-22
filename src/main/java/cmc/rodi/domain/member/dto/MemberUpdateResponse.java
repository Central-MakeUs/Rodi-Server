package cmc.rodi.domain.member.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

/** 회원 부분 수정 결과(스펙 020) — 반영된 닉네임과 다음 닉네임 변경 가능 시각. */
public record MemberUpdateResponse(
        @Schema(description = "현재 닉네임(수정 반영 후)", example = "운전하는_고양이") String nickname,
        @Schema(
                        description = "다음 닉네임 변경 가능 시각(바꾼 시각 + 30일). 지금 바꿀 수 있으면 null",
                        example = "2026-11-21T15:00:00")
                LocalDateTime nicknameChangeableAt) {}
