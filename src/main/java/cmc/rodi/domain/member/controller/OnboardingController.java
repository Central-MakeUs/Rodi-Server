package cmc.rodi.domain.member.controller;

import cmc.rodi.domain.member.dto.OnboardingRequest;
import cmc.rodi.domain.member.service.OnboardingService;
import cmc.rodi.global.auth.resolver.CurrentMember;
import cmc.rodi.global.common.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 온보딩 API. 문서 스펙은 {@link OnboardingControllerDocs}. */
@RestController
@RequestMapping("/api/v1/members/me/onboarding")
@RequiredArgsConstructor
public class OnboardingController implements OnboardingControllerDocs {

    private final OnboardingService onboardingService;

    @Override
    @PostMapping
    public ApiResponse<Void> submit(
            @CurrentMember Long memberId, @Valid @RequestBody OnboardingRequest request) {
        onboardingService.submit(memberId, request);
        return ApiResponse.success(null);
    }
}
