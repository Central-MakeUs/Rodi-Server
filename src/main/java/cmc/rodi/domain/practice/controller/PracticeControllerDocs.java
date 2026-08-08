package cmc.rodi.domain.practice.controller;

import cmc.rodi.domain.practice.dto.PracticeItem;
import cmc.rodi.domain.practice.dto.PracticeRegisterResponse;
import cmc.rodi.domain.practice.dto.PracticeSkipReasonRequest;
import cmc.rodi.domain.practice.dto.PracticeVisitRequest;
import cmc.rodi.domain.practice.dto.PracticeVisitResponse;
import cmc.rodi.global.common.pagination.CursorPage;
import cmc.rodi.global.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

/** 연습 코스 API의 Swagger 문서 스펙. 매핑·구현은 {@link PracticeController}. */
@Tag(name = "Practice", description = "사용자 연습 코스")
public interface PracticeControllerDocs {

    @Operation(
            summary = "연습 목록에 담기",
            description =
                    "코스 상세의 [연습하기]로 장소를 내 연습 목록에 담는다(PLANNED). 코스·주차장 모두 가능."
                            + " 이미 담긴 장소면 행을 새로 만들지 않고 상태만 예정으로 되돌리며, 지난 연습 횟수는 유지된다. JWT 필요.")
    ApiResponse<PracticeRegisterResponse> register(
            @Parameter(description = "장소 id") Long placeId,
            @Parameter(hidden = true) Long memberId);

    @Operation(
            summary = "내 연습 목록 조회",
            description =
                    "담아둔 연습 항목을 최근 방문순(방문 이력이 없으면 담은 시각 기준)으로 커서 페이지네이션 반환한다."
                            + " 상태 필터는 없고 한 목록에 전부 내려간다. 장소 요약은 저장 목록과 동일한 형식. JWT 필요.")
    ApiResponse<CursorPage<PracticeItem>> getMyPractices(
            @Parameter(description = "페이지 크기(1~100)") int size,
            @Parameter(description = "다음 페이지 커서") String cursor,
            @Parameter(hidden = true) Long memberId);

    @Operation(
            summary = "방문 기록(다녀왔어요)",
            description =
                    "RV-01의 \"다녀왔어요\"를 기록한다. 상태는 보내지 않는다 — 이 호출 자체가 방문이므로 서버가 VISITED로 정한다."
                            + " 연습 횟수 +1, 방문 시각 기록. 앱이 GPS로 측정한 인정 주행거리"
                            + "(certifiedDistanceMeters)를 보내면 서버가 필요 거리(min(코스거리 × 40%, 5km))와"
                            + " 비교해 방문 인증 여부를 판정한다. 측정 없이 눌렀다면 거리를 생략하며 인증되지 않는다."
                            + " 타인 항목은 403. JWT 필요.")
    ApiResponse<PracticeVisitResponse> recordVisit(
            @Parameter(description = "연습 항목 id") Long practiceId,
            @Parameter(hidden = true) Long memberId,
            PracticeVisitRequest request);

    @Operation(
            summary = "미방문 사유 제출(안 했어요)",
            description =
                    "RV-01의 \"안 했어요\"를 기록한다. 상태를 NOT_VISITED로 바꾸면서 미방문 이유 폼"
                            + "(GET /practices/skip-reason-form)에서 고른 사유를 함께 저장한다."
                            + " 사유는 한 번 저장하면 수정할 수 없고(409), 다시 다녀오면 비워져 새로 남길 수 있다."
                            + " 타인 항목은 403. JWT 필요.")
    ApiResponse<Void> submitSkipReason(
            @Parameter(description = "연습 항목 id") Long practiceId,
            @Parameter(hidden = true) Long memberId,
            PracticeSkipReasonRequest request);

    @Operation(
            summary = "연습 목록에서 제거",
            description = "연습 항목을 삭제한다(멱등, 없어도 200). 타인 항목은 403. JWT 필요.")
    ApiResponse<Void> delete(
            @Parameter(description = "연습 항목 id") Long practiceId,
            @Parameter(hidden = true) Long memberId);
}
