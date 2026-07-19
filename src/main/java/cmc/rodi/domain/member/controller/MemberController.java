package cmc.rodi.domain.member.controller;

import cmc.rodi.domain.member.dto.MemberUpdateRequest;
import cmc.rodi.domain.member.dto.OnboardingRequest;
import cmc.rodi.domain.member.service.MemberProfileService;
import cmc.rodi.domain.member.service.MemberWithdrawalService;
import cmc.rodi.domain.member.service.OnboardingService;
import cmc.rodi.global.auth.resolver.CurrentMember;
import cmc.rodi.global.common.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 회원 API. 문서 스펙은 {@link MemberControllerDocs}. */
@RestController
@RequestMapping("/api/v1/members")
@RequiredArgsConstructor
public class MemberController implements MemberControllerDocs {

    private final MemberWithdrawalService memberWithdrawalService;
    private final OnboardingService onboardingService;
    private final MemberProfileService memberProfileService;

    @Override
    @DeleteMapping("/me")
    public ApiResponse<Void> withdraw(@CurrentMember Long memberId) {
        memberWithdrawalService.withdraw(memberId);
        return ApiResponse.success(null);
    }

    @Override
    @PatchMapping("/me")
    public ApiResponse<Void> updateMe(
            @CurrentMember Long memberId, @Valid @RequestBody MemberUpdateRequest request) {
        memberProfileService.update(memberId, request);
        return ApiResponse.success(null);
    }

    @Override
    @PostMapping("/me/onboarding")
    public ApiResponse<Void> submitOnboarding(
            @CurrentMember Long memberId, @Valid @RequestBody OnboardingRequest request) {
        onboardingService.submit(memberId, request);
        return ApiResponse.success(null);
    }
}
