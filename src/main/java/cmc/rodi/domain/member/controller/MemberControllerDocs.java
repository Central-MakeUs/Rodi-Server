package cmc.rodi.domain.member.controller;

import cmc.rodi.domain.member.dto.FilterTagsRequest;
import cmc.rodi.domain.member.dto.MemberUpdateRequest;
import cmc.rodi.domain.member.dto.MyPageResponse;
import cmc.rodi.domain.member.dto.OnboardingRequest;
import cmc.rodi.global.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

/** 회원 API의 Swagger 문서 스펙. 매핑·구현은 {@link MemberController}. */
@Tag(name = "Member", description = "회원")
public interface MemberControllerDocs {

    @Operation(
            summary = "마이페이지 조회",
            description =
                    "닉네임·레벨·레벨별 추천 태그(표시용 코드)·운전목표·저장한 장소 수를 반환한다. "
                            + "추천 태그는 레벨 고정 매핑(NAVIGATOR는 활동 태그). JWT 필요.")
    ApiResponse<MyPageResponse> getMyPage(@Parameter(hidden = true) Long memberId);

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

    @Operation(
            summary = "홈 정렬 필터 저장",
            description =
                    "홈 정렬 필터(연습유형 리스트)를 전체 교체 저장한다. 카테고리는 클라가 연습유형으로 풀어 보낸다. "
                            + "빈 배열이면 필터 해제(전체 노출). 저장된 값은 인증된 목록·검색 정렬에 적용된다. JWT 필요.")
    ApiResponse<Void> updateFilterTags(
            @Parameter(hidden = true) Long memberId, FilterTagsRequest request);

    @Operation(
            summary = "회원 차단",
            description =
                    "해당 회원을 차단한다(멱등). 차단하면 내 후기 목록에서 그 회원의 후기가 빠진다(단방향, 후기 요약 수치는 그대로). "
                            + "자기 자신은 400, 없는 회원은 404. JWT 필요.")
    ApiResponse<Void> block(
            @Parameter(description = "차단할 회원 id") Long memberId,
            @Parameter(hidden = true) Long currentMemberId);

    @Operation(summary = "회원 차단 해제", description = "차단을 해제한다(멱등, 차단 상태가 아니어도 200). JWT 필요.")
    ApiResponse<Void> unblock(
            @Parameter(description = "차단 해제할 회원 id") Long memberId,
            @Parameter(hidden = true) Long currentMemberId);
}
