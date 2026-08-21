package cmc.rodi.domain.member.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

/**
 * 회원 부분 수정 요청(마이페이지). 현재 수정 가능 필드는 운전 목표뿐이다. 빈 문자열/null이면 목표를 지운다(빈값 허용). 향후 수정 필드가 늘면 여기에 추가한다.
 */
public record MemberUpdateRequest(
        @Schema(description = "운전 목표(최대 30자, 빈값이면 목표 삭제)", example = "골목길에 익숙해지기") @Size(max = 30)
                String drivingGoal) {}
