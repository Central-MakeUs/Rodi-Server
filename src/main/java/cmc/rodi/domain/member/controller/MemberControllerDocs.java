package cmc.rodi.domain.member.controller;

import cmc.rodi.domain.member.dto.BlockedMemberItem;
import cmc.rodi.domain.member.dto.CourseTutorialCompletionResponse;
import cmc.rodi.domain.member.dto.FilterTagsRequest;
import cmc.rodi.domain.member.dto.MemberUpdateRequest;
import cmc.rodi.domain.member.dto.MyPageResponse;
import cmc.rodi.domain.member.dto.NicknameAvailabilityResponse;
import cmc.rodi.domain.member.dto.OnboardingFormResponse;
import cmc.rodi.domain.member.dto.OnboardingRequest;
import cmc.rodi.domain.member.dto.SortTagsRequest;
import cmc.rodi.global.common.pagination.CursorPage;
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
            description =
                    "회원 정보를 부분 수정한다. 보낸 필드만 바꾸고 생략한 필드는 그대로 둔다. "
                            + "운전 목표: 최대 30자, null·빈값이면 삭제. "
                            + "닉네임: 사용 가능 여부 확인 API와 같은 판정 — 위반 시 400 MEMBER_400_2(길이)·MEMBER_400_3(문자)·"
                            + "MEMBER_400_4(금칙어), 중복이면 409 MEMBER_409_3. 하나라도 실패하면 아무것도 저장하지 않는다. JWT 필요.")
    ApiResponse<Void> updateMe(
            @Parameter(hidden = true) Long memberId, MemberUpdateRequest request);

    @Operation(
            summary = "온보딩 폼 조회",
            description =
                    "온보딩 화면 정의를 반환한다. 단계(steps) → 문항(questions) → 선택지(options) 구조이고, 문항 code는 "
                            + "온보딩 제출 요청의 필드명, 선택지 code는 제출 값이다. 2단계는 건너뛸 수 있다(skippable). "
                            + "흐름 규칙: skipsFollowUps 선택지를 고르면 후속 문항 생략, showWhen 문항은 조건 선택지를 골랐을 때만 노출, "
                            + "exclusive 선택지는 단독으로만. JWT 필요.")
    ApiResponse<OnboardingFormResponse> getOnboardingForm();

    @Operation(
            summary = "닉네임 사용 가능 여부 확인",
            description =
                    "저장하지 않고 닉네임 변경과 같은 판정만 한다. 규칙 위반도 200으로 내리고 available=false와 사유 코드(reason)를 준다 — "
                            + "MEMBER_400_2 길이(2~10자) · MEMBER_400_3 문자(한글·영문·숫자·_, _는 처음·끝·연속 불가) · "
                            + "MEMBER_400_4 금칙어 · MEMBER_409_3 중복(영문 대소문자 무시). 본인 현재 닉네임은 사용 가능. "
                            + "확인 뒤 다른 회원이 먼저 쓸 수 있으므로 최종 판정은 변경 요청이 한다. JWT 필요.")
    ApiResponse<NicknameAvailabilityResponse> checkNicknameAvailability(
            @Parameter(hidden = true) Long memberId,
            @Parameter(description = "확인할 닉네임(앞뒤 공백은 제거 후 판정)", example = "초보_탈출") String nickname);

    @Operation(
            summary = "온보딩 제출",
            description =
                    "운전 경험·추가 정보를 한 번에 제출한다. 레벨은 클라이언트가 변환해 보낸 값을 저장한다. "
                            + "저장만 하며 응답 데이터는 없다(추천유형·레벨은 클라이언트 로컬 값). 이미 온보딩한 회원은 409.")
    ApiResponse<Void> submitOnboarding(
            @Parameter(hidden = true) Long memberId, OnboardingRequest request);

    @Operation(
            summary = "홈 정렬 기준 저장",
            description =
                    "홈 정렬 기준(연습유형 리스트)을 전체 교체 저장한다. 카테고리는 클라가 연습유형으로 풀어 보낸다. "
                            + "저장된 값은 인증된 목록·검색에서 매칭 장소를 앞에 두는 정렬에만 쓰인다(필터 아님 — 필터는 "
                            + "목록·검색·마커의 practiceTypes 파라미터). 빈 배열이면 해제(거리순). JWT 필요.")
    ApiResponse<Void> updateSortTags(
            @Parameter(hidden = true) Long memberId, SortTagsRequest request);

    @Operation(
            summary = "홈 정렬 기준 저장(옛 경로)",
            deprecated = true,
            description =
                    "PUT /members/me/sort-tags의 옛 경로. 본문 필드명만 filterTags이고 동작은 같다(sort_tags에 저장). "
                            + "앱이 새 경로로 바뀌면 제거한다. JWT 필요.")
    ApiResponse<Void> updateFilterTags(
            @Parameter(hidden = true) Long memberId, FilterTagsRequest request);

    @Operation(
            summary = "코스 등록 튜토리얼 완료 저장",
            description =
                    "코스 등록 튜토리얼을 완료한 최초 시각을 저장한다. 요청 본문은 없고, 여러 번 호출해도 최초 완료 시각을 유지한다. "
                            + "완료 여부 조회 전용 API는 만들지 않으며 로그인·토큰 재발급 응답의 isCourseTutorialCompleted로 판단한다.")
    ApiResponse<CourseTutorialCompletionResponse> completeCourseTutorial(
            @Parameter(hidden = true) Long memberId);

    @Operation(
            summary = "회원 차단",
            description =
                    "해당 회원을 차단한다(멱등). 차단하면 내 후기 목록에서 그 회원의 후기가 빠진다(단방향, 후기 요약 수치는 그대로). "
                            + "자기 자신은 400, 없는 회원은 404. JWT 필요.")
    ApiResponse<Void> block(
            @Parameter(description = "차단할 회원 id") Long memberId,
            @Parameter(hidden = true) Long currentMemberId);

    @Operation(
            summary = "차단한 회원 목록 조회",
            description =
                    "내가 차단한 회원을 차단한 시각 최신순 커서 페이지네이션으로 반환한다. 항목의 memberId를 그대로"
                            + " 차단 해제 요청 경로에 쓰면 된다. 탈퇴·익명화된 회원은 nickname이 null로 나온다. JWT 필요.")
    ApiResponse<CursorPage<BlockedMemberItem>> getMyBlocks(
            @Parameter(description = "페이지 크기(1~100)") int size,
            @Parameter(description = "다음 페이지 커서") String cursor,
            @Parameter(hidden = true) Long memberId);

    @Operation(summary = "회원 차단 해제", description = "차단을 해제한다(멱등, 차단 상태가 아니어도 200). JWT 필요.")
    ApiResponse<Void> unblock(
            @Parameter(description = "차단 해제할 회원 id") Long memberId,
            @Parameter(hidden = true) Long currentMemberId);
}
