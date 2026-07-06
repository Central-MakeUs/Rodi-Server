package cmc.rodi.domain.member.controller;

import cmc.rodi.global.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

/** 회원 API의 Swagger 문서 스펙. 매핑·구현은 {@link MemberController}. */
@Tag(name = "Member", description = "회원")
public interface MemberControllerDocs {

    @Operation(
            summary = "회원 탈퇴 요청",
            description =
                    "탈퇴를 요청한다(Day 0, soft delete). 서버 세션(refresh)이 전체 폐기된다. "
                            + "유예기간(3일) 내 동일 소셜 재로그인 시 복구 안내를 받는다.")
    ApiResponse<Void> withdraw(@Parameter(hidden = true) Long memberId);
}
