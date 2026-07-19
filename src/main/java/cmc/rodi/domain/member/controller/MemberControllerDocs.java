package cmc.rodi.domain.member.controller;

import cmc.rodi.domain.member.dto.MemberUpdateRequest;
import cmc.rodi.domain.member.dto.OnboardingRequest;
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

    @Operation(
            summary = "회원 정보 수정(마이페이지)",
            description = "회원 정보를 부분 수정한다. 현재 수정 가능 필드는 운전 목표(최대 30자, 빈값이면 삭제). JWT 필요.")
    ApiResponse<Void> updateMe(
            @Parameter(hidden = true) Long memberId, MemberUpdateRequest request);

    @Operation(
            summary = "온보딩 제출",
            description =
                    "운전 경험·추가 정보를 한 번에 제출한다. 레벨은 클라이언트가 변환해 보낸 값을 저장한다. "
                            + "저장만 하며 응답 데이터는 없다(추천유형·레벨은 클라이언트 로컬 값). 이미 온보딩한 회원은 409.")
    ApiResponse<Void> submitOnboarding(
            @Parameter(hidden = true) Long memberId, OnboardingRequest request);
}
