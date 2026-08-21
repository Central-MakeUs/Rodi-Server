package cmc.rodi.domain.member.dto;

import cmc.rodi.domain.member.entity.Member;
import cmc.rodi.global.common.validation.GraphemeSize;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 회원 부분 수정 요청(마이페이지). 현재 수정 가능 필드는 운전 목표뿐이다. 빈 문자열/null이면 목표를 지운다(빈값 허용). 향후 수정 필드가 늘면 여기에 추가한다.
 */
public record MemberUpdateRequest(
        @Schema(
                        description = "운전 목표(빈값이면 목표 삭제). 최대 30자 — 이모지·자모 포함 사용자가 보는 글자 수 기준",
                        example = "골목길에 익숙해지기")
                @GraphemeSize(max = Member.DRIVING_GOAL_MAX_LENGTH)
                String drivingGoal) {}
