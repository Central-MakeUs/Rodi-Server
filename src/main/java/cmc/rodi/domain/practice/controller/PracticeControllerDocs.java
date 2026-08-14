package cmc.rodi.domain.practice.controller;

import cmc.rodi.domain.practice.dto.PracticeItem;
import cmc.rodi.domain.practice.dto.PracticeRegisterResponse;
import cmc.rodi.domain.practice.dto.PracticeSkipReasonRequest;
import cmc.rodi.domain.practice.dto.PracticeVisitRequest;
import cmc.rodi.domain.practice.dto.PracticeVisitResponse;
import cmc.rodi.global.common.form.FormResponse;
import cmc.rodi.global.common.pagination.CursorPage;
import cmc.rodi.global.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;

/** 연습 코스 API의 Swagger 문서 스펙. 매핑·구현은 {@link PracticeController}. */
@Tag(name = "Practice", description = "사용자 연습 코스")
public interface PracticeControllerDocs {

    /**
     * 미방문 이유 폼의 실제 응답. 공용 {@code FormResponse} 스키마를 신고 사유 폼과 나눠 쓰는데 springdoc은 스키마 단위로 example을
     * 그리므로, 폼별 예시는 이렇게 엔드포인트에서 준다.
     */
    String SKIP_REASON_FORM_EXAMPLE =
            """
            {
              "isSuccess": true,
              "code": "COMMON_200",
              "message": "요청에 성공했습니다.",
              "data": {
                "questionId": "WHY_NOT_PRACTICED",
                "type": "SINGLE_SELECT",
                "title": "왜 연습을 다녀오지 않았나요?",
                "description": "이유를 알려주시면 더 나은 코스를 추천해드릴게요!",
                "required": true,
                "options": [
                  { "code": "CHECK_REALTIME_TRAFFIC", "label": "실시간 교통정보를 보려고 했어요", "order": 1, "requiresTextInput": false },
                  { "code": "TOO_FAR", "label": "생각보다 멀었어요", "order": 2, "requiresTextInput": false },
                  { "code": "ROUTE_SEEMED_DIFFICULT", "label": "길이 어려워 보여요", "order": 3, "requiresTextInput": false },
                  { "code": "SCHEDULE_DID_NOT_MATCH", "label": "일정이 맞지 않았어요", "order": 4, "requiresTextInput": false },
                  { "code": "OTHER", "label": "기타", "order": 5, "requiresTextInput": true,
                    "textInputPlaceholder": "이유를 작성해주세요", "textInputMaxLength": 100 }
                ]
              }
            }
            """;

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
                    "담아둔 연습 항목을 최근 활동순(lastActivityAt = 다녀왔으면 마지막 방문 시각, 아니면 담은 시각)으로"
                            + " 커서 페이지네이션 반환한다. 담기만 한 항목도 날짜가 있어 카드가 비지 않는다."
                            + " 상태 필터는 없고 한 목록에 전부 내려간다. 장소 요약은 저장 목록과 동일한 형식."
                            + " 방문 인증 여부는 내려주지 않는다 — 인증 표시는 후기 응답의 isVerifiedVisit 한 곳으로 모았다."
                            + " JWT 필요.")
    ApiResponse<CursorPage<PracticeItem>> getMyPractices(
            @Parameter(description = "페이지 크기(1~100)") int size,
            @Parameter(description = "다음 페이지 커서") String cursor,
            @Parameter(hidden = true) Long memberId);

    @Operation(
            summary = "방문 기록(다녀왔어요)",
            description =
                    "RV-01의 \"다녀왔어요\"를 기록한다. 상태는 보내지 않는다 — 이 호출 자체가 방문이므로 서버가 VISITED로 정한다."
                            + " 부를 때마다 방문이 하나씩 쌓이는 비멱등 연산이라 하위 리소스 POST다."
                            + " 연습 횟수 +1, 방문 시각 기록. 앱이 GPS로 측정한 인정 주행거리"
                            + "(certifiedDistanceMeters)를 보내면 서버가 필요 거리(min(코스거리 × 40%, 5km))와"
                            + " 비교해 방문 인증 여부를 판정한다. 측정 없이 눌렀다면 거리를 생략하며 인증되지 않는다."
                            + " 주차장처럼 주행거리가 없는 장소는 거리를 보내도 0으로 처리된다 — 인증 대상이 아니다."
                            + " 같은 거리가 회원의 누적 주행거리에도 쌓여 레벨이 오를 수 있다 — 이때 응답의"
                            + " levelUp·newLevel로 알 수 있다. 누적에는 코스 전체 거리라는 상한이 걸려,"
                            + " 측정값이 그보다 크면 코스 거리까지만 반영된다."
                            + " 인증 결과는 이번 방문 기준(isCertifiedNow)만 준다 — 누적 인증 여부는 응답에 없다."
                            + " 인증은 이 주행을 시작한 레벨로 기록되므로, 이 주행으로 승급했다면 새 레벨에서 다시 인증해야 한다."
                            + " 재시도·연타는 서버가 흡수한다 — 직전 방문 후 10분 안의 호출은 같은 방문으로 보고 아무것도 바꾸지 않으며,"
                            + " 오류가 아니라 200에 현재 상태를 담아 준다(addedCertifiedDistanceMeters=0, isCertifiedNow=false,"
                            + " levelUp=false). 타인 항목은 403. JWT 필요.")
    ApiResponse<PracticeVisitResponse> recordVisit(
            @Parameter(description = "연습 항목 id") Long practiceId,
            @Parameter(hidden = true) Long memberId,
            PracticeVisitRequest request);

    @Operation(
            summary = "미방문 이유 폼 조회",
            description =
                    "\"안 했어요\" 화면에 그릴 사유 선택지를 내려준다(order 오름차순). 문구·순서를 서버가 정의하므로 앱 배포 없이 바꿀 수 있다."
                            + " OTHER(기타)는 requiresTextInput=true이며 placeholder·최대 길이가 함께 온다."
                            + " 선택지의 code가 그대로 사유 제출 요청의 reason 값이다. JWT 필요.")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            // 없으면 예시만 남고 응답 스키마가 통째로 사라진다(필드 설명이 안 보인다)
            useReturnTypeSchema = true,
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(ref = "#/components/schemas/ApiResponseFormResponse"),
                            examples =
                                    @ExampleObject(
                                            name = "미방문 사유 폼",
                                            value = SKIP_REASON_FORM_EXAMPLE)))
    ApiResponse<FormResponse> getSkipReasonForm();

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
}
