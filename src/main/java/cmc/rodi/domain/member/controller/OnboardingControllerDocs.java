package cmc.rodi.domain.member.controller;

import cmc.rodi.domain.member.dto.OnboardingRequest;
import cmc.rodi.global.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

/** 온보딩 API의 Swagger 문서 스펙. 매핑·구현은 {@link OnboardingController}. */
@Tag(name = "Onboarding", description = "온보딩")
public interface OnboardingControllerDocs {

    @Operation(
            summary = "온보딩 제출",
            description =
                    "운전 경험·추가 정보를 한 번에 제출한다. 레벨은 클라이언트가 변환해 보낸 값을 저장한다. "
                            + "저장만 하며 응답 데이터는 없다(추천유형·레벨은 클라이언트 로컬 값). 이미 온보딩한 회원은 409.")
    ApiResponse<Void> submit(@Parameter(hidden = true) Long memberId, OnboardingRequest request);
}
