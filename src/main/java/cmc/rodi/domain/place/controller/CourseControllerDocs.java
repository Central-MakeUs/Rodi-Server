package cmc.rodi.domain.place.controller;

import cmc.rodi.domain.place.dto.CourseRegisterRequest;
import cmc.rodi.domain.place.dto.CourseRegisterResponse;
import cmc.rodi.domain.place.dto.CourseRegistrationFormResponse;
import cmc.rodi.domain.place.dto.MyCourseItem;
import cmc.rodi.domain.place.entity.ApprovalStatus;
import cmc.rodi.global.common.pagination.CursorPage;
import cmc.rodi.global.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;

/** 사용자 코스 등록 API의 Swagger 문서 스펙. 매핑·구현은 {@link CourseController}. */
@Tag(name = "Course", description = "사용자 코스 등록")
public interface CourseControllerDocs {

    String REGISTER_REQUEST_EXAMPLE =
            """
            {
              "name": "압구정로데오역",
              "address": "서울특별시 강남구",
              "distanceMeters": 8200,
              "waypoints": [
                { "type": "START",       "lat": 37.5273, "lng": 127.0403, "name": "압구정로데오역" },
                { "type": "VIA",         "lat": 37.5227, "lng": 127.0521, "name": "청담사거리" },
                { "type": "DESTINATION", "lat": 37.5133, "lng": 127.0533, "name": "삼성중앙역" }
              ],
              "practiceTypes": ["STRAIGHT", "LANE_CHANGE", "INTERSECTION"],
              "description": "차선이 넓고, 직선 구간이 길어요.",
              "caution": "갑자기 나오는 자전거 주의!"
            }
            """;

    @Operation(
            summary = "코스 등록",
            description =
                    """
                    지도에서 고른 경로로 코스를 등록한다. **승인 대기(PENDING)** 상태로 저장되며,
                    관리자가 승인하기 전까지 전체 목록·검색·연관검색어·지도 마커에 나오지 않는다.

                    - `waypoints`는 **경로 순서대로** 보낸다. 첫 항목은 `START`, 마지막은 `DESTINATION`,
                      가운데는 전부 `VIA`(0~3개)여야 한다.
                    - `name`을 생략하면 **출발지 지점명**이 코스명이 된다. 그래서 출발지 지점명은 필수다.
                    - 좌표·주소·주행거리는 앱이 카카오맵에서 받은 값을 그대로 보낸다(서버는 지오코딩·경로탐색을 하지 않는다).
                    - `practiceTypes`는 등록 폼의 `code`를 1~3개, 중복 없이 보낸다.
                    """,
            requestBody =
                    @io.swagger.v3.oas.annotations.parameters.RequestBody(
                            content =
                                    @Content(
                                            examples =
                                                    @ExampleObject(
                                                            value = REGISTER_REQUEST_EXAMPLE))))
    ApiResponse<CourseRegisterResponse> register(
            @Schema(hidden = true) CourseRegisterRequest request,
            @Parameter(hidden = true) Long memberId);

    @Operation(
            summary = "코스 등록 폼 조회",
            description =
                    """
                    등록 화면 정의를 내려준다 — 연습유형 **카테고리 → 연습유형** 트리와 입력 제약·안내 문구.
                    문구·구성을 서버가 쥐고 있어 앱 배포 없이 바꿀 수 있다.

                    - `selectAllEnabled=false`인 카테고리(복합 상황)는 **"전체" 버튼을 그리지 않는다** —
                      항목이 4개라 최대 선택 개수(3)를 넘기 때문이다.
                    - 연습유형은 카테고리를 넘나들며 `maxSelect`개까지 고를 수 있고, 초과하면
                      `maxSelectExceededMessage`를 띄운다.
                    """)
    ApiResponse<CourseRegistrationFormResponse> getRegistrationForm();

    @Operation(
            summary = "내가 등록한 코스 목록",
            description =
                    """
                    내가 등록한 코스를 **최신 등록순**으로 커서 페이징한다. 승인 전 코스는 전체 목록·검색에
                    나오지 않으므로, 심사 상태를 확인하는 창구다.

                    - `status`로 `PENDING`·`APPROVED`·`REJECTED` 하나만 걸러 볼 수 있다(생략하면 전체).
                    - `totalCount`는 **첫 페이지에서만** 채워지고 **상태 필터가 적용된 개수**다.
                    - 내가 삭제한 코스와 운영자가 등록한 코스는 포함되지 않는다.
                    """)
    ApiResponse<CursorPage<MyCourseItem>> getMyCourses(
            @Parameter(description = "승인 상태 필터(생략 시 전체)") ApprovalStatus status,
            @Parameter(description = "페이지 크기(1~100)") int size,
            @Parameter(description = "다음 페이지 커서") String cursor,
            @Parameter(hidden = true) Long memberId);

    @Operation(
            summary = "내 코스 삭제",
            description =
                    """
                    내가 등록한 코스를 삭제한다. 승인 상태와 무관하게 지울 수 있다.

                    **soft delete**라 행이 남는다 — 물리 삭제하면 다른 사용자의 북마크·후기·연습기록까지
                    함께 사라져, 담아둔 사람은 항목이 없어진 이유를 알 수 없다. 삭제 후에는
                    전체 목록·검색·내 코스 목록에서 빠지고, 이미 담아둔 사용자의 저장·연습 목록에는
                    `isDeleted=true`로 남으며 상세 진입 시 `COURSE_404_2`가 내려간다.

                    - 없는 코스·이미 삭제된 코스는 **멱등하게 200**.
                    - 남의 코스와 운영자가 등록한 코스는 403.
                    - 주차장 id를 보내면 404(코스가 아니다).
                    """)
    ApiResponse<Void> delete(
            @Parameter(description = "코스 id") Long courseId,
            @Parameter(hidden = true) Long memberId);
}
