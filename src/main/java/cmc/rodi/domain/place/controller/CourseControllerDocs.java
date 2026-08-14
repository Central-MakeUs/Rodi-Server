package cmc.rodi.domain.place.controller;

import cmc.rodi.domain.place.dto.CourseRegisterRequest;
import cmc.rodi.domain.place.dto.CourseRegisterResponse;
import cmc.rodi.domain.place.dto.CourseRegistrationFormResponse;
import cmc.rodi.global.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
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
            @Schema(hidden = true) CourseRegisterRequest request, Long memberId);

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
}
