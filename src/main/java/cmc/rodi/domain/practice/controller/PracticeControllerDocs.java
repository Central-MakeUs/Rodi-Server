package cmc.rodi.domain.practice.controller;

import cmc.rodi.domain.practice.dto.PracticeItem;
import cmc.rodi.domain.practice.dto.PracticeRegisterResponse;
import cmc.rodi.domain.practice.dto.PracticeStatusUpdateRequest;
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
            summary = "방문 여부 상태 변경",
            description =
                    "VISITED면 그 자리에서 방문 처리(연습 횟수 +1, 방문 시각 기록)한다 — 클라이언트가 이동 추적으로 자동 호출하며"
                            + " 서버는 결과를 기록만 한다. NOT_VISITED면 사유가 필수이고(기타는 직접 입력 필수),"
                            + " 이미 사유가 있으면 409. 타인 항목은 403. JWT 필요.")
    ApiResponse<Void> updateStatus(
            @Parameter(description = "연습 항목 id") Long practiceId,
            @Parameter(hidden = true) Long memberId,
            PracticeStatusUpdateRequest request);

    @Operation(
            summary = "연습 목록에서 제거",
            description = "연습 항목을 삭제한다(멱등, 없어도 200). 타인 항목은 403. JWT 필요.")
    ApiResponse<Void> delete(
            @Parameter(description = "연습 항목 id") Long practiceId,
            @Parameter(hidden = true) Long memberId);
}
